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

import org.jackhuang.hmcl.community.launch.LaunchContext;
import org.jackhuang.hmcl.community.launch.LaunchStartupTask;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 启动链上的内存优化任务（`order = 150`）。
///
/// 仅当实例生效设置启用了 `memoryOptimizationOnLaunch` 时，在游戏进程创建前执行一次
/// [MemoryReclaimer#optimizeForLaunch()]。该调用不受手动回收 60 秒冷却与 10 分钟周期限制，
/// 但会跳过 `-XX:+DisableExplicitGC`、已关闭的 JVM 与非交互式启动（生成启动脚本）。
///
/// 任务失败只记录日志，不阻断启动。
@NotNullByDefault
public final class MemoryLaunchOptimizationTask implements LaunchStartupTask {

    /// 任务 id。
    public static final String ID = "memory-optimization";

    /// 任务顺序：体检 100 之后、快照 200 之前。
    public static final int ORDER = 150;

    /// 构造任务。
    public MemoryLaunchOptimizationTask() {
    }

    /// 返回任务 id。
    ///
    /// @return id
    @Override
    @NotNull
    public String id() {
        return ID;
    }

    /// 返回执行顺序。
    ///
    /// @return 150
    @Override
    public int order() {
        return ORDER;
    }

    /// 执行启动前内存优化。
    ///
    /// @param context 启动上下文
    /// @return 已完成的 Task
    @Override
    @NotNull
    public Task<?> run(@NotNull LaunchContext context) {
        if (!context.interactive()) {
            return Task.completed(null);
        }
        GameSettings.Effective setting = context.setting();
        if (setting == null || !setting.getInheritable(GameSettings::memoryOptimizationOnLaunchProperty)) {
            return Task.completed(null);
        }

        try {
            boolean reclaimed = MemoryReclaimer.instance().optimizeForLaunch();
            LOG.info(String.format(
                    "trigger=launch versionId=%s reclaimed=%s",
                    context.versionId(), reclaimed));
        } catch (Throwable e) {
            LOG.warning("launch-time memory optimization failed", e);
        }
        return Task.completed(null);
    }
}
