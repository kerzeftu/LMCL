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

import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.NotNullByDefault;

/// 未配置下载源时返回的版本列表：刷新一定失败，且不发起任何网络请求。
///
/// 不能直接让 [org.jackhuang.hmcl.download.DownloadProvider#getVersionListURLs()]
/// 返回空列表：上游 `GetTask` 会对空列表取 `url.get(0)`，在构造阶段抛异常。
@NotNullByDefault
final class MissingVersionList extends VersionList<RemoteVersion> {

    @Override
    public boolean hasType() {
        return false;
    }

    @Override
    public Task<?> refreshAsync() {
        return Task.supplyAsync(() -> {
            throw new CommunityDownloadSourceMissingException();
        });
    }
}
