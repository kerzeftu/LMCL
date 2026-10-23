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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 启动器内置的每日一言语料。
///
/// 资源位于 `assets/community/daily-quotes-mc.json` 与
/// `assets/community/daily-quotes-poetry.json`，都是公版/原创短句，
/// 不依赖网络。按 [LocalDate#toEpochDay()] 的奇偶交替选择 MC 与诗词，
/// 同一天多次调用返回同一条；资源缺失时返回安全兜底句，不抛异常。
@NotNullByDefault
public final class BuiltinQuotes {

    private static final String MC_RESOURCE = "/assets/community/daily-quotes-mc.json";
    private static final String POETRY_RESOURCE = "/assets/community/daily-quotes-poetry.json";

    private static final List<DailyQuote> MC = load(MC_RESOURCE, QuoteKind.MC);
    private static final List<DailyQuote> POETRY = load(POETRY_RESOURCE, QuoteKind.POETRY);

    private BuiltinQuotes() {
    }

    /// 返回指定日期对应的内置短句。
    ///
    /// @param date 日期
    /// @return 短句
    @NotNull
    public static DailyQuote forDate(@NotNull LocalDate date) {
        long day = date.toEpochDay();
        boolean poetry = Math.floorMod(day, 2) == 0;
        List<DailyQuote> list = poetry ? POETRY : MC;
        if (list.isEmpty()) {
            list = poetry ? MC : POETRY;
        }
        if (list.isEmpty()) {
            return fallback();
        }
        int index = (int) Math.floorMod(Math.floorDiv(day, 2), list.size());
        return list.get(index);
    }

    /// 返回全部内置短句（先 MC 后诗词）。
    ///
    /// @return 不可变列表
    @NotNull
    public static List<DailyQuote> all() {
        List<DailyQuote> result = new ArrayList<>(MC.size() + POETRY.size());
        result.addAll(MC);
        result.addAll(POETRY);
        return List.copyOf(result);
    }

    /// 安全兜底短句。
    ///
    /// @return 短句
    @NotNull
    public static DailyQuote fallback() {
        return new DailyQuote(
                "在方块之间，抬头看看天空。",
                null,
                null,
                QuoteKind.MC,
                QuoteOrigin.BUILTIN,
                null,
                null);
    }

    private static @NotNull List<DailyQuote> load(@NotNull String resource, @NotNull QuoteKind kind) {
        List<DailyQuote> result = new ArrayList<>();
        try (InputStream input = BuiltinQuotes.class.getResourceAsStream(resource)) {
            if (input == null) {
                LOG.warning("Missing built-in quote resource: " + resource);
                return List.of();
            }
            JsonObject root = JsonUtils.GSON.fromJson(
                    new InputStreamReader(input, StandardCharsets.UTF_8),
                    JsonObject.class);
            if (root == null || !root.has("quotes") || !root.get("quotes").isJsonArray()) {
                LOG.warning("Invalid built-in quote resource: " + resource);
                return List.of();
            }
            JsonArray quotes = root.getAsJsonArray("quotes");
            for (JsonElement element : quotes) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                String text = getString(object, "text");
                if (text == null) {
                    continue;
                }
                try {
                    result.add(new DailyQuote(
                            text,
                            getString(object, "author"),
                            getString(object, "source"),
                            kind,
                            QuoteOrigin.BUILTIN,
                            null,
                            null));
                } catch (IllegalArgumentException e) {
                    LOG.warning("Skipping invalid built-in quote", e);
                }
            }
        } catch (Throwable e) {
            LOG.warning("Failed to read built-in quote resource: " + resource, e);
        }
        return List.copyOf(result);
    }

    private static String getString(@NotNull JsonObject object, @NotNull String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        String text = value.getAsString().trim();
        return text.isEmpty() ? null : text;
    }
}
