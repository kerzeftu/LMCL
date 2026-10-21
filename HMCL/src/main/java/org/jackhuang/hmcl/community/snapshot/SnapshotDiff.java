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
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// 当前运行目录与目标快照之间的差异。
///
/// @param added 当前有、快照没有的路径
/// @param missing 快照有、当前没有的路径
/// @param changed 两边都有但内容不同的路径
/// @param invalid 对象缺失或校验失败的路径（回滚时不会覆盖当前文件）
@NotNullByDefault
public record SnapshotDiff(
        @NotNull @Unmodifiable List<String> added,
        @NotNull @Unmodifiable List<String> missing,
        @NotNull @Unmodifiable List<String> changed,
        @NotNull @Unmodifiable List<String> invalid) {

    /// 构造空差异。
    @NotNull
    public static SnapshotDiff empty() {
        return new SnapshotDiff(List.of(), List.of(), List.of(), List.of());
    }
}
