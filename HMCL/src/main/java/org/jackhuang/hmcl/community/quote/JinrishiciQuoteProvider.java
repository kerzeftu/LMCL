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

/// 今日诗词源（`https://v2.jinrishici.com`）。
///
/// 先 `GET /token` 获取按安装 token，再携带 `X-User-Token` 请求 `/sentence`。
/// token 写入 [JinrishiciTokenStore]；遇到 401/403 时清除并重试一次。
/// 今日诗词免费版仅限非商用、不支持服务端调用，并会使用 IP/token 做限流。
@NotNullByDefault
public final class JinrishiciQuoteProvider implements QuoteProvider {

    /// token 接口。
    public static final String DEFAULT_TOKEN_URL = "https://v2.jinrishici.com/token";
    /// 句子接口。
    public static final String DEFAULT_SENTENCE_URL = "https://v2.jinrishici.com/sentence";

    private final JinrishiciTokenStore tokenStore;
    private final String tokenUrl;
    private final String sentenceUrl;

    /// 使用默认接口和全局 token 文件。
    public JinrishiciQuoteProvider() {
        this(JinrishiciTokenStore.get(), DEFAULT_TOKEN_URL, DEFAULT_SENTENCE_URL);
    }

    /// 使用指定 token 存储与接口（测试可注入）。
    ///
    /// @param tokenStore token 存储
    /// @param tokenUrl token 接口
    /// @param sentenceUrl 句子接口
    public JinrishiciQuoteProvider(
            @NotNull JinrishiciTokenStore tokenStore,
            @NotNull String tokenUrl,
            @NotNull String sentenceUrl) {
        this.tokenStore = tokenStore;
        this.tokenUrl = tokenUrl;
        this.sentenceUrl = sentenceUrl;
    }

    @Override
    public @NotNull DailyQuote fetch() throws Exception {
        String token = tokenStore.load();
        if (token == null) {
            token = requestToken();
        }
        try {
            return requestSentence(token);
        } catch (QuoteHttp.HttpException e) {
            if (e.status() != 401 && e.status() != 403) {
                throw e;
            }
            tokenStore.clear();
            return requestSentence(requestToken());
        }
    }

    private @NotNull String requestToken() throws Exception {
        String body = QuoteHttp.get(tokenUrl, Map.of());
        String token = parseToken(body);
        tokenStore.save(token);
        return token;
    }

    private @NotNull DailyQuote requestSentence(@NotNull String token) throws Exception {
        String body = QuoteHttp.get(sentenceUrl, Map.of("X-User-Token", token));
        DailyQuote quote = parseSentence(body);
        String refreshed = parseResponseToken(body);
        if (refreshed != null) {
            tokenStore.save(refreshed);
        }
        return quote;
    }

    /// 解析 token 响应。
    ///
    /// @param body 响应正文
    /// @return token
    /// @throws IOException 响应无效
    static @NotNull String parseToken(@NotNull String body) throws IOException {
        JsonObject root = parseObject(body);
        if (root == null) {
            throw new IOException("invalid Jinrishici token response");
        }
        String token = getString(root, "token");
        if (token == null) {
            JsonElement data = root.get("data");
            if (data != null && data.isJsonPrimitive() && data.getAsJsonPrimitive().isString()) {
                token = data.getAsString().trim();
            } else if (data != null && data.isJsonObject()) {
                token = getString(data.getAsJsonObject(), "token");
            }
        }
        if (token == null || token.isEmpty()) {
            throw new IOException("Jinrishici token response has no token");
        }
        return token;
    }

    /// 解析句子响应。
    ///
    /// @param body 响应正文
    /// @return 短句
    /// @throws IOException 响应无效
    static @NotNull DailyQuote parseSentence(@NotNull String body) throws IOException {
        JsonObject root = parseObject(body);
        if (root == null || !root.has("data") || !root.get("data").isJsonObject()) {
            throw new IOException("invalid Jinrishici sentence response");
        }
        JsonObject data = root.getAsJsonObject("data");
        String content = getString(data, "content");
        if (content == null) {
            throw new IOException("Jinrishici sentence response has no content");
        }
        String author = null;
        String source = null;
        if (data.has("origin") && data.get("origin").isJsonObject()) {
            JsonObject origin = data.getAsJsonObject("origin");
            author = getString(origin, "author");
            source = getString(origin, "title");
        }
        return new DailyQuote(
                content,
                author,
                source,
                QuoteKind.POETRY,
                QuoteOrigin.ONLINE,
                DailyQuoteSettings.JINRISHICI_ID,
                "Jinrishici");
    }

    private static @Nullable String parseResponseToken(@NotNull String body) {
        try {
            JsonObject root = parseObject(body);
            return root == null ? null : getString(root, "token");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable JsonObject parseObject(@NotNull String body) {
        try {
            return JsonUtils.GSON.fromJson(body, JsonObject.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable String getString(@NotNull JsonObject object, @NotNull String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isString()) {
            return null;
        }
        String value = object.get(key).getAsString().trim();
        return value.isEmpty() ? null : value;
    }
}
