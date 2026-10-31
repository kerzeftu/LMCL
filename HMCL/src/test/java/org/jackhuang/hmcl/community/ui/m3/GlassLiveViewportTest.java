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

import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/// [GlassLiveViewport] 的纯几何测试，不创建任何 JavaFX 场景或节点。
public final class GlassLiveViewportTest {

    @Test
    public void coverMappingCentersImageAndKeepsSceneRect() {
        GlassLiveViewport.Mapping mapping = map(
                100.0, 50.0, 1000.0, 800.0,
                100.0, 50.0, 1000.0, 800.0,
                2000.0, 1000.0, 0.0);

        assertNotNull(mapping);
        // cover scale = max(1000/2000, 800/1000) = 0.8，图片横向居中后
        // 卡片左边缘对应图片像素 (100-100+300)/0.8 = 375。
        assertRectangle(mapping.imageViewport(), 375.0, 0.0, 1250.0, 1000.0);
        assertEquals(100.0, mapping.sceneMinX(), 0.001);
        assertEquals(50.0, mapping.sceneMinY(), 0.001);
        assertEquals(1000.0, mapping.sceneWidth(), 0.001);
        assertEquals(800.0, mapping.sceneHeight(), 0.001);
    }

    @Test
    public void mappingClipsAtBackgroundNodeEdge() {
        GlassLiveViewport.Mapping mapping = map(
                900.0, 900.0, 300.0, 300.0,
                0.0, 0.0, 1000.0, 1000.0,
                1000.0, 1000.0, 0.0);

        assertNotNull(mapping);
        assertRectangle(mapping.imageViewport(), 900.0, 900.0, 100.0, 100.0);
        assertRectangle(mapping.sceneMinX(), mapping.sceneMinY(),
                mapping.sceneWidth(), mapping.sceneHeight(), 900.0, 900.0, 100.0, 100.0);
    }

    @Test
    public void marginExpandsSampleRectAroundCard() {
        GlassLiveViewport.Mapping mapping = map(
                400.0, 400.0, 200.0, 100.0,
                0.0, 0.0, 1000.0, 1000.0,
                1000.0, 1000.0, 146.0);

        assertNotNull(mapping);
        assertRectangle(mapping.imageViewport(), 254.0, 254.0, 492.0, 392.0);
        assertEquals(254.0, mapping.sceneMinX(), 0.001);
        assertEquals(254.0, mapping.sceneMinY(), 0.001);
        assertEquals(492.0, mapping.sceneWidth(), 0.001);
        assertEquals(392.0, mapping.sceneHeight(), 0.001);
    }

    @Test
    public void expansionIsClampedToBackgroundNode() {
        GlassLiveViewport.Mapping mapping = map(
                10.0, 10.0, 100.0, 100.0,
                0.0, 0.0, 1000.0, 1000.0,
                1000.0, 1000.0, 146.0);

        assertNotNull(mapping);
        assertRectangle(mapping.imageViewport(), 0.0, 0.0, 256.0, 256.0);
    }

    @Test
    public void mappingRejectsCardsFullyOutsideBackgroundNode() {
        assertNull(map(-500.0, -500.0, 100.0, 100.0,
                0.0, 0.0, 1000.0, 1000.0, 1000.0, 1000.0, 0.0));
        assertNull(map(1500.0, 300.0, 100.0, 100.0,
                0.0, 0.0, 1000.0, 1000.0, 1000.0, 1000.0, 0.0));
        assertNull(map(1001.0, 0.0, 100.0, 100.0,
                0.0, 0.0, 1000.0, 1000.0, 1000.0, 1000.0, 0.0));
    }

