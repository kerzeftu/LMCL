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

import org.jackhuang.hmcl.community.launch.CommunityLaunchHooks;
import org.jackhuang.hmcl.community.launch.LaunchEvent;
import org.jackhuang.hmcl.community.launch.LaunchEventListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 跨平台内存回收基座（步骤 12）。
///
/// 只调用标准 `System.gc()`，不做堆外操作、不依赖平台特有 API。自动回收的条件：
/// 用户开启、游戏进程已稳定运行（存活超过步骤 08 的阈值）、距上次回收超过间隔、
/// 已提交未使用超过阈值、距最近一次启动请求超过 60 秒、JVM 未禁用显式 GC。
///
/// 触发线程是守护调度线程；[#metrics()] 与 [#setEnabled(boolean)] 可在 JavaFX 线程调用。
/// 回收耗时超过 500ms 时自动拉长间隔（×3，上限 60 分钟），连续两次超过 1s 则自动停用并记录原因。
@NotNullByDefault
public final class MemoryReclaimer {

    /// 日志前缀，便于按前缀过滤。
    public static final String LOG_PREFIX = "[CommunityMemory]";

    /// 周期检查间隔（秒），与文档一致。
    static final long CHECK_PERIOD_SECONDS = 30;
    /// 最近一次启动请求后的静默保护期。
    static final long LAUNCH_GUARD_MILLIS = 60_000L;
    /// 手动回收冷却。
    static final long MANUAL_COOLDOWN_MILLIS = 60_000L;
    /// 超过该耗时视为“停顿偏慢”，把间隔拉长。
    static final long SLOW_WARN_MILLIS = 500L;
    /// 超过该耗时累计两次即自动停用。
    static final long SLOW_DISABLE_MILLIS = 1_000L;
    /// 连续慢次数达到该值自动停用。
    static final int SLOW_STREAK_LIMIT = 2;
    /// 允许自动回收的最小已提交堆。
    static final long MIN_COMMITTED_BYTES = 256L * 1024 * 1024;
    /// 间隔上限（分钟）。
    static final int MAX_INTERVAL_MINUTES = 60;

    private static volatile MemoryReclaimer instance;

    /// 安装回收器；重复调用无副作用。应在 `CommunityBootstrap` 中、UI 启动前调用。
    public static void install() {
        instance();
    }

    /// 获取全局实例（首次调用时创建调度线程并注册启动事件监听）。
    @NotNull
    public static MemoryReclaimer instance() {
        MemoryReclaimer local = instance;
        if (local == null) {
            synchronized (MemoryReclaimer.class) {
                local = instance;
                if (local == null) {
                    local = new MemoryReclaimer();
                    instance = local;
                }
            }
        }
        return local;
    }

    private final ScheduledExecutorService scheduler;
    private final LaunchEventListener launchListener;
    private final boolean explicitGcDisabled;
    private final Set<String> survived = ConcurrentHashMap.newKeySet();
    private final AtomicInteger slowStreak = new AtomicInteger();
    private final AtomicBoolean shutdown = new AtomicBoolean();

    private volatile MemoryBaseSettings settings;
    private volatile long lastLaunchRequestAtMillis;
    private volatile long lastManualReclaimAtMillis;
    private volatile long lastFreedBytes;

