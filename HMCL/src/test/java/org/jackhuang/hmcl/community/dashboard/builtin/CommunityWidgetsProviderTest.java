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

import org.jackhuang.hmcl.community.dashboard.DashboardItem;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.dashboard.WidgetRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 内置组件注册表的契约测试。
public final class CommunityWidgetsProviderTest {

    @Test
    public void providerIsDiscoveredByServiceLoader() {
        WidgetRegistry registry = WidgetRegistry.load();
        for (String id : List.of("launch", "pinned-instances", "crash-history", "snapshot",
                "java-recommend", "network", "recent-worlds",
                "health-check", "activity-calendar")) {
            assertTrue(registry.isRegistered(id), () -> "missing widget: " + id);
        }
    }

    @Test
    public void defaultColumnsOnlyPutPlayInfoOnTheLeft() {
        assertEquals(DashboardItem.COLUMN_LEFT, HomePane.defaultColumn("activity-calendar"));
        assertEquals(DashboardItem.COLUMN_LEFT, HomePane.defaultColumn("recent-worlds"));
        for (String id : List.of("launch", "pinned-instances", "crash-history", "snapshot",
                "java-recommend", "network", "health-check")) {
            assertEquals(DashboardItem.COLUMN_RIGHT, HomePane.defaultColumn(id), id);
        }
    }

    @Test
    public void descriptorsAreOrderedAndValid() {
        List<DashboardWidgetDescriptor> descriptors = new CommunityWidgetsProvider().descriptors();
        assertEquals(9, descriptors.size());

        List<String> ids = descriptors.stream().map(DashboardWidgetDescriptor::id).toList();
        assertEquals(List.of("launch", "pinned-instances", "crash-history", "snapshot",
                "java-recommend", "network", "recent-worlds",
                "health-check", "activity-calendar"), ids);

        int previousOrder = -1;
        for (int i = 0; i < descriptors.size(); i++) {
            DashboardWidgetDescriptor descriptor = descriptors.get(i);
            assertTrue(descriptor.isValid(), () -> "invalid descriptor: " + descriptor);
            assertTrue(descriptor.order() > previousOrder);
            previousOrder = descriptor.order();
            assertEquals(1, descriptor.defaultRows());
            assertTrue(descriptor.nameKey().startsWith("community."));
            boolean wide = i == 0 || i == 1 || i == 3 || i == 6 || i == 8;
            assertEquals(wide ? 2 : 1, descriptor.defaultColumns());
        }
    }
}
