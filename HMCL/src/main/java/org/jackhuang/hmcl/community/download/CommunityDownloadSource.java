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

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;

/// 用户自定义的一个下载源。
///
/// 地址语义与上游 `BMCLAPIDownloadProvider` 相同：一个 BMCLAPI 兼容的 API 根，
/// 需要自行提供 `/mc`、`/assets`、`/libraries`、`/maven`、`/fabric-meta` 等路径。
/// 官方 Mojang 源由启动器内置，不在这里配置，且始终作为最后的回退。
///
/// @param name 显示名（不参与下载）
/// @param url 规范化后的 API 根（无尾斜杠）
@NotNullByDefault
public record CommunityDownloadSource(@NotNull String name, @NotNull String url) {

    /// 名称的最大长度（防止配置文件被塞入超长文本）。
    public static final int MAX_NAME_LENGTH = 64;

    /// 地址的最大长度（防止配置文件被塞入超长文本）。
    private static final int MAX_URL_LENGTH = 2048;

    /// 规范化用户输入的地址：去空白、补 `https://`、去尾部斜杠。
    ///
    /// @param raw 原始输入
    /// @return 规范化地址；空输入返回空串
    @NotNull
    public static String normalizeUrl(@NotNull String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (!value.contains("://")) {
            value = "https://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.length() > MAX_URL_LENGTH ? value.substring(0, MAX_URL_LENGTH) : value;
    }

    /// 判断规范化后的地址是否为合法的 http(s) 地址。
    ///
    /// @param url 规范化地址
    /// @return 是否为合法地址
    public static boolean isValidUrl(@NotNull String url) {
        if (url.isEmpty()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            String host = uri.getHost();
            return host != null && !host.isBlank();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /// 从地址推导默认显示名（主机名）。
    ///
    /// @param url 规范化地址
    /// @return 主机名；无法解析时返回地址本身
    @NotNull
    public static String defaultName(@NotNull String url) {
        try {
            String host = new URI(url).getHost();
            if (host != null && !host.isBlank()) {
                return host;
            }
        } catch (URISyntaxException ignored) {
        }
        return url;
    }

    /// 创建并规范化名称与地址。
    ///
    /// @param name 显示名，空串时使用主机名
    /// @param rawUrl 原始地址
    /// @return 下载源
    /// @throws IllegalArgumentException 地址非法
    @NotNull
    public static CommunityDownloadSource of(@NotNull String name, @NotNull String rawUrl) {
        String url = normalizeUrl(rawUrl);
        if (!isValidUrl(url)) {
            throw new IllegalArgumentException("Invalid download source URL: " + rawUrl);
        }
        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            normalizedName = defaultName(url);
        }
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            normalizedName = normalizedName.substring(0, MAX_NAME_LENGTH);
        }
        return new CommunityDownloadSource(normalizedName, url);
    }

    /// 从 JSON 读取下载源；字段缺失/地址非法时返回 `null`。
    ///
    /// @param object JSON 对象
    /// @return 下载源或 `null`
    @Nullable
    public static CommunityDownloadSource fromJson(@NotNull JsonObject object) {
        String url = normalizeUrl(JsonUtils.getString(object, "url", ""));
        if (!isValidUrl(url)) {
            return null;
        }
        String name = JsonUtils.getString(object, "name", "");
        return of(name, url);
    }

    /// 写成 JSON。
    ///
    /// @return JSON 对象
    @NotNull
    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("name", name);
        object.addProperty("url", url);
        return object;
    }
}
