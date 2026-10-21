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
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// 一次回滚的结果。
///
/// @param restored 成功还原的文件数
/// @param deleted 成功删除的新增文件数
/// @param failed 失败的文件数
/// @param failedPaths 失败路径（相对运行目录）
/// @param preRestoreSnapshotId 回滚前快照 id；`null` 表示预拍失败、回滚未执行
@NotNullByDefault
public record RestoreResult(
        int restored,
        int deleted,
        int failed,
        @NotNull @Unmodifiable List<String> failedPaths,
        @Nullable String preRestoreSnapshotId) {

    /// 构造已中止的回滚结果。
    ///
    /// @param failedPaths 无法处理的原因
    /// @return 结果
    @NotNull
    public static RestoreResult aborted(@NotNull List<String> failedPaths) {
        return new RestoreResult(0, 0, failedPaths.size(), List.copyOf(failedPaths), null);
    }
}
