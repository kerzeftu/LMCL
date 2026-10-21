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
package org.jackhuang.hmcl.community.snapshot;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

/// 快照的物化方式。
@NotNullByDefault
public enum SnapshotMode {

    /// 除必须复制的文件外，均以硬链接保存。
    HARDLINK("hardlink"),

    /// 全部文件真实复制（硬链接不可用时自动降级）。
    COPY("copy"),

    /// 资源预算不足时只记录文件清单，不保存文件内容。
    MANIFEST_ONLY("manifest-only");

    private final String jsonName;

    SnapshotMode(@NotNull String jsonName) {
        this.jsonName = jsonName;
    }

    /// 返回 manifest 中的字符串值。
    ///
    /// @return 字符串值
    @NotNull
    public String jsonName() {
        return jsonName;
    }

    /// 解析 manifest 中的字符串值；未知值按 [HARDLINK] 处理。
    ///
    /// @param value 字符串值
    /// @return 模式
    @NotNull
    public static SnapshotMode fromJson(@NotNull String value) {
        for (SnapshotMode mode : values()) {
            if (mode.jsonName.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return HARDLINK;
    }
}
