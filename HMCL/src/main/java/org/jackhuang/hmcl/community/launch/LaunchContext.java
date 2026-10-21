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

import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// 启动任务可用的上下文快照。
///
/// 由 `LauncherHelper` 在 Java/依赖检查完成后构造，因此 [LaunchContext#java()] 在
/// 真实启动时一定非空；离线调用（如步骤 15 的复检）允许为空，相关检查会降级为 SKIPPED。
///
/// @param repository 实例仓库
/// @param version 已解析（含继承合并）的版本对象
/// @param versionId 实例 id（用户选中的版本 id）
/// @param java 本次启动使用的 Java；离线调用时可能为 `null`
/// @param setting 实例生效设置；离线调用时可能为 `null`
/// @param gameVersion 游戏版本号（如 `1.20.4`）；未知时 `null`
/// @param interactive 是否为交互式启动（生成启动脚本时为 `false`）
@NotNullByDefault
public record LaunchContext(
        @NotNull HMCLGameRepository repository,
        @NotNull Version version,
        @NotNull String versionId,
        @Nullable JavaRuntime java,
        @Nullable GameSettings.Effective setting,
        @Nullable String gameVersion,
        boolean interactive) {
}
