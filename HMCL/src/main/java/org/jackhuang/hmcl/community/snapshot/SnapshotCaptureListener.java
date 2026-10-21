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
package org.jackhuang.hmcl.community.snapshot;

import org.jackhuang.hmcl.community.launch.LaunchEvent;
import org.jackhuang.hmcl.community.launch.LaunchEventListener;
import org.jackhuang.hmcl.community.launch.LaunchEventKind;
import org.jackhuang.hmcl.community.launch.LaunchTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

/// 成功启动退出后自动采集快照。
///
/// 在事件线程上只做作用域解析（仓库缓存），实际磁盘采集提交到 IO 线程，不阻塞退出。
@NotNullByDefault
public final class SnapshotCaptureListener implements LaunchEventListener {

    @Override
    public void onLaunchEvent(@NotNull LaunchEvent event) {
        if (event.kind() != LaunchEventKind.EXITED_NORMAL
                && event.kind() != LaunchEventKind.EXITED_ABNORMAL) {
            return;
        }
        if (event.alive() == null
                || event.alive().toMillis() < LaunchTracker.loadDefaultThreshold() * 1000L) {
            return;
        }
        SnapshotScope scope = SnapshotService.scopeOf(event.versionId());
        if (scope == null) {
            return;
        }
        SnapshotService.capture(scope, SnapshotReason.AUTO_START, event.timestamp());
    }
}
