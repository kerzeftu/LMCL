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

import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.task.AsyncTaskExecutor;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 社区版的唯一启动钩子入口。
///
/// 由 `LauncherHelper` 在 4 个锚点调用（见文档 08 §2.2），其余代码只通过本类注册监听器与启动任务。
/// 所有方法都保证不会把异常抛给上游：钩子内部任何失败只记录日志，
/// 唯一的例外是启动任务主动抛出的 [CancellationException]（用于用户取消本次启动）。
@NotNullByDefault
public final class CommunityLaunchHooks {

    /// 运行时注册的事件监听器。
    private static final List<LaunchEventListener> LISTENERS = new CopyOnWriteArrayList<>();

    /// 启动任务（ServiceLoader 发现 + 运行时注册）。
    private static final List<LaunchStartupTask> STARTUP_TASKS = new CopyOnWriteArrayList<>();

    /// ServiceLoader 是否已执行。
    private static volatile boolean initialized;

    /// 启动状态机，首次使用时创建。
    private static volatile @Nullable LaunchTracker tracker;

    private CommunityLaunchHooks() {
    }

    /// 返回启动状态机（懒加载）。
    ///
    /// @return 状态机
    @NotNull
    public static LaunchTracker tracker() {
        LaunchTracker local = tracker;
        if (local == null) {
            synchronized (CommunityLaunchHooks.class) {
                local = tracker;
                if (local == null) {
                    local = new LaunchTracker(CommunityLaunchHooks::fireEvent);
                    tracker = local;
                }
            }
        }
        return local;
    }

    /// 注册事件监听器；重复注册会被忽略。
    ///
    /// @param listener 监听器
    public static void addListener(@NotNull LaunchEventListener listener) {
        ensureInitialized();
        LISTENERS.add(listener);
    }

    /// 移除事件监听器。
    ///
    /// @param listener 监听器
    public static void removeListener(@NotNull LaunchEventListener listener) {
        LISTENERS.remove(listener);
    }

    /// 注册启动任务；重复 id 会被忽略。
    ///
    /// @param task 任务
    public static void registerStartupTask(@NotNull LaunchStartupTask task) {
        ensureInitialized();
        for (LaunchStartupTask existing : STARTUP_TASKS) {
            if (existing.id().equals(task.id())) {
                LOG.warning("Duplicate community startup task id: " + task.id());
                return;
            }
        }
        STARTUP_TASKS.add(task);
    }

    /// 返回当前已注册的启动任务（按 order 升序，只读副本）。
    ///
    /// @return 任务列表
    @NotNull
    public static List<LaunchStartupTask> startupTasks() {
        ensureInitialized();
        List<LaunchStartupTask> tasks = new ArrayList<>(STARTUP_TASKS);
        tasks.sort(Comparator.comparingInt(LaunchStartupTask::order));
        return tasks;
    }

    /// 返回当前已注册的事件监听器（只读副本）。
    ///
    /// @return 监听器列表
    @NotNull
    public static List<LaunchEventListener> listeners() {
        ensureInitialized();
        return List.copyOf(LISTENERS);
    }

    /// 处理“启动请求”（锚点 A，必须在 `unmarkVersionLaunchedAbnormally` 之前）。
    ///
    /// @param repository 实例仓库
    /// @param versionId 实例 id
    public static void onLaunchRequested(@NotNull HMCLGameRepository repository, @NotNull String versionId) {
        try {
            tracker().onLaunchRequested(repository, versionId);
        } catch (Throwable e) {
            LOG.warning("Community launch hook onLaunchRequested failed", e);
        }
    }

    /// 处理“进程已创建”（锚点 C）。
    ///
    /// @param versionId 实例 id
    /// @param process 游戏进程
    public static void onProcessCreated(@NotNull String versionId, @NotNull ManagedProcess process) {
        try {
            tracker().onProcessCreated(versionId, process);
        } catch (Throwable e) {
            LOG.warning("Community launch hook onProcessCreated failed", e);
        }
    }

