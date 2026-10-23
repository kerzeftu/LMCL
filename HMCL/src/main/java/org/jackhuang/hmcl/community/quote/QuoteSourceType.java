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

/// 联网源的类型。
public enum QuoteSourceType {
    /// Hitokoto 一言公益接口，无需 token。
    HITOKOTO("hitokoto"),
    /// 今日诗词，需要按安装获取 token。
    JINRISHICI("jinrishici"),
    /// 用户自行配置的 URL。
    CUSTOM("custom");

    private final String wireName;

    QuoteSourceType(@NotNull String wireName) {
        this.wireName = wireName;
    }

    /// 写入设置时使用的稳定名称。
    ///
    /// @return 稳定名称
    @NotNull
    public String wireName() {
        return wireName;
    }

    /// 解析稳定名称；未知值返回 `null`。
    ///
    /// @param value 名称
    /// @return 类型或 `null`
    @Nullable
    public static QuoteSourceType parse(@Nullable String value) {
        if (value != null) {
            for (QuoteSourceType type : values()) {
                if (type.wireName.equalsIgnoreCase(value)) {
                    return type;
                }
            }
        }
        return null;
    }
}
