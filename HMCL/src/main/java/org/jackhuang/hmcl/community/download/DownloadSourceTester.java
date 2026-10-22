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

import org.jackhuang.hmcl.task.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/// 下载源探测：请求 `<url>/mc/game/version_manifest.json`。
///
/// 只做只读探测，不下载任何文件、不写盘；连接/请求超时 5 秒。
/// 成功时记录从发起请求到收到响应头的毫秒数，供 [CommunityDownloadRanker] 排序。
@NotNullByDefault
final class DownloadSourceTester {

    /// 连接/读取超时。
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /// 版本清单路径（BMCLAPI 兼容源均提供）。
    private static final String VERSION_LIST_PATH = "/mc/game/version_manifest.json";

    /// 本类只提供静态方法。
    private DownloadSourceTester() {
    }

    /// 一次探测结果。
    ///
    /// @param available 是否返回 2xx
    /// @param latencyMillis 请求耗时（毫秒）；失败时为 `-1`
    record Result(boolean available, long latencyMillis) {

        /// 创建“不可用”结果。
        static Result failure() {
            return new Result(false, -1);
        }
    }

    /// 异步探测一个源。
    ///
    /// @param url 规范化后的 API 根
    /// @return 探测结果；失败/超时返回不可用
    @NotNull
    static CompletableFuture<Result> probe(@NotNull String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(url + VERSION_LIST_PATH))
                        .timeout(TIMEOUT)
                        .header("User-Agent", "LMCL-DownloadSourceTester")
                        .GET()
                        .build();
                long startedAt = System.nanoTime();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                long elapsedMillis = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
                boolean available = response.statusCode() >= 200 && response.statusCode() < 300;
                return available ? new Result(true, elapsedMillis) : Result.failure();
            } catch (Throwable e) {
                return Result.failure();
            }
        }, Schedulers.io());
    }
}
