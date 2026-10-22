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

import org.jackhuang.hmcl.download.AutoDownloadProvider;
import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.List;

/// 根据用户配置构建下载候选链。
///
/// 候选顺序等于传入顺序（通常已由 [CommunityDownloadRanker] 按测速结果排序）；
/// **不追加任何官方/内置源**。空配置返回 [MissingDownloadProvider]，所有网络操作都会明确失败。
@NotNullByDefault
public final class CommunityDownloadProvider {

    private CommunityDownloadProvider() {
    }

    /// 按给定顺序构建 provider。
    ///
    /// @param sources 已排序的下载源
    /// @return 自动候选 provider；空列表时为 [MissingDownloadProvider]
    @NotNull
    public static DownloadProvider of(@NotNull List<CommunityDownloadSource> sources) {
        if (sources.isEmpty()) {
            return new MissingDownloadProvider();
        }
        List<DownloadProvider> providers = new ArrayList<>(sources.size());
        for (CommunityDownloadSource source : sources) {
            providers.add(new BMCLAPIDownloadProvider(source.url()));
        }
        return new AutoDownloadProvider(providers, List.copyOf(providers));
    }
}
