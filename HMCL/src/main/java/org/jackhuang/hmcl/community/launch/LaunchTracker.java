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

import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 启动状态机：把 `LauncherHelper` 的钩子转换成可消费的 [LaunchEvent]。
///
/// 判定规则（与文档 08 §3.4 一致）：
/// - 进程存活 ≥ 阈值（默认 120s）→ 启动成功；
/// - 未达阈值且 `ExitType.NORMAL` → 未知（用户很快正常关闭，不算崩溃）；
/// - 未达阈值且非 `NORMAL` → 崩溃；
/// - `ExitType.INTERRUPTED` → 用户主动停止，忽略。
///
/// 状态按实例 id 保存在内存中，进程退出后移除；不做持久化（持久化属于步骤 09/15）。
@NotNullByDefault
public final class LaunchTracker {

    /// 启动结果分类。
    public enum Outcome {
        /// 存活达到阈值。
        SUCCESS,
        /// 未达阈值但正常退出。
        UNKNOWN,
        /// 未达阈值且异常退出。
        CRASH,
        /// 用户主动停止。
        IGNORED
    }

    /// 默认成功阈值（秒）。
    public static final int DEFAULT_SUCCESS_THRESHOLD_SECONDS = 120;

    /// 活跃启动会话，按实例 id 索引。
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /// 事件出口。
    private final Consumer<LaunchEvent> sink;

    /// 成功阈值（秒）。
    private final int successThresholdSeconds;

    /// 使用默认阈值构造。
    ///
    /// @param sink 事件出口
    public LaunchTracker(@NotNull Consumer<LaunchEvent> sink) {
        this(sink, loadDefaultThreshold());
    }

    /// 使用指定阈值构造（测试用）。
    ///
    /// @param sink 事件出口
    /// @param successThresholdSeconds 成功阈值（秒），必须为正数
    public LaunchTracker(@NotNull Consumer<LaunchEvent> sink, int successThresholdSeconds) {
        this.sink = sink;
        this.successThresholdSeconds = Math.max(1, successThresholdSeconds);
    }

    /// 读取默认阈值：`community-settings.json` 的 `launchTracker.successThresholdSeconds`。
    ///
    /// @return 阈值（秒）
    public static int loadDefaultThreshold() {
        try {
            var section = CommunitySettings.get().section("launchTracker");
            if (section.get("successThresholdSeconds") != null) {
                int value = section.get("successThresholdSeconds").getAsInt();
                if (value >= 10 && value <= 3600) {
                    return value;
                }
            }
        } catch (Throwable e) {
            LOG.warning("Failed to read launch success threshold, using default", e);
        }
        return DEFAULT_SUCCESS_THRESHOLD_SECONDS;
    }

    /// 返回成功阈值（秒）。
    ///
    /// @return 阈值
    public int successThresholdSeconds() {
        return successThresholdSeconds;
    }

    /// 处理“启动请求”。
    ///
    /// 必须在 `LauncherHelper` 的 `unmarkVersionLaunchedAbnormally` **之前**调用，
    /// 否则上一次的 `.abnormal` 标记已被删除。
    ///
    /// @param repository 实例仓库
    /// @param versionId 实例 id
    public void onLaunchRequested(@NotNull HMCLGameRepository repository, @NotNull String versionId) {
        Session session = sessions.computeIfAbsent(versionId, key -> new Session());
        session.requestedAt = Instant.now();
        session.previousAbnormal = hasAbnormalMark(repository, versionId);

        emit(LaunchEvent.of(LaunchEventKind.REQUESTED, versionId));
        if (session.previousAbnormal) {
            emit(LaunchEvent.of(LaunchEventKind.PREVIOUS_ABNORMAL, versionId));
        }
    }

    /// 处理“启动任务链完成”事件。
    ///
    /// @param versionId 实例 id
    /// @param gameVersion 游戏版本；未知时 `null`
    public void onTasksCompleted(@NotNull String versionId, @Nullable String gameVersion) {
        Session session = sessions.get(versionId);
        if (session == null) {
            return;
        }
        if (gameVersion != null) {
            session.gameVersion = gameVersion;
        }
        emit(LaunchEvent.of(LaunchEventKind.TASKS_COMPLETED, versionId));
    }

