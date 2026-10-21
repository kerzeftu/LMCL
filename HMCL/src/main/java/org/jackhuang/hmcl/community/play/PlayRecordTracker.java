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
package org.jackhuang.hmcl.community.play;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jackhuang.hmcl.community.launch.LaunchEvent;
import org.jackhuang.hmcl.community.launch.LaunchEventListener;
import org.jackhuang.hmcl.community.launch.LaunchEventKind;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 游玩打点：把 08 的启动事件序列折算为按日累计的游玩时长。
///
/// 通过 `META-INF/services/org.jackhuang.hmcl.community.launch.LaunchEventListener`
/// 注册，不依赖首页仪表盘是否挂载。事件按到达顺序串行处理（同一 CompletableFuture 链），
/// 文件读写发生在 `Schedulers.io()`；UI 线程不做磁盘操作。
@NotNullByDefault
public final class PlayRecordTracker implements LaunchEventListener {

    /// 默认有效游玩阈值（秒），与 08 的 `launchTracker.successThresholdSeconds` 一致。
    public static final int DEFAULT_THRESHOLD_SECONDS = 120;

    /// 事件处理链，保证 PROCESS_CREATED 先于 EXITED 落盘。
    private static final Object CHAIN_LOCK = new Object();
    private static CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

    private final PlayRecordStore store;

    /// ServiceLoader 使用的无参构造。
    public PlayRecordTracker() {
        this(PlayRecordStore.get());
    }

    /// 测试用构造。
    ///
    /// @param store 存储
    PlayRecordTracker(@NotNull PlayRecordStore store) {
        this.store = store;
    }

    @Override
    public void onLaunchEvent(@NotNull LaunchEvent event) {
        submit(() -> apply(event, System.currentTimeMillis()));
    }

    /// 恢复上次启动器被强杀时残留的未闭合会话；应在 UI 启动前调用一次。
    public static void recoverNow() {
        try {
            PlayRecordStore store = PlayRecordStore.get();
            recover(store, thresholdSeconds(), System.currentTimeMillis());
        } catch (Throwable e) {
            LOG.warning("[CommunityPlay] Failed to recover play session", e);
        }
    }

    /// 恢复指定存储中的残留会话（测试直接调用）。
    ///
    /// @param store 存储
    /// @param thresholdSeconds 有效阈值
    /// @param nowMillis 当前时刻
    static void recover(@NotNull PlayRecordStore store, int thresholdSeconds, long nowMillis) {
        Map<String, PlayRecordStore.Session> sessions = store.loadSessions();
        if (sessions.isEmpty()) {
            return;
        }
        boolean enabled = store.enabled();
        if (enabled) {
            LocalDate day = LocalDate.now();
            for (PlayRecordStore.Session session : sessions.values()) {
                long elapsedSeconds = Math.max(0, (nowMillis - session.startedAtMillis()) / 1000L);
                if (elapsedSeconds >= thresholdSeconds) {
                    store.addSession(day, elapsedSeconds, true);
                    LOG.info("[CommunityPlay] Recovered estimated session: " + elapsedSeconds + "s");
                }
            }
        }
        store.saveSessions(Map.of());
    }

    /// 是否应计入一次会话。
    ///
    /// @param durationSeconds 会话秒数
    /// @param thresholdSeconds 有效阈值
    /// @return 达到阈值返回 `true`
    public static boolean shouldRecord(long durationSeconds, int thresholdSeconds) {
        return durationSeconds >= Math.max(0, thresholdSeconds);
    }

    /// 串行提交事件；异常只记日志，不影响启动流程。
    private static void submit(@NotNull Runnable task) {
        synchronized (CHAIN_LOCK) {
            chain = chain.handle((ignored, error) -> null).thenRunAsync(() -> {
                try {
                    task.run();
                } catch (Throwable e) {
                    LOG.warning("[CommunityPlay] Failed to handle launch event", e);
                }
            }, Schedulers.io());
        }
    }

    private void apply(@NotNull LaunchEvent event, long nowMillis) {
        String versionId = event.versionId();
        Map<String, PlayRecordStore.Session> sessions = store.loadSessions();
        boolean enabled = store.enabled();

        switch (event.kind()) {
            case REQUESTED -> {
                if (sessions.remove(versionId) != null) {
                    store.saveSessions(sessions);
                }
            }
            case PROCESS_CREATED -> {
                if (enabled) {
                    sessions.put(versionId, new PlayRecordStore.Session(nowMillis, false));
                    store.saveSessions(sessions);
                }
            }
            case SURVIVED_THRESHOLD -> {
                PlayRecordStore.Session session = sessions.get(versionId);
                if (session != null) {
                    sessions.put(versionId, new PlayRecordStore.Session(session.startedAtMillis(), true));
                    store.saveSessions(sessions);
                }
            }
            case EXITED_NORMAL, EXITED_ABNORMAL -> {
                PlayRecordStore.Session session = sessions.remove(versionId);
                store.saveSessions(sessions);
                if (!enabled || session == null || !session.trusted()) {
                    return;
                }
                long durationSeconds = event.alive() != null
                        ? Math.max(0, event.alive().getSeconds())
                        : Math.max(0, (nowMillis - session.startedAtMillis()) / 1000L);
                if (shouldRecord(durationSeconds, thresholdSeconds())) {
                    store.addSession(LocalDate.now(), durationSeconds, false);
                    LOG.info("[CommunityPlay] Recorded session: " + versionId + " " + durationSeconds + "s");
                }
            }
            case ABORTED_BY_USER, LAUNCH_FAILED -> {
                if (sessions.remove(versionId) != null) {
                    store.saveSessions(sessions);
                }
            }
            default -> {
            }
        }
    }

    /// 读取 08 的有效游玩阈值。
    ///
    /// @return 秒
    static int thresholdSeconds() {
        try {
            JsonObject section = CommunitySettings.get().section("launchTracker");
            return Math.max(0, JsonUtils.getInt(section, "successThresholdSeconds", DEFAULT_THRESHOLD_SECONDS));
        } catch (Throwable e) {
            return DEFAULT_THRESHOLD_SECONDS;
        }
    }

    /// 计算时长秒数（供测试/诊断）。
    ///
    /// @param startMillis 开始时刻
    /// @param endMillis 结束时刻
    /// @return 秒数，不为负
    public static long durationSeconds(long startMillis, long endMillis) {
        return Math.max(0, Duration.ofMillis(endMillis - startMillis).getSeconds());
    }
}
