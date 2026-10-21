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

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 仪表盘布局引擎的纯函数测试（不依赖 JavaFX/磁盘）。
@NotNullByDefault
public final class DashboardLayoutTest {

    /// `autoColumns` 在窗口极窄时也至少给 1 列。
    @Test
    public void autoColumnsHandlesDegenerateWidths() {
        assertEquals(1, DashboardLayout.autoColumns(0));
        assertEquals(1, DashboardLayout.autoColumns(Double.NaN));
        assertEquals(1, DashboardLayout.autoColumns(179));
        assertEquals(1, DashboardLayout.autoColumns(699));
        assertEquals(2, DashboardLayout.autoColumns(700));
        assertEquals(3, DashboardLayout.autoColumns(1000));
        assertEquals(4, DashboardLayout.autoColumns(1400));
    }

    /// 隐藏项不参与布局，行优先首次适配得到预期坐标。
    @Test
    public void computeResultSkipsHiddenItemsAndPacksInOrder() {
        List<DashboardItem> items = List.of(
                new DashboardItem("c", true, 30, 1, 1),
                new DashboardItem("a", true, 10, 2, 1),
                new DashboardItem("hidden", false, 20, 2, 1),
                new DashboardItem("b", true, 40, 1, 2));

        DashboardLayout.LayoutResult result = DashboardLayout.computeResult(items, 2);

        assertEquals(3, result.placed().size());
        DashboardLayout.PlacedItem a = result.placed().get(0);
        assertEquals("a", a.item().id());
        assertEquals(0, a.row());
        assertEquals(0, a.column());

        DashboardLayout.PlacedItem c = result.placed().get(1);
        assertEquals("c", c.item().id());
        assertEquals(1, c.row(), "2 列被 a 占满后，c 应换到下一行");
        assertEquals(0, c.column());

        DashboardLayout.PlacedItem b = result.placed().get(2);
        assertEquals("b", b.item().id());
        assertEquals(1, b.column(), "b 应填在 c 右侧的空位");
        assertTrue(result.rejected().isEmpty());
        assertEquals(3, result.rows(), "b 是 1x2，跨到第三行");
    }

    /// 重复 id 与超过行上限的项进入 rejected，而不是抛异常。
    @Test
    public void computeResultRejectsDuplicatesAndOverflow() {
        List<DashboardItem> items = List.of(
                new DashboardItem("a", true, 0, 1, 1),
                new DashboardItem("a", true, 1, 1, 1),
                new DashboardItem("Bad Id", true, 2, 1, 1));
        DashboardLayout.LayoutResult result = DashboardLayout.computeResult(items, 1);
        assertEquals(1, result.placed().size());
        assertEquals(2, result.rejected().size());
        assertTrue(result.rejected().contains("Bad Id"));
    }
}
