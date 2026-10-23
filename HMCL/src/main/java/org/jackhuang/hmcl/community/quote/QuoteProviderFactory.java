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

/// 根据源类型创建 provider。
@NotNullByDefault
public final class QuoteProviderFactory {

    private QuoteProviderFactory() {
    }

    /// 创建 provider。
    ///
    /// @param source 源
    /// @return provider
    /// @throws IllegalArgumentException 源类型不受支持或缺少 URL
    @NotNull
    public static QuoteProvider create(@NotNull QuoteSource source) {
        return switch (source.type()) {
            case HITOKOTO -> new HitokotoQuoteProvider();
            case JINRISHICI -> new JinrishiciQuoteProvider();
            case CUSTOM -> new CustomQuoteProvider(source);
        };
    }
}
