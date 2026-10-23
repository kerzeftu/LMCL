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
package org.jackhuang.hmcl.community.quote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/// 每日一言联网请求的共享 HTTP 客户端。
///
/// 只用 JDK [HttpClient]，不引入第三方依赖；连接 3 秒、请求 5 秒，
/// 响应正文超过 64 KiB 视为失败，避免异常大包。非 2xx 抛出
/// [HttpException] 并保留状态码，供今日诗词 token 失效时重试。
@NotNullByDefault
final class QuoteHttp {

    /// 响应正文上限。
    private static final int MAX_BODY_BYTES = 64 * 1024;
    /// 启动器 User-Agent。
    private static final String USER_AGENT = "LMCL/1.0 (daily quote)";
    /// 连接超时。
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    /// 单次请求超时。
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private QuoteHttp() {
    }

    /// HTTP 非 2xx 响应。
    static final class HttpException extends IOException {
        private final int status;

        HttpException(int status) {
            super("HTTP " + status);
            this.status = status;
        }

        /// 状态码。
        ///
        /// @return HTTP 状态码
        int status() {
            return status;
        }
    }

    /// 发起 GET 请求。
    ///
    /// @param url 完整 http/https URL
    /// @param headers 额外请求头
    /// @return 响应正文
    /// @throws IOException 网络错误、非 2xx 或正文过大
    /// @throws InterruptedException 线程被中断
    static @NotNull String get(@NotNull String url, @NotNull Map<String, String> headers)
            throws IOException, InterruptedException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (RuntimeException e) {
            throw new IOException("invalid URL: " + url, e);
        }
        if (uri.getScheme() == null
                || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IOException("invalid URL: " + url);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain;q=0.9, */*;q=0.1")
                .GET();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }

        HttpResponse<String> response = CLIENT.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HttpException(response.statusCode());
        }
        byte[] body = response.body() == null
                ? new byte[0]
                : response.body().getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_BODY_BYTES) {
            throw new IOException("response body too large: " + body.length + " bytes");
        }
        return response.body() == null ? "" : response.body();
    }
}
