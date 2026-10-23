/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.community.quote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

/// 用户自定义的 HTTP 源。
///
/// 支持两类响应：
/// - 纯文本：取第一行非空内容；
/// - JSON：对象或数组首项，正文取 `text` / `content` / `hitokoto` / `quote`，
///   作者取 `author` / `from_who`，出处取 `source` / `from` / `title`；
///   兼容一层 `data` 包装。
///
/// 不执行 HTML、不跟随脚本，也不对 URL 做额外白名单：是否信任由用户决定。
@NotNullByDefault
public final class CustomQuoteProvider implements QuoteProvider {

    private final QuoteSource source;

    /// 创建自定义源 provider。
    ///
    /// @param source 自定义源
    public CustomQuoteProvider(@NotNull QuoteSource source) {
        if (source.type() != QuoteSourceType.CUSTOM) {
            throw new IllegalArgumentException("not a custom quote source: " + source.id());
        }
        this.source = source;
    }

    @Override
    public @NotNull DailyQuote fetch() throws Exception {
        String url = source.url();
        if (url == null) {
            throw new IOException("custom quote source has no url: " + source.id());
        }
        String body = QuoteHttp.get(url, Map.of());
        return parse(body, source);
    }

    /// 解析自定义源响应。
    ///
    /// @param body 响应正文
    /// @param source 源
    /// @return 短句
    /// @throws IOException 正文为空或没有可用字段
    static @NotNull DailyQuote parse(@NotNull String body, @NotNull QuoteSource source) throws IOException {
        String text = body.trim();
        if (text.isEmpty()) {
            throw new IOException("empty custom quote response");
        }
        if (text.startsWith("<")) {
            throw new IOException("custom quote response is HTML");
        }
        if (text.startsWith("{") || text.startsWith("[")) {
            return parseJson(text, source);
        }
        String firstLine = firstNonBlankLine(text);
        if (firstLine == null) {
            throw new IOException("empty custom quote response");
        }
        return new DailyQuote(
                firstLine,
                null,
                null,
                QuoteKind.OTHER,
                QuoteOrigin.ONLINE,
                source.id(),
                source.name());
    }

    private static @NotNull DailyQuote parseJson(@NotNull String body, @NotNull QuoteSource source) throws IOException {
        JsonElement element;
        try {
            element = JsonUtils.GSON.fromJson(body, JsonElement.class);
        } catch (RuntimeException e) {
            throw new IOException("invalid custom quote json", e);
        }
        JsonObject object = asObject(element);
        if (object == null) {
            throw new IOException("custom quote json is not an object");
        }
        JsonObject payload = object;
        if (object.has("data") && object.get("data").isJsonObject()) {
            payload = object.getAsJsonObject("data");
        }
        String text = firstString(payload, "text", "content", "hitokoto", "quote");
        if (text == null && object.has("data") && object.get("data").isJsonPrimitive()
                && object.get("data").getAsJsonPrimitive().isString()) {
            text = object.get("data").getAsString().trim();
        }
        if (text == null || text.isEmpty()) {
            throw new IOException("custom quote json has no text field");
        }
        return new DailyQuote(
                text,
                firstString(payload, "author", "from_who"),
                firstString(payload, "source", "from", "title"),
                QuoteKind.OTHER,
                QuoteOrigin.ONLINE,
                source.id(),
                source.name());
    }

    private static @Nullable JsonObject asObject(@NotNull JsonElement element) {
        if (element.isJsonObject()) {
            return element.getAsJsonObject();
        }
        if (element.isJsonArray() && !element.getAsJsonArray().isEmpty()) {
            JsonElement first = element.getAsJsonArray().get(0);
            if (first.isJsonObject()) {
                return first.getAsJsonObject();
            }
        }
        return null;
    }

    private static @Nullable String firstNonBlankLine(@NotNull String text) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private static @Nullable String firstString(@NotNull JsonObject object, @NotNull String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive()
                    && object.get(key).getAsJsonPrimitive().isString()) {
                String value = object.get(key).getAsString().trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }
}
