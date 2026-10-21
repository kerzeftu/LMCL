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

import org.jetbrains.annotations.NotNullByDefault;

/// 启动生命周期事件的类型。
///
/// 事件由 [CommunityLaunchHooks] 在 `LauncherHelper` 的固定锚点发出，
/// 消费方（09 崩溃归档、14 首页启动卡、15 游玩日历、16 快照）只读事件，
/// 不得在 `LauncherHelper` 里再插入自己的逻辑。
@NotNullByDefault
public enum LaunchEventKind {
    /// 收到启动请求（游戏进程尚未创建）。
    REQUESTED,

    /// 上一次启动异常退出（实例目录存在 `.abnormal` 标记）。
    PREVIOUS_ABNORMAL,

    /// 启动任务链（含社区启动任务）执行完成。
    TASKS_COMPLETED,

    /// 游戏进程已创建。
    PROCESS_CREATED,

    /// 进程存活达到成功阈值（默认 120 秒）。
    SURVIVED_THRESHOLD,

    /// 进程正常退出。
    EXITED_NORMAL,

    /// 进程异常退出（崩溃/被杀）。
    EXITED_ABNORMAL,

    /// 用户主动停止，不计入崩溃。
    ABORTED_BY_USER,

    /// 启动任务链失败（进程尚未创建）。
    LAUNCH_FAILED
}
