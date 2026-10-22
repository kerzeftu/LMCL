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
package org.jackhuang.hmcl.community.download;

import javafx.application.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 下载源自动测速排序。
///
/// 进入下载入口、保存下载源或启动时触发一次异步探测：并行请求每个源的
/// `/mc/game/version_manifest.json`，按响应延迟从低到高生成候选顺序；失败的源
/// 排在最后。结果缓存 [CommunityDownloadRanker#TTL_MILLIS] 毫秒，过期后下次
/// 下载再测。排序完成后会通知 [CommunityDownloadInstaller] 用新顺序重建
/// provider，并把顺序静默写回配置（设置页正在显示时只应用、不写盘，避免覆盖
/// 用户正在编辑的内容）。
///
/// 下载开始前由 [RankingDownloadProvider] 调用 [CommunityDownloadRanker#awaitBeforeDownload]：
/// 若本次下载入口标记了 [CommunityDownloadRanker#markDownloadEntry]，或缓存已过期，
/// 则等待测速完成后再返回，保证第一个文件请求使用新顺序。
///
/// 线程：所有公开方法可在任意线程调用；实际探测与 provider 重建在
/// `Schedulers.io()` 上执行，不阻塞 JavaFX 线程。
@NotNullByDefault
public final class CommunityDownloadRanker {

    /// 测速结果缓存时间（5 分钟）。
    static final long TTL_MILLIS = 5L * 60L * 1_000L;

    /// 下载开始前等待测速的最长时间（12 秒）。
    static final long DOWNLOAD_WAIT_MILLIS = 12_000L;

    /// 超过该时间没有 provider 调用，视为新的一次下载会话。
    static final long DOWNLOAD_SESSION_IDLE_MILLIS = 2L * 60L * 1_000L;

    /// 测速锁。
    private static final Object LOCK = new Object();

    /// 最近一次测速得到的地址顺序（最快的在前）。
    private static volatile List<String> rankedUrls = List.of();

    /// 最近一次测速时间（`System.currentTimeMillis()`）。
    private static volatile long testedAt;

    /// 是否有测速任务正在运行。
    private static volatile boolean running;

    /// 运行期间收到强制刷新时，完成后是否再跑一轮。
    private static volatile boolean pendingRefresh;

    /// 当前这轮测速的完成信号；没有运行时为已完成的 future。
    private static volatile CompletableFuture<Void> activeRefresh = CompletableFuture.completedFuture(null);

    /// 进入下载入口时置位，首个 provider 调用消费一次。
    private static volatile boolean downloadEntryPending;

    /// 最近一次 provider 调用时间。
    private static volatile long lastProviderUse;

    /// 本类只提供静态方法。
    private CommunityDownloadRanker() {
    }

    /// 按最近一次测速结果排列下载源；未测速或配置变化时保留原顺序。
    ///
    /// @param sources 当前配置的下载源
    /// @return 排序后的下载源
    @NotNull
    public static List<CommunityDownloadSource> order(@NotNull List<CommunityDownloadSource> sources) {
        List<String> ranking = rankedUrls;
        if (ranking.isEmpty() || sources.isEmpty()) {
            return sources;
        }

        Map<String, CommunityDownloadSource> remaining = new HashMap<>();
        for (CommunityDownloadSource source : sources) {
            remaining.put(source.url(), source);
        }

        List<CommunityDownloadSource> ordered = new ArrayList<>(sources.size());
        for (String url : ranking) {
            CommunityDownloadSource source = remaining.remove(url);
            if (source != null) {
                ordered.add(source);
            }
        }
        for (CommunityDownloadSource source : sources) {
            if (remaining.containsKey(source.url())) {
                ordered.add(source);
            }
        }
        return List.copyOf(ordered);
    }