    /// 处理“进程退出”（锚点 D）。
    ///
    /// @param versionId 实例 id
    /// @param exitCode 退出码
    /// @param exitType 退出类型
    public static void onProcessExit(@NotNull String versionId, int exitCode, @NotNull ProcessListener.ExitType exitType) {
        try {
            tracker().onProcessExit(versionId, exitCode, exitType);
        } catch (Throwable e) {
            LOG.warning("Community launch hook onProcessExit failed", e);
        }
    }

    /// 该实例是否正在启动中。
    ///
    /// @param versionId 实例 id
    /// @return 是否启动中
    public static boolean isLaunching(@NotNull String versionId) {
        try {
            return tracker().isLaunching(versionId);
        } catch (Throwable e) {
            return false;
        }
    }

    /// 该实例活跃的启动会话数。
    ///
    /// @param versionId 实例 id
    /// @return 数量
    public static int activeSessionCount(@NotNull String versionId) {
        try {
            return tracker().activeSessionCount(versionId);
        } catch (Throwable e) {
            return 0;
        }
    }

    /// 执行启动任务链（锚点 B，插入在登录之前）。
    ///
    /// 返回的 Task 永不失败（除非任务主动抛出 [CancellationException]）；
    /// 单任务失败、超时、返回 `null` 都只记录日志并继续下一个任务。
    ///
    /// @param context 启动上下文
    /// @return 完成时代表所有任务执行完毕
    @NotNull
    public static Task<Void> runStartupTasks(@NotNull LaunchContext context) {
        return Task.runAsync("community: startup tasks", Schedulers.io(), () -> runStartupTasksNow(context));
    }

    /// 同步执行所有启动任务（供 Task 调用与单测）。
    ///
    /// @param context 启动上下文
    /// @throws CancellationException 任务要求取消本次启动
    public static void runStartupTasksNow(@NotNull LaunchContext context) throws CancellationException {
        try {
            for (LaunchStartupTask task : startupTasks()) {
                if (!task.isEnabled()) {
                    LOG.info("Skip community startup task " + task.id() + ": " + task.disabledReason());
                    continue;
                }
                try {
                    Task<?> subTask = task.run(context);
                    if (subTask == null) {
                        continue;
                    }
                    AsyncTaskExecutor executor = new AsyncTaskExecutor(subTask);
                    if (!executor.test()) {
                        Exception exception = executor.getException();
                        if (exception == null || exception instanceof CancellationException) {
                            // Cancelled tasks report a null exception; treat them as an explicit abort request.
                            throw new CancellationException("community startup task " + task.id() + " was cancelled");
                        }
                        LOG.warning("Community startup task " + task.id() + " failed", exception);
                    }
                } catch (CancellationException e) {
                    throw e;
                } catch (Throwable e) {
                    LOG.warning("Community startup task " + task.id() + " failed", e);
                }
            }
            tracker().onTasksCompleted(context.versionId(), context.gameVersion());
        } catch (CancellationException e) {
            throw e;
        } catch (Throwable e) {
            LOG.warning("Community startup task chain failed", e);
        }
    }

    /// 分发事件到所有监听器；单个监听器异常只记录日志。
    private static void fireEvent(@NotNull LaunchEvent event) {
        for (LaunchEventListener listener : LISTENERS) {
            try {
                listener.onLaunchEvent(event);
            } catch (Throwable e) {
                LOG.warning("Community launch event listener failed: " + listener, e);
            }
        }
    }

    /// 首次调用时通过 ServiceLoader 发现监听器与启动任务。
    private static synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            for (LaunchEventListener listener : ServiceLoader.load(LaunchEventListener.class)) {
                LISTENERS.add(listener);
            }
        } catch (Throwable e) {
            LOG.warning("Failed to discover community launch listeners", e);
        }
        try {
            for (LaunchStartupTask task : ServiceLoader.load(LaunchStartupTask.class)) {
                if (STARTUP_TASKS.stream().noneMatch(it -> it.id().equals(task.id()))) {
                    STARTUP_TASKS.add(task);
                }
            }
        } catch (Throwable e) {
            LOG.warning("Failed to discover community startup tasks", e);
        }
    }
}