    /// 处理“进程已创建”事件，并安排存活阈值计时。
    ///
    /// @param versionId 实例 id
    /// @param process 游戏进程
    public void onProcessCreated(@NotNull String versionId, @NotNull ManagedProcess process) {
        Session session = sessions.computeIfAbsent(versionId, key -> new Session());
        session.process = process;
        session.processCreatedAt = Instant.now();

        emit(LaunchEvent.of(LaunchEventKind.PROCESS_CREATED, versionId));

        Instant createdAt = session.processCreatedAt;
        session.thresholdFuture = CompletableFuture.runAsync(() -> {
            if (session.exited || !process.isRunning()) {
                return;
            }
            session.survived = true;
            Duration alive = Duration.between(createdAt, Instant.now());
            emit(LaunchEvent.alive(LaunchEventKind.SURVIVED_THRESHOLD, versionId, alive, session.gameVersion));
        }, CompletableFuture.delayedExecutor(successThresholdSeconds, TimeUnit.SECONDS, Schedulers.io()));
    }

    /// 处理“进程退出”事件。
    ///
    /// @param versionId 实例 id
    /// @param exitCode 退出码
    /// @param exitType 退出类型
    public void onProcessExit(@NotNull String versionId, int exitCode, @NotNull ProcessListener.ExitType exitType) {
        Session session = sessions.remove(versionId);
        if (session == null) {
            // 没有观测到启动请求（例如外部直接结束进程），仍发出事件供消费方兜底。
            emit(LaunchEvent.exit(exitEventKind(exitType), versionId, exitCode, exitType, null, null));
            return;
        }

        boolean first;
        synchronized (session) {
            first = !session.exited;
            session.exited = true;
        }
        if (!first) {
            return;
        }

        if (session.thresholdFuture != null) {
            session.thresholdFuture.cancel(false);
            session.thresholdFuture = null;
        }

        Duration alive = session.processCreatedAt == null
                ? null
                : Duration.between(session.processCreatedAt, Instant.now());
        emit(LaunchEvent.exit(exitEventKind(exitType), versionId, exitCode, exitType, alive, session.gameVersion));
    }

    /// 返回该实例是否有正在进行的启动（未退出）。
    ///
    /// @param versionId 实例 id
    /// @return 是否启动中
    public boolean isLaunching(@NotNull String versionId) {
        Session session = sessions.get(versionId);
        return session != null && !session.exited;
    }

    /// 返回该实例的活跃启动会话数量（0 或 1）。
    ///
    /// @param versionId 实例 id
    /// @return 数量
    public int activeSessionCount(@NotNull String versionId) {
        return isLaunching(versionId) ? 1 : 0;
    }

    /// 按退出类型选择事件类型。
    ///
    /// @param exitType 退出类型
    /// @return 事件类型
    @NotNull
    public static LaunchEventKind exitEventKind(@NotNull ProcessListener.ExitType exitType) {
        return switch (exitType) {
            case NORMAL -> LaunchEventKind.EXITED_NORMAL;
            case INTERRUPTED -> LaunchEventKind.ABORTED_BY_USER;
            default -> LaunchEventKind.EXITED_ABNORMAL;
        };
    }

    /// 判定一次退出属于哪种结果。
    ///
    /// @param exitType 退出类型
    /// @param aliveMillis 进程存活毫秒数
    /// @param thresholdSeconds 成功阈值（秒）
    /// @return 分类
    @NotNull
    public static Outcome classify(
            @NotNull ProcessListener.ExitType exitType,
            long aliveMillis,
            int thresholdSeconds) {
        if (exitType == ProcessListener.ExitType.INTERRUPTED) {
            return Outcome.IGNORED;
        }
        if (aliveMillis >= Math.max(1, thresholdSeconds) * 1000L) {
            return Outcome.SUCCESS;
        }
        return exitType == ProcessListener.ExitType.NORMAL ? Outcome.UNKNOWN : Outcome.CRASH;
    }

    /// 实例目录是否存在 `.abnormal` 标记。
    private static boolean hasAbnormalMark(@NotNull HMCLGameRepository repository, @NotNull String versionId) {
        try {
            Path mark = repository.getVersionRoot(versionId).resolve(".abnormal");
            return Files.isRegularFile(mark);
        } catch (Throwable e) {
            LOG.warning("Failed to read abnormal mark for " + versionId, e);
            return false;
        }
    }

    /// 发送事件；出口异常只记录日志。
    private void emit(@NotNull LaunchEvent event) {
        try {
            sink.accept(event);
        } catch (Throwable e) {
            LOG.warning("Failed to dispatch launch event " + event.kind(), e);
        }
    }

    /// 一次启动的内存状态。
    private static final class Session {
        private @Nullable Instant requestedAt;
        private @Nullable Instant processCreatedAt;
        private @Nullable ManagedProcess process;
        private @Nullable CompletableFuture<Void> thresholdFuture;
        private @Nullable String gameVersion;
        private boolean previousAbnormal;
        private boolean survived;
        private volatile boolean exited;
    }
}
