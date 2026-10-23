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

/// 每日一言联网源。
///
/// 实现不得做 UI 操作；由 [DailyQuoteService] 在 io 线程调用。
@NotNullByDefault
public interface QuoteProvider {

    /// 获取一条短句。
    ///
    /// @return 短句
    /// @throws Exception 网络、解析或校验失败
    @NotNull
    DailyQuote fetch() throws Exception;
}