    private MemoryReclaimer() {
        this.explicitGcDisabled = detectExplicitGcDisabled(
                ManagementFactory.getRuntimeMXBean().getInputArguments());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CommunityMemoryReclaimer");
            thread.setDaemon(true);
            return thread;
        });
        this.launchListener = this::onLaunchEvent;
        CommunityLaunchHooks.addListener(launchListener);
        scheduler.scheduleWithFixedDelay(this::periodicCheck,
                CHECK_PERIOD_SECONDS, CHECK_PERIOD_SECONDS, TimeUnit.SECONDS);
        try {
            Runtime.getRuntime().addShutdownHook(
                    new Thread(this::shutdown, "CommunityMemoryReclaimer-shutdown"));
        } catch (IllegalStateException ignored) {
            // JVM 已在关闭流程中，无需再注册。
        }
    }

    /// 当前指标快照；读取堆信息与设置，不做阻塞 IO（设置首次加载除外）。
    @NotNull
    public MemoryMetrics metrics() {
        MemoryBaseSettings current = settings();
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long committed = heap.getCommitted();
        long used = heap.getUsed();
        return new MemoryMetrics(
                used,
                committed,
                heap.getMax(),
                Math.max(0, committed - used),
                current.lastReclaimAt(),
                lastFreedBytes,
                current.totalReclaimedMiB() * 1024L * 1024L,
                current.intervalMinutes(),
                isEffectivelyEnabled(current),
                disabledReason(current),
                !survived.isEmpty());
    }

    /// 手动回收；60 秒冷却内或 JVM 禁用显式 GC 时返回 `false`。
    ///
    /// 真正的 GC 在守护线程执行，返回 `true` 只表示已受理。
    public synchronized boolean reclaimNow() {
        if (shutdown.get() || explicitGcDisabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (!canManualReclaim(now, lastManualReclaimAtMillis)) {
            return false;
        }
        try {
            scheduler.execute(() -> {
                try {
                    doReclaim("manual");
                } catch (Throwable e) {
                    LOG.warning(LOG_PREFIX + " manual reclaim failed", e);
                }
            });
        } catch (RejectedExecutionException e) {
            return false;
        }
        lastManualReclaimAtMillis = now;
        return true;
    }

    /// 开启或关闭自动回收；重新开启会清除自动停用状态。
    public void setEnabled(boolean enabled) {
        MemoryBaseSettings current = settings();
        current.setEnabled(enabled);
        if (enabled) {
            current.setAutoDisabled(false, null);
            slowStreak.set(0);
        }
        current.save();
    }

    /// 自动回收当前是否生效（用户开启且未被自动停用、且 JVM 未禁用显式 GC）。
    public boolean isEnabled() {
        return isEffectivelyEnabled(settings());
    }

    private MemoryBaseSettings settings() {
        MemoryBaseSettings local = settings;
        if (local == null) {
            synchronized (this) {
                local = settings;
                if (local == null) {
                    local = MemoryBaseSettings.load();
                    settings = local;
                }
            }
        }
        return local;
    }

    private boolean isEffectivelyEnabled(MemoryBaseSettings current) {
        return current.enabled() && !current.autoDisabled() && !explicitGcDisabled;
    }

    private @Nullable String disabledReason(MemoryBaseSettings current) {
        if (explicitGcDisabled) {
            return "explicit_gc_disabled";
        }
        if (current.autoDisabled()) {
            return current.disabledReason() == null ? "pause_too_long" : current.disabledReason();
        }
        return null;
    }

    private void onLaunchEvent(@NotNull LaunchEvent event) {
        try {
            switch (event.kind()) {
                case REQUESTED -> lastLaunchRequestAtMillis = event.timestamp();
                case SURVIVED_THRESHOLD -> survived.add(event.versionId());
                case PROCESS_CREATED, EXITED_NORMAL, EXITED_ABNORMAL, ABORTED_BY_USER ->
                        survived.remove(event.versionId());
                default -> {
                }
            }
        } catch (Throwable e) {
            LOG.warning(LOG_PREFIX + " launch event handling failed", e);
        }
    }

    private void periodicCheck() {
        if (shutdown.get()) {
            return;
        }
        try {
            MemoryBaseSettings current = settings();
            String reason = rejectReason(createContext(current));
            if (reason == null) {
                doReclaim("periodic");
            } else if (!"no_game".equals(reason) && !"interval".equals(reason)) {
                LOG.debug(LOG_PREFIX + " skip periodic reclaim: " + reason);
            }
        } catch (Throwable e) {
            LOG.warning(LOG_PREFIX + " periodic check failed", e);
        }
    }

    private TriggerContext createContext(MemoryBaseSettings current) {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long now = System.currentTimeMillis();
        long lastReclaim = current.lastReclaimAt() == null
                ? Long.MIN_VALUE : current.lastReclaimAt().toEpochMilli();
        long sinceLastReclaim = lastReclaim == Long.MIN_VALUE ? Long.MAX_VALUE : now - lastReclaim;
        long sinceLaunch = lastLaunchRequestAtMillis == 0
                ? Long.MAX_VALUE : now - lastLaunchRequestAtMillis;
        return new TriggerContext(
                isEffectivelyEnabled(current),
                explicitGcDisabled,
                !survived.isEmpty(),
                sinceLastReclaim,
                sinceLaunch,
                heap.getCommitted(),
                heap.getUsed(),
                current.intervalMinutes(),
                current.minReclaimMiB());
    }

    private void doReclaim(String trigger) {
        MemoryUsage before = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long beforeCommitted = before.getCommitted();
        long start = System.nanoTime();
        System.gc();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
        MemoryUsage after = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long freed = Math.max(0, beforeCommitted - after.getCommitted());

        MemoryBaseSettings current = settings();
        Instant now = Instant.now();
        lastFreedBytes = freed;
        current.recordReclaim(now, freed);
        if ("periodic".equals(trigger)) {
            applyAdaptivePolicy(current, elapsedMillis);
        }
        current.save();

        LOG.info(String.format(
                "%s trigger=%s beforeCommitted=%d afterCommitted=%d freed=%d elapsedMs=%d intervalMinutes=%d",
                LOG_PREFIX, trigger, beforeCommitted, after.getCommitted(), freed,
                elapsedMillis, current.intervalMinutes()));
    }

    /// 自适应：慢回收拉长间隔；连续两次超过 1 秒则自动停用。
    private void applyAdaptivePolicy(MemoryBaseSettings current, long elapsedMillis) {
        if (elapsedMillis > SLOW_DISABLE_MILLIS) {
            int streak = slowStreak.incrementAndGet();
            if (streak >= SLOW_STREAK_LIMIT && !current.autoDisabled()) {
                current.setAutoDisabled(true, "pause_too_long");
                LOG.warning(String.format(
                        "%s auto disabled: %d consecutive reclaims exceeded %dms",
                        LOG_PREFIX, streak, SLOW_DISABLE_MILLIS));
            }
        } else {
            slowStreak.set(0);
        }
        if (elapsedMillis > SLOW_WARN_MILLIS) {
            int next = escalatedInterval(current.intervalMinutes(), elapsedMillis);
            if (next != current.intervalMinutes()) {
                current.setIntervalMinutes(next);
                LOG.info(String.format(
                        "%s slow reclaim (%dms), interval escalated to %d minutes",
                        LOG_PREFIX, elapsedMillis, next));
            }
        }
    }

    private void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            CommunityLaunchHooks.removeListener(launchListener);
            scheduler.shutdownNow();
        }
    }

    // region 可单测的纯逻辑

    /// 一次周期检查的输入。
    record TriggerContext(
            boolean enabled,
            boolean explicitGcDisabled,
            boolean gameRunning,
            long sinceLastReclaimMillis,
            long sinceLaunchRequestMillis,
            long committedBytes,
            long usedBytes,
            int intervalMinutes,
            int minReclaimMiB) {
    }

    /// 判断本次周期检查是否应跳过；返回 `null` 表示可以回收。
    static @Nullable String rejectReason(TriggerContext context) {
        if (!context.enabled()) {
            return "disabled";
        }
        if (context.explicitGcDisabled()) {
            return "explicit_gc_disabled";
        }
        if (!context.gameRunning()) {
            return "no_game";
        }
        if (context.sinceLastReclaimMillis() < context.intervalMinutes() * 60_000L) {
            return "interval";
        }
        if (context.sinceLaunchRequestMillis() < LAUNCH_GUARD_MILLIS) {
            return "launch_guard";
        }
        if (context.committedBytes() < MIN_COMMITTED_BYTES) {
            return "committed_too_low";
        }
        if (context.committedBytes() - context.usedBytes()
                < context.minReclaimMiB() * 1024L * 1024L) {
            return "reclaimable_too_low";
        }
        return null;
    }

    /// 慢回收后的新间隔：耗时超过 500ms 时乘三，最高 60 分钟。
    static int escalatedInterval(int currentMinutes, long elapsedMillis) {
        if (elapsedMillis <= SLOW_WARN_MILLIS) {
            return currentMinutes;
        }
        return Math.min(MAX_INTERVAL_MINUTES, Math.max(1, currentMinutes * 3));
    }

    /// 手动回收冷却判断。
    static boolean canManualReclaim(long nowMillis, long lastManualMillis) {
        return nowMillis - lastManualMillis >= MANUAL_COOLDOWN_MILLIS;
    }

    /// JVM 参数中是否带 `-XX:+DisableExplicitGC`。
    static boolean detectExplicitGcDisabled(List<String> inputArguments) {
        for (String argument : inputArguments) {
            if ("-XX:+DisableExplicitGC".equals(argument)) {
                return true;
            }
        }
        return false;
    }

    // endregion
}
