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
package org.jackhuang.hmcl.community.crash;

import org.jetbrains.annotations.NotNullByDefault;

/// 崩溃发生的阶段，以步骤 08 的 `SURVIVED_THRESHOLD` 事件为分界。
@NotNullByDefault
public enum LaunchPhase {
    /// 未发出存活阈值事件就退出（启动期崩溃）。
    STARTUP,

    /// 存活阈值之后退出（运行期崩溃）。
    RUNTIME;

    /// 从索引文件中宽松解析；未知值按 [STARTUP] 处理。
    ///
    /// @param value 字符串
    /// @return 阶段
    public static LaunchPhase parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return STARTUP;
        }
    }
}
