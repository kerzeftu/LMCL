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

import org.jackhuang.hmcl.launch.ProcessListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/// 一次启动生命周期中的事件。
///
/// 事件在触发线程上同步分发给监听器（可能是 JavaFX 线程，也可能是进程监听线程），
/// 监听器不得假设线程，需要碰 UI 时必须自行切回 JavaFX 线程。
///
/// @param kind 事件类型
/// @param versionId 实例 id
/// @param timestamp 事件发生时间（epoch millis）
/// @param exitCode 退出码；非退出事件为 `-1`
/// @param exitType 退出类型；非退出事件为 `null`
/// @param alive 进程创建到退出/存活阈值的时长；未知时为 `null`
/// @param gameVersion 游戏版本号；未知时为 `null`
@NotNullByDefault
public record LaunchEvent(
        @NotNull LaunchEventKind kind,
        @NotNull String versionId,
        long timestamp,
        int exitCode,
        @Nullable ProcessListener.ExitType exitType,
        @Nullable Duration alive,
        @Nullable String gameVersion) {

    /// 创建不携带退出信息的事件。
    ///
    /// @param kind 事件类型
    /// @param versionId 实例 id
    /// @return 事件
    @NotNull
    public static LaunchEvent of(@NotNull LaunchEventKind kind, @NotNull String versionId) {
        return new LaunchEvent(kind, versionId, System.currentTimeMillis(), -1, null, null, null);
    }

    /// 创建带存活时长的事件。
    ///
    /// @param kind 事件类型
    /// @param versionId 实例 id
    /// @param alive 存活时长
    /// @param gameVersion 游戏版本
    /// @return 事件
    @NotNull
    public static LaunchEvent alive(
            @NotNull LaunchEventKind kind,
            @NotNull String versionId,
            @NotNull Duration alive,
            @Nullable String gameVersion) {
        return new LaunchEvent(kind, versionId, System.currentTimeMillis(), -1, null, alive, gameVersion);
    }

    /// 创建退出事件。
    ///
    /// @param kind 事件类型
    /// @param versionId 实例 id
    /// @param exitCode 退出码
    /// @param exitType 退出类型
    /// @param alive 存活时长；进程未创建时为 `null`
    /// @param gameVersion 游戏版本
    /// @return 事件
    @NotNull
    public static LaunchEvent exit(
            @NotNull LaunchEventKind kind,
            @NotNull String versionId,
            int exitCode,
            @NotNull ProcessListener.ExitType exitType,
            @Nullable Duration alive,
            @Nullable String gameVersion) {
        return new LaunchEvent(kind, versionId, System.currentTimeMillis(), exitCode, exitType, alive, gameVersion);
    }
}