    /// 接受用户手动“测速排序”得到的顺序。
    ///
    /// 与 [#refreshAsync(boolean)] 不同，这里不再发起网络探测，避免设置页刚测完
    /// 又重复测一轮；调用方随后用 [CommunityDownloadInstaller#applyRanked()] 应用。
    ///
    /// @param urls 用户测速后得到的地址顺序
    static void acceptRanking(@NotNull List<String> urls) {
        synchronized (LOCK) {
            rankedUrls = List.copyOf(urls);
            testedAt = System.currentTimeMillis();
        }
    }

    /// 标记用户刚进入了下载入口，下一次文件请求前要等一轮新测速。
    public static void markDownloadEntry() {
        downloadEntryPending = true;
    }

    /// 文件下载真正开始前调用；必要时等待测速排序完成。
    ///
    /// 普通下载在 IO 线程调用，最多阻塞 [CommunityDownloadRanker#DOWNLOAD_WAIT_MILLIS]；
    /// 若在 JavaFX 线程调用（例如只构造待显示的 URL），只触发测速、不阻塞。
    public static void awaitBeforeDownload() {
        long now = System.currentTimeMillis();
        long previousUse = lastProviderUse;
        lastProviderUse = now;
        boolean newSession = previousUse <= 0L || now - previousUse >= DOWNLOAD_SESSION_IDLE_MILLIS;

        if (Platform.isFxApplicationThread()) {
            // FX 线程只触发测速，不阻塞；下载入口标记由首个 IO 请求消费。
            refreshAsync(false);
            return;
        }

        boolean pending = downloadEntryPending;
        if (pending) {
            downloadEntryPending = false;
        }
        boolean stale = isStale(now);
        boolean wait = pending || newSession || stale || running;
        if (!wait) {
            return;
        }

        long deadline = now + DOWNLOAD_WAIT_MILLIS;
        boolean force = pending || newSession || stale;
        while (true) {
            CompletableFuture<Void> refresh = refreshAsync(force);
            force = false;
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                LOG.debug("Timed out waiting for community download source ranking");
                return;
            }
            try {
                refresh.get(remaining, TimeUnit.MILLISECONDS);
            } catch (Throwable e) {
                LOG.debug("Failed to wait for community download source ranking", e);
                return;
            }
            if (!running) {
                return;
            }
            // 本轮完成时又收到了强制刷新请求；继续等新一轮。
        }
    }

    /// 异步刷新测速结果。
    ///
    /// @param force `true` 时忽略缓存时间；`false` 时在 TTL 内直接返回
    /// @return 本轮测速完成信号；已有任务在跑时返回当前任务的信号
    @NotNull
    public static CompletableFuture<Void> refreshAsync(boolean force) {
        List<CommunityDownloadSources> holder = new ArrayList<>(1);
        CompletableFuture<Void> result;
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (running) {
                if (force) {
                    pendingRefresh = true;
                }
                return activeRefresh;
            }
            if (!force && testedAt > 0 && now - testedAt < TTL_MILLIS) {
                return activeRefresh;
            }
            CommunityDownloadSources sources = CommunityDownloadSources.load();
            if (sources.isEmpty()) {
                rankedUrls = List.of();
                testedAt = now;
                activeRefresh = CompletableFuture.completedFuture(null);
                return activeRefresh;
            }
            running = true;
            result = new CompletableFuture<>();
            activeRefresh = result;
            holder.add(sources);
        }

        CommunityDownloadSources sources = holder.get(0);
        LOG.info("Testing " + sources.size() + " community download source(s)");
        List<CompletableFuture<DownloadSourceTester.Result>> futures = sources.sources().stream()
                .map(source -> DownloadSourceTester.probe(source.url()))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> {
                    List<String> ranking;
                    try {
                        List<Probe> probes = new ArrayList<>(sources.size());
                        for (int i = 0; i < sources.size(); i++) {
                            CommunityDownloadSource source = sources.sources().get(i);
                            DownloadSourceTester.Result probeResult;
                            try {
                                probeResult = futures.get(i).join();
                            } catch (Throwable e) {
                                probeResult = DownloadSourceTester.Result.failure();
                            }
                            probes.add(new Probe(source.url(), probeResult.available(),
                                    probeResult.latencyMillis(), i));
                        }
                        ranking = rank(probes);
                    } catch (Throwable e) {
                        LOG.warning("Failed to rank community download sources", e);
                        ranking = List.of();
                    }

                    boolean rerun;
                    synchronized (LOCK) {
                        if (!ranking.isEmpty()) {
                            rankedUrls = List.copyOf(ranking);
                        }
                        testedAt = System.currentTimeMillis();
                        running = false;
                        rerun = pendingRefresh;
                        pendingRefresh = false;
                    }
                    if (error != null) {
                        LOG.warning("Community download source speed test failed", error);
                    }

                    persistRanking(sources, ranking);
                    CommunityDownloadInstaller.applyRanked();

                    if (rerun) {
                        // 先启动下一轮，再唤醒等待者，避免等待者看到 running=false 提前返回。
                        refreshAsync(true);
                    }
                    result.complete(null);
                });
        return result;
    }

    /// 是否超过缓存时间。
    private static boolean isStale(long now) {
        long tested = testedAt;
        return tested <= 0L || now - tested >= TTL_MILLIS;
    }

    /// 测速完成后把排序写回配置；用户正在编辑设置页时只应用内存顺序。
    private static void persistRanking(@NotNull CommunityDownloadSources sources,
                                       @NotNull List<String> ranking) {
        if (ranking.isEmpty() || CommunityDownloadSettingsPane.isInstanceVisible()) {
            return;
        }
        try {
            List<String> configured = sources.sources().stream()
                    .map(CommunityDownloadSource::url)
                    .toList();
            if (configured.size() != ranking.size() || !configured.containsAll(ranking)) {
                // 测速期间配置被改过，不写入旧结果。
                return;
            }
            List<CommunityDownloadSource> ordered = order(sources.sources());
            boolean changed = false;
            for (int i = 0; i < ordered.size(); i++) {
                if (!ordered.get(i).url().equals(configured.get(i))) {
                    changed = true;
                    break;
                }
            }
            if (!changed) {
                return;
            }
            new CommunityDownloadSources(ordered).save();
            LOG.info("Saved ranked community download source order");
        } catch (Throwable e) {
            LOG.warning("Failed to save ranked community download sources", e);
        }
    }

    /// 根据探测结果排序：成功按延迟升序，失败按原顺序排在最后。
    ///
    /// @param probes 探测结果
    /// @return URL 顺序
    @NotNull
    static List<String> rank(@NotNull List<Probe> probes) {
        List<Probe> sorted = new ArrayList<>(probes);
        sorted.sort(Comparator
                .comparing(Probe::available).reversed()
                .thenComparingLong(probe -> probe.available() ? probe.latencyMillis() : Long.MAX_VALUE)
                .thenComparingInt(Probe::originalIndex));
        return sorted.stream().map(Probe::url).toList();
    }

    /// 清空测速结果（仅供测试）。
    static void resetForTesting() {
        synchronized (LOCK) {
            rankedUrls = List.of();
            testedAt = 0L;
            running = false;
            pendingRefresh = false;
            activeRefresh = CompletableFuture.completedFuture(null);
            downloadEntryPending = false;
            lastProviderUse = 0L;
        }
    }

    /// 注入测速结果（仅供测试）。
    ///
    /// @param urls 排好序的地址
    static void setRankedUrlsForTesting(@NotNull List<String> urls) {
        synchronized (LOCK) {
            rankedUrls = List.copyOf(urls);
            testedAt = System.currentTimeMillis();
        }
    }

    /// 单个源的探测结果。
    ///
    /// @param url 源地址
    /// @param available 是否可用
    /// @param latencyMillis 延迟（毫秒）；失败为 `-1`
    /// @param originalIndex 配置中的原始下标
    record Probe(@NotNull String url, boolean available, long latencyMillis, int originalIndex) {
    }
}