    @Test
    public void mappingRejectsInvalidGeometry() {
        assertNull(map(Double.NaN, 0.0, 100.0, 100.0,
                0.0, 0.0, 1000.0, 1000.0, 1000.0, 1000.0, 0.0));
        assertNull(map(0.0, 0.0, 0.0, 100.0,
                0.0, 0.0, 1000.0, 1000.0, 1000.0, 1000.0, 0.0));
        assertNull(map(0.0, 0.0, 100.0, 100.0,
                0.0, 0.0, 0.0, 1000.0, 1000.0, 1000.0, 0.0));
        assertNull(map(0.0, 0.0, 100.0, 100.0,
                0.0, 0.0, 1000.0, 1000.0, 0.0, 1000.0, 0.0));
        assertNull(map(0.0, 0.0, 100.0, 100.0,
                0.0, 0.0, 1000.0, 1000.0, 1000.0, 1000.0, -1.0));
        // 亚像素卡片视为不可见，避免生成 0 尺寸视口。
        assertNull(map(0.0, 0.0, 0.5, 100.0,
                0.0, 0.0, 1000.0, 1000.0, 1000.0, 1000.0, 0.0));
    }

    @Test
    public void coverMappingUsesWiderNodeScale() {
        GlassLiveViewport.Mapping mapping = map(
                1080.0, 0.0, 120.0, 60.0,
                0.0, 0.0, 1200.0, 600.0,
                2000.0, 1000.0, 0.0);

        assertNotNull(mapping);
        assertRectangle(mapping.imageViewport(), 1800.0, 0.0, 200.0, 100.0);
    }

    @Test
    public void sampleMarginCoversBlurSpread() {
        assertEquals(146.0, GlassLiveViewport.sampleMargin(96.0), 0.001);
        assertEquals(98.0, GlassLiveViewport.sampleMargin(64.0), 0.001);
        assertEquals(62.0, GlassLiveViewport.sampleMargin(40.0), 0.001);
        assertEquals(38.0, GlassLiveViewport.sampleMargin(24.0), 0.001);
        assertEquals(20.0, GlassLiveViewport.sampleMargin(12.0), 0.001);
        assertEquals(2.0, GlassLiveViewport.sampleMargin(0.0), 0.001);
        assertEquals(2.0, GlassLiveViewport.sampleMargin(-10.0), 0.001);
        assertEquals(2.0, GlassLiveViewport.sampleMargin(Double.NaN), 0.001);
    }

    @Test
    public void localBlurRadiusCompensatesSceneScale() {
        assertEquals(96.0, GlassLiveViewport.localBlurRadius(96.0, 1.0), 0.001);
        assertEquals(48.0, GlassLiveViewport.localBlurRadius(96.0, 2.0), 0.001);
        assertEquals(192.0, GlassLiveViewport.localBlurRadius(96.0, 0.5), 0.001);
        assertEquals(96.0, GlassLiveViewport.localBlurRadius(96.0, 0.0), 0.001);
        assertEquals(96.0, GlassLiveViewport.localBlurRadius(96.0, Double.NaN), 0.001);
        assertEquals(1.0, GlassLiveViewport.localBlurRadius(0.0, 1.0), 0.001);
        assertEquals(1.0, GlassLiveViewport.localBlurRadius(-5.0, 1.0), 0.001);
    }

    private static GlassLiveViewport.Mapping map(
            double cardX, double cardY, double cardWidth, double cardHeight,
            double nodeX, double nodeY, double nodeWidth, double nodeHeight,
            double imageWidth, double imageHeight, double margin) {
        return GlassLiveViewport.map(cardX, cardY, cardWidth, cardHeight,
                nodeX, nodeY, nodeWidth, nodeHeight,
                imageWidth, imageHeight, margin);
    }

    private static void assertRectangle(Rectangle2D rectangle,
                                        double minX, double minY, double width, double height) {
        assertNotNull(rectangle);
        assertRectangle(rectangle.getMinX(), rectangle.getMinY(),
                rectangle.getWidth(), rectangle.getHeight(), minX, minY, width, height);
    }

    private static void assertRectangle(double actualX, double actualY, double actualWidth, double actualHeight,
                                        double expectedX, double expectedY, double expectedWidth, double expectedHeight) {
        assertEquals(expectedX, actualX, 0.001);
        assertEquals(expectedY, actualY, 0.001);
        assertEquals(expectedWidth, actualWidth, 0.001);
        assertEquals(expectedHeight, actualHeight, 0.001);
    }
}
