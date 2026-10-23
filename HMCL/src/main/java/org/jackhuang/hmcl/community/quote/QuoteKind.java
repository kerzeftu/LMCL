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
import org.jetbrains.annotations.Nullable;

/// 每日一言的内容类别。
public enum QuoteKind {
    /// Minecraft 风味的原创短句。
    MC("mc"),
    /// 公版古诗词/古文。
    POETRY("poetry"),
    /// 来源未归类，保留给自定义源。
    OTHER("other");

    private final String wireName;

    QuoteKind(@NotNull String wireName) {
        this.wireName = wireName;
    }

    /// 返回写入缓存/设置时使用的稳定名称。
    ///
    /// @return 稳定名称
    @NotNull
    public String wireName() {
        return wireName;
    }

    /// 解析稳定名称；未知值返回 `null`。
    ///
    /// @param value 名称
    /// @return 类别或 `null`
    @Nullable
    public static QuoteKind parse(@Nullable String value) {
        if (value != null) {
            for (QuoteKind kind : values()) {
                if (kind.wireName.equalsIgnoreCase(value)) {
                    return kind;
                }
            }
        }
        return null;
    }
}
