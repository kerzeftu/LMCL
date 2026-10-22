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

import java.net.URI;
import java.util.Set;

/// Modrinth / CurseForge 插件资源的官方地址。
///
/// 游戏本体和加载器的下载源仍然完全由用户配置（零内置，见
/// [MissingDownloadProvider]）；但插件、整合包、资源包等“插件型资源”的 API
/// 与 CDN 地址属于协议端点，不能依赖 BMCLAPI 兼容镜像，必须在无自定义下载源时
/// 也能直接访问。这里集中维护这些端点，供 [MissingDownloadProvider] 放行。
///
/// 只放行下列官方主机，避免把“未配置游戏下载源”错误地放大成“允许任意网络请求”。
@NotNullByDefault
public final class CommunityAddonSources {

    /// Modrinth API 根。
    public static final String MODRINTH_API = "https://api.modrinth.com";

    /// Modrinth CDN 根。
    public static final String MODRINTH_CDN = "https://cdn.modrinth.com";

    /// CurseForge API 根。
    public static final String CURSEFORGE_API = "https://api.curseforge.com";

    /// CurseForge 文件 CDN 根。
    public static final String CURSEFORGE_CDN = "https://edge.forgecdn.net";

    /// 允许直接访问的官方主机。
    private static final Set<String> BUILTIN_HOSTS = Set.of(
            "api.modrinth.com",
            "cdn.modrinth.com",
            "api.curseforge.com",
            "edge.forgecdn.net");

    /// 本类只提供静态方法。
    private CommunityAddonSources() {
    }

    /// 判断地址是否属于内置插件资源端点。
    ///
    /// @param url 原始地址
    /// @return 是否为 Modrinth / CurseForge 的官方 HTTPS 地址
    public static boolean isBuiltinAddonUrl(@NotNull String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && BUILTIN_HOSTS.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
