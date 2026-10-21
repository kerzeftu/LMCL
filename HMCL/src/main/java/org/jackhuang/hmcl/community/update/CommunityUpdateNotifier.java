/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.community.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.community.CommunityMetadata;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/// LMCL 的"检查更新"服务：只提示、**永不下载或替换任何文件**。
///
/// 这是对上游自更新链路的替代：上游 `UpdateChecker` 因产物未签名，
/// 在联网前就会抛出 `Self verification failed`（见步骤 05 §2），
/// 所以 LMCL 的更新提示必须由本类独立完成。
///
/// 仓库尚未创建时 [CommunityMetadata#GITHUB_REPO] / [#GITEE_REPO] 为空，
/// [#isConfigured] 返回 `false`，[#checkAsync] 立即返回空，UI 不显示任何入口。
@NotNullByDefault
public final class CommunityUpdateNotifier {
    private CommunityUpdateNotifier() {
    }

    /// 一次可展示的更新信息。
    ///
    /// @param version    新版本号（已去掉 `v` 前缀），例如 `3.16.1001`
    /// @param releaseUrl 发布页 URL；仓库未创建时回退到官网下载页
    /// @param notes      发布说明（纯文本，已截断，可能为空串）
    /// @param source     来源：`github` / `gitee`
    public record UpdateInfo(String version, String releaseUrl, String notes, String source) {
    }

    /// 是否已配置可查询的仓库。
    public static boolean isConfigured() {
        return !CommunityMetadata.GITHUB_REPO.isBlank() || !CommunityMetadata.GITEE_REPO.isBlank();
    }

    /// 更新提示按钮应打开的地址：优先 GitHub，其次 Gitee，最后官网下载页。
    public static String repositoryUrl() {
        if (!CommunityMetadata.GITHUB_REPO.isBlank()) {
            return "https://github.com/" + CommunityMetadata.GITHUB_REPO;
        }
        if (!CommunityMetadata.GITEE_REPO.isBlank()) {
            return "https://gitee.com/" + CommunityMetadata.GITEE_REPO;
        }
        return Metadata.DOWNLOAD_URL;
    }

    /// 异步检查最新发布版；无仓库配置时直接返回空。
    ///
    /// 本方法不抛异常：网络错误、限流、JSON 异常一律记日志并返回空，
    /// 由调用方决定是否提示（启动时检查失败必须静默）。
    public static CompletableFuture<Optional<UpdateInfo>> checkAsync() {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.supplyAsync(CommunityUpdateNotifier::checkNow, Schedulers.io());
    }

    private static Optional<UpdateInfo> checkNow() {
        if (!CommunityMetadata.GITHUB_REPO.isBlank()) {
            Optional<UpdateInfo> info = fetchGithub(CommunityMetadata.GITHUB_REPO);
            if (info.isPresent()) {
                return info;
            }
        }
        if (!CommunityMetadata.GITEE_REPO.isBlank()) {
            return fetchGitee(CommunityMetadata.GITEE_REPO);
        }
        return Optional.empty();
    }

    private static Optional<UpdateInfo> fetchGithub(String repo) {
        String url = "https://api.github.com/repos/" + repo + "/releases/latest";
        return fetchJson(url, "github").flatMap(json -> {
            String tag = optString(json, "tag_name");
            if (tag == null) {
                return Optional.empty();
            }
            String htmlUrl = optString(json, "html_url");
            String body = optString(json, "body");
            return toInfo(tag, htmlUrl == null ? repositoryUrl() : htmlUrl, body, "github");
        });
    }

    private static Optional<UpdateInfo> fetchGitee(String repo) {
        String url = "https://gitee.com/api/v5/repos/" + repo + "/releases/latest";
        return fetchJson(url, "gitee").flatMap(json -> {
            String tag = optString(json, "tag_name");
            if (tag == null) {
                return Optional.empty();
            }
            String page = "https://gitee.com/" + repo + "/releases/tag/" + tag;
            String body = optString(json, "body");
            return toInfo(tag, page, body, "gitee");
        });
    }

    private static Optional<UpdateInfo> toInfo(String tag, String releaseUrl, String body, String source) {
        String version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
        if (VersionNumber.compare(Metadata.VERSION, version) >= 0) {
            return Optional.empty();
        }
        String notes = body == null ? "" : body.strip();
        if (notes.length() > 800) {
            notes = notes.substring(0, 800) + "…";
        }
        return Optional.of(new UpdateInfo(version, releaseUrl, notes, source));
    }

    private static Optional<JsonObject> fetchJson(String url, String source) {
        HttpURLConnection connection = null;
        try {
            connection = NetworkUtils.createHttpConnection(url);
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(8_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "LMCL/" + Metadata.VERSION);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return Optional.empty();
            }
            try (InputStream in = connection.getInputStream()) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                return Optional.of(json);
            }
        } catch (IOException | RuntimeException e) {
            // 启动路径上的更新检查必须"静默失败"：离线、限流、代理拦截都不该打扰用户。
            org.jackhuang.hmcl.util.logging.Logger.LOG.warning("Failed to check update from " + source, e);
            return Optional.empty();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String optString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        try {
            String value = json.get(key).getAsString();
            return value.isBlank() ? null : value;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
