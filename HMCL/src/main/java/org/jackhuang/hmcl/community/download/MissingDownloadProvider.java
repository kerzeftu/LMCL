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

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.net.URI;
import java.util.List;

/// 未配置任何下载源时使用的 provider。
///
/// 所有可能触发网络的入口都明确失败，绝不回落到 Mojang/BMCLAPI 等内置源。
/// UI 应优先用 [CommunityDownloadGuard] 在进入下载流程前拦截，这个类只负责兜底。
@NotNullByDefault
public final class MissingDownloadProvider implements DownloadProvider {

    @Override
    public List<URI> getVersionListURLs() {
        return List.of();
    }

    @Override
    public List<URI> getAssetObjectCandidates(String assetObjectLocation) {
        return List.of();
    }

    @Override
    public String injectURL(String baseURL) {
        throw new CommunityDownloadSourceMissingException();
    }

    @Override
    public List<URI> injectURLWithCandidates(String baseURL) {
        throw new CommunityDownloadSourceMissingException();
    }

    @Override
    public VersionList<?> getVersionListById(String id) {
        return new MissingVersionList();
    }

    @Override
    public int getConcurrency() {
        return 1;
    }
}
