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
package org.jackhuang.hmcl.community.ui.m3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// [GlassEdgeFade] 中滚动距离与量化规则的 headless 测试。
///
/// 只调用不依赖场景图的静态纯函数，不创建 `Scene`/`Stage`、不启动 Toolkit，
/// 因此可以在无显示环境下运行。
public final class GlassEdgeFadeTest {

    /// 浮点比较容差。
    private static final double EPSILON = 1e-9;

    /// 过渡带高度与量化步长符合 72 px / 1 px 的规格。
    @Test
    public void constantsMatchSpecification() {
        assertEquals(72.0, GlassEdgeFade.FADE_HEIGHT, EPSILON);
        assertEquals(1.0, GlassEdgeFade.DISTANCE_STEP, EPSILON);
        assertEquals(2, GlassEdgeFade.Edge.values().length);
        assertEquals(GlassEdgeFade.Edge.TOP, GlassEdgeFade.Edge.valueOf("TOP"));
        assertEquals(GlassEdgeFade.Edge.BOTTOM, GlassEdgeFade.Edge.valueOf("BOTTOM"));
    }

    /// 顶部过渡带在滚动起始位置不可见。
    @Test
    public void scrollDistanceIsZeroAtStart() {
        assertEquals(0.0, GlassEdgeFade.scrollDistance(0.0, 1000.0, 300.0), EPSILON);
    }

    /// 滚到底部时上边缘显示全部可滚动距离。
    @Test
    public void scrollDistanceReachesScrollableHeightAtEnd() {
        assertEquals(700.0, GlassEdgeFade.scrollDistance(1.0, 1000.0, 300.0), EPSILON);
        assertEquals(350.0, GlassEdgeFade.scrollDistance(0.5, 1000.0, 300.0), EPSILON);
    }

    /// `vvalue` 越界时按 `[0, 1]` 裁剪。
    @Test
    public void scrollDistanceClampsOutOfRangeValues() {
        assertEquals(0.0, GlassEdgeFade.scrollDistance(-0.5, 1000.0, 300.0), EPSILON);
        assertEquals(700.0, GlassEdgeFade.scrollDistance(1.5, 1000.0, 300.0), EPSILON);
        assertEquals(0.0, GlassEdgeFade.scrollDistance(Double.NaN, 1000.0, 300.0), EPSILON);
    }

    /// 内容不足视口时没有可滚动距离，上下过渡带都不出现。
    @Test
    public void scrollDistanceIsZeroWhenContentFitsViewport() {
        assertEquals(0.0, GlassEdgeFade.scrollDistance(1.0, 200.0, 300.0), EPSILON);
        assertEquals(0.0, GlassEdgeFade.scrollDistance(0.7, 300.0, 300.0), EPSILON);
    }

    /// 内容高度或视口高度为 0/负数等退化输入不会产生负距离。
    @Test
    public void scrollDistanceHandlesDegenerateSizes() {
        assertEquals(0.0, GlassEdgeFade.scrollDistance(1.0, 0.0, 300.0), EPSILON);
        assertEquals(0.0, GlassEdgeFade.scrollDistance(1.0, 100.0, 300.0), EPSILON);
        assertEquals(100.0, GlassEdgeFade.scrollDistance(1.0, 100.0, 0.0), EPSILON);
        assertEquals(100.0, GlassEdgeFade.scrollDistance(1.0, 100.0, -20.0), EPSILON);
        assertEquals(0.0, GlassEdgeFade.scrollDistance(1.0, Double.POSITIVE_INFINITY, 300.0), EPSILON);
    }

    /// 滚动距离按 1 px 向上量化：0 隐藏，任意正距离都显示对应的行数。
    @Test
    public void quantizeRowsRoundsUpToStep() {
        assertEquals(0, GlassEdgeFade.quantizeRows(72, 0.0));
        assertEquals(1, GlassEdgeFade.quantizeRows(72, 0.2));
        assertEquals(1, GlassEdgeFade.quantizeRows(72, 1.0));
        assertEquals(3, GlassEdgeFade.quantizeRows(72, 3.0));
        assertEquals(4, GlassEdgeFade.quantizeRows(72, 4.0));
        assertEquals(5, GlassEdgeFade.quantizeRows(72, 4.1));
        assertEquals(5, GlassEdgeFade.quantizeRows(72, 5.0));
        assertEquals(8, GlassEdgeFade.quantizeRows(72, 8.0));
    }

    /// 行数不会超过过渡带高度。
    @Test
    public void quantizeRowsClampsToBandHeight() {
        assertEquals(72, GlassEdgeFade.quantizeRows(72, 72.0));
        assertEquals(72, GlassEdgeFade.quantizeRows(72, 100.0));
        assertEquals(72, GlassEdgeFade.quantizeRows(72, Double.MAX_VALUE));
    }

    /// 过渡带高度较小时行数也按 1 px 向上量化，且不会超过带高。
    @Test
    public void quantizeRowsRespectsSmallBands() {
        assertEquals(1, GlassEdgeFade.quantizeRows(3, 1.0));
        assertEquals(3, GlassEdgeFade.quantizeRows(3, 4.0));
        assertEquals(1, GlassEdgeFade.quantizeRows(2, 0.5));
        assertEquals(0, GlassEdgeFade.quantizeRows(0, 5.0));
        assertEquals(0, GlassEdgeFade.quantizeRows(-4, 5.0));
    }

    /// 距离为 0、负数或非有限值时过渡带完全隐藏。
    @Test
    public void quantizeRowsHidesNonPositiveDistances() {
        assertEquals(0, GlassEdgeFade.quantizeRows(72, 0.0));
        assertEquals(0, GlassEdgeFade.quantizeRows(72, -1.0));
        assertEquals(0, GlassEdgeFade.quantizeRows(72, Double.NaN));
        assertEquals(0, GlassEdgeFade.quantizeRows(72, Double.POSITIVE_INFINITY));
    }
}
