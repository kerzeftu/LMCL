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
package org.jackhuang.hmcl.community.memory;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/// 启动器侧内存指标的只读快照（供仪表盘内存卡渲染）。
///
/// 采集来源是 `MemoryMXBean.getHeapMemoryUsage()` 与 [MemoryReclaimer] 的运行时状态，
/// 每次调用只读一次，不做后台高频轮询。
///
/// @param heapUsed 已使用堆（字节）
/// @param heapCommitted 已提交堆（字节）
/// @param heapMax 堆上限（字节）；JVM 未设上限时为 `-1`
/// @param reclaimableEstimate 已提交未使用的保守估计（字节）
/// @param lastReclaimAt 上次回收时间；从未回收时为 `null`
/// @param lastReclaimFreed 上次回收减少的已提交堆（字节，可为 0）
/// @param totalReclaimed 本次启动以来累计减少的已提交堆（字节）
/// @param intervalMinutes 当前自动回收检查间隔（分钟）
/// @param enabled 自动回收当前是否生效
/// @param disabledReason 停用原因代码：`pause_too_long` / `explicit_gc_disabled`；无则为 `null`
/// @param gameRunning 是否有游戏进程已稳定运行（存活超过阈值）
@NotNullByDefault
public record MemoryMetrics(
        long heapUsed,
        long heapCommitted,
        long heapMax,
        long reclaimableEstimate,
        @Nullable Instant lastReclaimAt,
        long lastReclaimFreed,
        long totalReclaimed,
        int intervalMinutes,
        boolean enabled,
        @Nullable String disabledReason,
        boolean gameRunning) {
}
