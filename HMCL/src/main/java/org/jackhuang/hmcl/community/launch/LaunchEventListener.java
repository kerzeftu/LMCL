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
package org.jackhuang.hmcl.community.launch;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

/// 启动事件监听器。
///
/// 实现类可通过 `META-INF/services` 注册（由 [CommunityLaunchHooks] 在类初始化时发现），
/// 也可在运行时用 [CommunityLaunchHooks#addListener(LaunchEventListener)] 注册。
/// 单个监听器抛异常只记录日志，不影响其它监听器与启动流程。
@FunctionalInterface
@NotNullByDefault
public interface LaunchEventListener {

    /// 处理一个启动事件。
    ///
    /// @param event 事件
    void onLaunchEvent(@NotNull LaunchEvent event);
}
