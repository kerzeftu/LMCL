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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// 组件工厂；实现类在 `META-INF/services` 登记。框架对 provider 的异常做隔离。
///
/// 约定：
/// - [WidgetProvider#descriptors()] 在 [WidgetRegistry#load()] 时调用一次，必须可在 FX 线程外的
///   任何线程调用，禁止 I/O；
/// - [WidgetProvider#create(DashboardWidgetDescriptor, DashboardContext)] 只在需要渲染时调用，
///   线程为 FX 线程，同样禁止阻塞 I/O（数据加载放组件的 `loadAsync`）。
@NotNullByDefault
public interface WidgetProvider {

    /// 返回本 provider 提供的全部组件描述。
    ///
    /// @return 不可变列表，不允许 `null` 元素
    @NotNull
    @Unmodifiable
    List<DashboardWidgetDescriptor> descriptors();

    /// 按描述创建组件实例。
    ///
    /// @param descriptor 由 [WidgetProvider#descriptors()] 返回的描述
    /// @param context 组件上下文
    /// @return 组件实例
    @NotNull
    DashboardWidget create(@NotNull DashboardWidgetDescriptor descriptor, @NotNull DashboardContext context);
}
