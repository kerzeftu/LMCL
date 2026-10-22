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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 下载源自动测速排序。
///
/// 进入下载入口、保存下载源或启动时触发一次异步探测：并行请求每个源的
/// `/mc/game/version_manifest.json`，按响应延迟从低到高生成候选顺序；失败的源
/// 排在最后。结果缓存 [CommunityDownloadRanker#TTL_MILLIS] 毫秒，过期后下次
/// 进入下载入口再测。排序完成后会通知 [CommunityDownloadInstaller] 用新顺序
/// 重建 provider，正在进行的下载不受影响。
///
/// 线程：所有公开方法可在任意线程调用；实际探测与 provider 重建在
/// `Schedulers.io()` 上执行，不阻塞 JavaFX 线程。
@NotNullByDefault
public final class CommunityDownloadRanker {

    /// 测速结果缓存时间（5 分钟）。
    static final long TTL_MILLIS = 5L * 60L * 1_000L;

    /// 测速锁。
    private static final Object LOCK = new Object();

    /// 最近一次测速得到的地址顺序（最快的在前）。
    private static volatile List<String> rankedUrls = List.of();

    /// 最近一次测速时间（`System.currentTimeMillis()`）。
    private static volatile long testedAt;

    /// 是否有测速任务正在运行。
    private static volatile boolean running;

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

    /// 异步刷新测速结果。
    ///
    /// @param force `true` 时忽略缓存时间；`false` 时在 TTL 内直接返回
    public static void refreshAsync(boolean force) {
        List<CommunityDownloadSources> holder = new ArrayList<>(1);
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (running) {
                return;
            }
            if (!force && testedAt > 0 && now - testedAt < TTL_MILLIS) {
                return;
            }
            CommunityDownloadSources sources = CommunityDownloadSources.load();
            if (sources.isEmpty()) {
                rankedUrls = List.of();
                testedAt = now;
                return;
            }
            running = true;
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
                            DownloadSourceTester.Result result;
                            try {
                                result = futures.get(i).join();
                            } catch (Throwable e) {
                                result = DownloadSourceTester.Result.failure();
                            }
                            probes.add(new Probe(source.url(), result.available(), result.latencyMillis(), i));
                        }
                        ranking = rank(probes);
                    } catch (Throwable e) {
                        LOG.warning("Failed to rank community download sources", e);
                        ranking = List.of();
                    }

                    synchronized (LOCK) {
                        if (!ranking.isEmpty()) {
                            rankedUrls = List.copyOf(ranking);
                        }
                        testedAt = System.currentTimeMillis();
                        running = false;
                    }
                    if (error != null) {
                        LOG.warning("Community download source speed test failed", error);
                    }
                    CommunityDownloadInstaller.applyRanked();
                });
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
