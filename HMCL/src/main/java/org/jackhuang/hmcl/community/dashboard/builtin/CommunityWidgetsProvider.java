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
package org.jackhuang.hmcl.community.dashboard.builtin;

import org.jackhuang.hmcl.community.dashboard.DashboardContext;
import org.jackhuang.hmcl.community.dashboard.DashboardWidget;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.dashboard.WidgetProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// 内置十张卡片的组件工厂（步骤 14 的 P0 六卡去掉内存卡 + 步骤 15 的 P1/P2 四卡 + 步骤 16 的快照卡）。
///
/// 通过 `META-INF/services/org.jackhuang.hmcl.community.dashboard.WidgetProvider` 注册。
/// 描述顺序与默认布局一致：启动、置顶实例、崩溃历史、Java、网络、快照、最近存档、存储、健康检查、游玩日历。
@NotNullByDefault
public final class CommunityWidgetsProvider implements WidgetProvider {

    /// 快照卡 id。
    public static final String ID_SNAPSHOT = "snapshot";

    /// 启动卡 id。
    public static final String ID_LAUNCH = "launch";

    /// 置顶实例卡 id。
    public static final String ID_PINNED = "pinned-instances";

    /// 崩溃历史卡 id。
    public static final String ID_CRASH = "crash-history";

    /// Java 卡 id。
    public static final String ID_JAVA = "java-recommend";

    /// 网络卡 id。
    public static final String ID_NETWORK = "network";

    /// 最近存档卡 id。
    public static final String ID_RECENT_WORLDS = "recent-worlds";

    /// 存储卡 id。
    public static final String ID_STORAGE = "storage";

    /// 游玩日历卡 id。
    public static final String ID_ACTIVITY_CALENDAR = "activity-calendar";

    /// 健康检查卡 id。
    public static final String ID_HEALTH_CHECK = "health-check";

    /// 组件描述（不可变，按默认布局顺序）。
    private static final List<DashboardWidgetDescriptor> DESCRIPTORS = List.of(
            new DashboardWidgetDescriptor(ID_LAUNCH, "community.launch.title", null, 2, 1, 10),
            new DashboardWidgetDescriptor(ID_PINNED, "community.pinned.title", null, 2, 1, 20),
            new DashboardWidgetDescriptor(ID_CRASH, "community.crash.title", null, 1, 1, 40),
            new DashboardWidgetDescriptor(ID_SNAPSHOT, "community.snapshot.title", null, 2, 1, 45),
            new DashboardWidgetDescriptor(ID_JAVA, "community.java.title", null, 1, 1, 50),
            new DashboardWidgetDescriptor(ID_NETWORK, "community.network.title", null, 1, 1, 60),
            new DashboardWidgetDescriptor(ID_RECENT_WORLDS, "community.worlds.title", null, 2, 1, 70),
            new DashboardWidgetDescriptor(ID_STORAGE, "community.storage.title", null, 1, 1, 80),
            new DashboardWidgetDescriptor(ID_HEALTH_CHECK, "community.health.title", null, 1, 1, 90),
            new DashboardWidgetDescriptor(ID_ACTIVITY_CALENDAR, "community.calendar.title", null, 2, 1, 100));

    /// 构造 provider。
    public CommunityWidgetsProvider() {
    }

    /// 返回全部内置卡片描述。
    ///
    /// @return 不可变描述列表
    @Override
    @NotNull
    @Unmodifiable
    public List<DashboardWidgetDescriptor> descriptors() {
        return DESCRIPTORS;
    }

    /// 按 id 创建卡片。
    ///
    /// @param descriptor 组件描述
    /// @param context 组件上下文
    /// @return 卡片实例
    @Override
    @NotNull
    public DashboardWidget create(@NotNull DashboardWidgetDescriptor descriptor,
                                  @NotNull DashboardContext context) {
        return switch (descriptor.id()) {
            case ID_LAUNCH -> new LaunchWidget(descriptor, context);
            case ID_PINNED -> new PinnedInstancesWidget(descriptor, context);
            case ID_CRASH -> new CrashHistoryWidget(descriptor, context);
            case ID_SNAPSHOT -> new SnapshotWidget(descriptor, context);
            case ID_JAVA -> new JavaRecommendWidget(descriptor, context);
            case ID_NETWORK -> new NetworkWidget(descriptor, context);
            case ID_RECENT_WORLDS -> new RecentWorldsWidget(descriptor, context);
            case ID_STORAGE -> new StorageWidget(descriptor, context);
            case ID_ACTIVITY_CALENDAR -> new ActivityCalendarWidget(descriptor, context);
            case ID_HEALTH_CHECK -> new HealthCheckWidget(descriptor, context);
            default -> throw new IllegalArgumentException("Unknown dashboard widget id: " + descriptor.id());
        };
    }
}
