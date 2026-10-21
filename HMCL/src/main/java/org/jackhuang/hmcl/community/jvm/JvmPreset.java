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
package org.jackhuang.hmcl.community.jvm;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/// JVM 参数向导的四个档位。
///
/// 存储值是小写枚举名（`upstream` / `g1` / `zgc` / `custom`），
/// 与 `community-settings.json` 的 `jvmWizard.<instanceId>.mode` 一致。
@NotNullByDefault
public enum JvmPreset {

    /// 跟随上游：不向 `jvmOptions` 写入任何内容。
    UPSTREAM,

    /// 固定堆大小（G1）：只写 `-Xms/-Xmx`；G1 子参数由上游注入。
    G1,

    /// 大内存（ZGC）：写 `-Xms/-Xmx -XX:+UseZGC`，要求 Java 21+、64 位、堆 ≥ 12G。
    ZGC,

    /// 自定义：只做冲突检测，不修改参数。
    CUSTOM;

    /// 返回用于存储的小写值。
    ///
    /// @return 小写枚举名
    @NotNull
    public String storageValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /// 返回 i18n key 后缀（`community.jvm.mode.<后缀>`）。
    ///
    /// @return 小写枚举名
    @NotNull
    public String i18nKey() {
        return "community.jvm.mode." + storageValue();
    }

    /// 解析存储值；`null`、空白或未知值回退到 [#UPSTREAM]。
    ///
    /// @param value 存储值
    /// @return 档位，永不为 `null`
    @NotNull
    public static JvmPreset of(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return UPSTREAM;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UPSTREAM;
        }
    }
}
