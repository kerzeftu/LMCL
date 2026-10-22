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

/// 在真正发起文件请求前等待社区下载源测速排序的 provider 包装。
///
/// 所有 URL 查询方法都会先调用
/// [CommunityDownloadRanker#awaitBeforeDownload]；下载任务运行在 IO 线程时最多
/// 等待 12 秒，确保首个文件请求使用最新测速顺序。`getConcurrency()` 不等待，
/// 避免线程池初始化阶段被网络探测阻塞。
///
/// 该包装只由 [CommunityDownloadInstaller] 在配置了至少一个下载源时安装。
@NotNullByDefault
final class RankingDownloadProvider implements DownloadProvider {

    /// 实际下载提供者。
    private final DownloadProvider delegate;

    /// 构造包装。
    ///
    /// @param delegate 实际下载提供者
    RankingDownloadProvider(@NotNull DownloadProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<URI> getVersionListURLs() {
        CommunityDownloadRanker.awaitBeforeDownload();
        return delegate.getVersionListURLs();
    }

    @Override
    public List<URI> getAssetObjectCandidates(String assetObjectLocation) {
        CommunityDownloadRanker.awaitBeforeDownload();
        return delegate.getAssetObjectCandidates(assetObjectLocation);
    }

    @Override
    public String injectURL(String baseURL) {
        CommunityDownloadRanker.awaitBeforeDownload();
        return delegate.injectURL(baseURL);
    }

    @Override
    public List<URI> injectURLWithCandidates(String baseURL) {
        CommunityDownloadRanker.awaitBeforeDownload();
        return delegate.injectURLWithCandidates(baseURL);
    }

    @Override
    public List<URI> injectURLsWithCandidates(List<String> urls) {
        CommunityDownloadRanker.awaitBeforeDownload();
        return delegate.injectURLsWithCandidates(urls);
    }

    @Override
    public VersionList<?> getVersionListById(String id) {
        CommunityDownloadRanker.awaitBeforeDownload();
        return delegate.getVersionListById(id);
    }

    @Override
    public int getConcurrency() {
        return delegate.getConcurrency();
    }
}
