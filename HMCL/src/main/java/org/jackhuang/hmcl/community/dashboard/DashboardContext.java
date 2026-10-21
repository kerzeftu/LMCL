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
package org.jackhuang.hmcl.community.dashboard;

import javafx.beans.value.ObservableValue;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/// 组件上下文：组件不得静态持有 `MainPage`/`Repository`，需要时从这里取。
///
/// @param repository 当前游戏仓库提供者（仓库未就绪时返回 `null`）
/// @param selectedInstanceId 当前选中实例 id 的可观察值（可为 `null`）
/// @param requestDisable 请求“本次隐藏”某个组件（由 [DashboardPane] 处理）
/// @param prefs 组件持久化偏好
@NotNullByDefault
public record DashboardContext(
        @NotNull Supplier<@Nullable HMCLGameRepository> repository,
        @NotNull ObservableValue<@Nullable String> selectedInstanceId,
        @NotNull Consumer<DashboardWidgetDescriptor> requestDisable,
        @NotNull DashboardPrefs prefs) {
}
