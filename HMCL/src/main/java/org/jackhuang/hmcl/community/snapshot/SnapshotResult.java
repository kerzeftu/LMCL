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

/// 一次采集的结果。
///
/// @param snapshotId 成功时的快照 id
/// @param skipped 是否跳过
/// @param skipReason 跳过原因代码（`disabled` / `game_running` / `lock_timeout` / `disk_low` / `error`）
/// @param info 成功时的摘要
@NotNullByDefault
public record SnapshotResult(
        @Nullable String snapshotId,
        boolean skipped,
        @Nullable String skipReason,
        @Nullable SnapshotInfo info) {

    /// 构造成功结果。
    ///
    /// @param info 摘要
    /// @return 结果
    @NotNull
    public static SnapshotResult success(@NotNull SnapshotInfo info) {
        return new SnapshotResult(info.id(), false, null, info);
    }

    /// 构造跳过结果。
    ///
    /// @param reason 原因代码
    /// @return 结果
    @NotNull
    public static SnapshotResult skipped(@NotNull String reason) {
        return new SnapshotResult(null, true, reason, null);
    }
}
