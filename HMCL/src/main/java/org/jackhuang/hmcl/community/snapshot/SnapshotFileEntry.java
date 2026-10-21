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
import org.jetbrains.annotations.Nullable;

/// 快照中一个文件的元数据。
///
/// `object` 是内容寻址对象相对 objects 目录的路径（`ab/cdef…`）；`null` 表示
/// manifest-only 快照没有保存内容。`link` 记录物化方式：`hardlink` / `copy`。
///
/// @param path 相对运行目录的路径，使用 `/` 分隔
/// @param size 文件字节数
/// @param sha256 内容的 SHA-256（小写十六进制）
/// @param object 对象路径，或 `null`
/// @param link 物化方式，或 `null`
/// @param mtime 采集时的修改时间（epoch millis）
@NotNullByDefault
public record SnapshotFileEntry(
        @NotNull String path,
        long size,
        @NotNull String sha256,
        @Nullable String object,
        @Nullable String link,
        long mtime) {
}
