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
package org.jackhuang.hmcl.community.launch;

import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// 启动任务链上的一个社区任务（例如 08 的体检、16 的成功启动快照）。
///
/// 任务由 [CommunityLaunchHooks#runStartupTasks(LaunchContext)] 按 [LaunchStartupTask#order()]
/// 升序依次执行；任务的 `Task` 未完成前游戏进程不会创建。
///
/// 约定：
/// - 任务失败（抛异常或 Task 失败）只记录日志，**不阻断启动**；
/// - 需要阻止本次启动时抛出 `CancellationException`（上游会静默处理）；
/// - 任务在非 JavaFX 线程执行，碰 UI 必须自行切回 JavaFX 线程。
@NotNullByDefault
public interface LaunchStartupTask {

    /// 任务唯一 id（用于日志与排错）。
    ///
    /// @return id
    @NotNull
    String id();

    /// 执行顺序，数值越小越早执行（体检 100、快照 200、预留 300）。
    ///
    /// @return 顺序值
    int order();

    /// 执行任务。
    ///
    /// @param context 启动上下文
    /// @return 需要异步执行的 Task；同步完成可返回 [Task#completed(Object)]
    /// @throws Exception 任务失败（会被 [CommunityLaunchHooks] 吞掉并记录日志）
    @NotNull
    Task<?> run(@NotNull LaunchContext context) throws Exception;

    /// 可选的展示名；默认返回 [LaunchStartupTask#id()]。
    ///
    /// @return 展示名
    @NotNull
    default String name() {
        return id();
    }

    /// 是否启用该任务。
    ///
    /// @return 默认启用
    default boolean isEnabled() {
        return true;
    }

    /// 任务不可用时的说明；`null` 表示无。
    ///
    /// @return 说明或 `null`
    @Nullable
    default String disabledReason() {
        return null;
    }
}
