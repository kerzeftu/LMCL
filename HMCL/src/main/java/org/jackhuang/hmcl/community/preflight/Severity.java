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
package org.jackhuang.hmcl.community.preflight;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/// 单项检查的严重程度。
@NotNullByDefault
public enum Severity {
    /// 通过。
    OK,
    /// 可能有问题，不阻断启动。
    WARN,
    /// 很可能无法启动。
    ERROR,
    /// 未能完成检查（异常、超时、禁用、离线降级）。
    SKIPPED;

    /// 返回该严重程度的展示权重；[Severity#SKIPPED] 不参与最坏值计算，返回 `-1`。
    ///
    /// @return 权重
    public int rank() {
        return switch (this) {
            case OK -> 0;
            case WARN -> 1;
            case ERROR -> 2;
            case SKIPPED -> -1;
        };
    }

    /// 解析字符串；无法识别时返回 `null`。
    ///
    /// @param value 字符串
    /// @return 严重程度或 `null`
    @Nullable
    public static Severity parse(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /// 返回两个严重程度中更严重的一个；[Severity#SKIPPED] 视为无意见。
    ///
    /// @param left 左值
    /// @param right 右值
    /// @return 更严重者，两者皆 SKIPPED 时返回 SKIPPED
    @NotNull
    public static Severity worst(@NotNull Severity left, @NotNull Severity right) {
        return left.rank() >= right.rank() ? left : right;
    }
}
