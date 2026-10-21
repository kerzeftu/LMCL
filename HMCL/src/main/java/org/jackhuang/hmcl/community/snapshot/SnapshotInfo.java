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

import java.time.Instant;

/// 管理界面使用的快照摘要。
///
/// @param id 快照 id
/// @param at 采集时间
/// @param reason 产生原因
/// @param files 文件数量
/// @param bytes 文件总字节数
/// @param manifestOnly 是否只保存了清单
/// @param pinned 是否为回滚前自动快照（不参与自动清理）
@NotNullByDefault
public record SnapshotInfo(
        @NotNull String id,
        @NotNull Instant at,
        @NotNull SnapshotReason reason,
        long files,
        long bytes,
        boolean manifestOnly,
        boolean pinned) {
}
