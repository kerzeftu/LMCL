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
package org.jackhuang.hmcl.community.crash;

import org.jackhuang.hmcl.community.launch.CommunityLaunchHooks;
import org.jackhuang.hmcl.community.launch.CommunityLaunchHooks.LaunchSession;
import org.jackhuang.hmcl.community.launch.LaunchEvent;
import org.jackhuang.hmcl.community.launch.LaunchEventListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 崩溃归档事件监听器（步骤 09 §3.1）。
///
/// 只消费步骤 08 的事件，不改上游启动流程；所有回调立即返回，重活交给 [CrashArchiver] 的 IO 线程。
@NotNullByDefault
public final class CrashArchiveListener implements LaunchEventListener {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    /// 已存活过阈值的实例（用于区分启动期/运行期崩溃）。
    private final Set<String> survived = ConcurrentHashMap.newKeySet();

    /// 安装到启动钩子；重复调用无副作用。
    public static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            CommunityLaunchHooks.addListener(new CrashArchiveListener());
        }
    }

    @Override
    public void onLaunchEvent(@NotNull LaunchEvent event) {
        try {
            switch (event.kind()) {
                case PREVIOUS_ABNORMAL -> {
                    LaunchSession session = CommunityLaunchHooks.sessionOf(event.versionId());
                    if (session != null) {
                        CrashArchiver.archiveRecoveredAsync(session, event.versionId());
                    }
                }
                case SURVIVED_THRESHOLD -> survived.add(event.versionId());
                case PROCESS_CREATED -> survived.remove(event.versionId());
                case EXITED_ABNORMAL -> {
                    boolean runtime = survived.remove(event.versionId());
                    LaunchSession session = CommunityLaunchHooks.sessionOf(event.versionId());
                    if (session != null) {
                        CrashArchiver.archiveAsync(session, event, runtime);
                    } else {
                        LOG.warning("No launch session for crash archive: " + event.versionId());
                    }
                }
                case EXITED_NORMAL, ABORTED_BY_USER -> survived.remove(event.versionId());
                default -> {
                }
            }
        } catch (Throwable e) {
            LOG.warning("Community crash archive listener failed", e);
        }
    }
}
