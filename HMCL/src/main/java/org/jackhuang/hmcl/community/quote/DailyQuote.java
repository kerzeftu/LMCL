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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// 一条已经解析好的每日一言。
///
/// 本记录不包含请求状态或缓存时间，缓存由 [DailyQuoteStore] 负责；
/// UI 只依赖 [DailyQuote#displayLine()]。
///
/// @param text 正文
/// @param author 作者，可为 `null`
/// @param source 出处（篇名、作品名或站点名），可为 `null`
/// @param kind 内容类别
/// @param origin 来源类别
/// @param sourceId 联网源 id；内置内容为 `null`
/// @param sourceName 联网源显示名；内置内容为 `null`
@NotNullByDefault
public record DailyQuote(
        @NotNull String text,
        @Nullable String author,
        @Nullable String source,
        @NotNull QuoteKind kind,
        @NotNull QuoteOrigin origin,
        @Nullable String sourceId,
        @Nullable String sourceName) {

    /// 规范化字段并校验正文非空。
    public DailyQuote {
        text = normalizeRequired(text);
        author = normalize(author);
        source = normalize(source);
        sourceId = normalize(sourceId);
        sourceName = normalize(sourceName);
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(origin, "origin");
    }

    /// 生成主页显示的一行文本。
    ///
    /// 格式为 `正文 — 作者《出处》`；作者与出处都缺失时只返回正文。
    ///
    /// @return 单行展示文本
    @NotNull
    public String displayLine() {
        StringBuilder builder = new StringBuilder(text);
        if (author != null && source != null) {
            builder.append(" — ").append(author).append('《').append(source).append('》');
        } else if (author != null) {
            builder.append(" — ").append(author);
        } else if (source != null) {
            builder.append(" — 《").append(source).append('》');
        }
        return builder.toString();
    }

    /// 来源的展示名；内置内容回退到“内置”，联网内容回退到源 id。
    ///
    /// @return 展示名
    @NotNull
    public String originName() {
        if (sourceName != null) {
            return sourceName;
        }
        if (sourceId != null) {
            return sourceId;
        }
        return origin == QuoteOrigin.BUILTIN ? "Built-in" : "Online";
    }

    private static @NotNull String normalizeRequired(@Nullable String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException("quote text must not be blank");
        }
        return normalized;
    }

    private static @Nullable String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
