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

/// 下载源连通性测试：请求 `<url>/mc/game/version_manifest.json`，2xx 视为可用。
///
/// 只做只读探测，不下载任何文件、不写盘；超时 8 秒。
@NotNullByDefault
final class DownloadSourceTester {

    /// 连接/读取超时。
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    /// 版本清单路径（BMCLAPI 兼容源均提供）。
    private static final String VERSION_LIST_PATH = "/mc/game/version_manifest.json";

    /// 本类只提供静态方法。
    private DownloadSourceTester() {
    }

    /// 异步测试一个源。
    ///
    /// @param url 规范化后的 API 根
    /// @return 是否可用；失败/超时返回 `false`
    @NotNull
    static CompletableFuture<Boolean> test(@NotNull String url) {
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
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                return response.statusCode() >= 200 && response.statusCode() < 300;
            } catch (Throwable e) {
                return false;
            }
        }, Schedulers.io());
    }
}
