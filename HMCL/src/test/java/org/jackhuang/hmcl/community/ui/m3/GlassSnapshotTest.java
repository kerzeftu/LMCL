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
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// [GlassSnapshot] 的纯几何测试。
public final class GlassSnapshotTest {

    @Test
    public void viewportUsesSceneOriginAndScale() {
        GlassSnapshot snapshot = snapshot(100, 100, 0.5, 10.0, 20.0);

        Rectangle2D viewport = snapshot.viewport(10.0, 20.0, 200.0, 200.0);

        assertNotNull(viewport);
        assertEquals(0.0, viewport.getMinX(), 0.001);
        assertEquals(0.0, viewport.getMinY(), 0.001);
        assertEquals(100.0, viewport.getWidth(), 0.001);
        assertEquals(100.0, viewport.getHeight(), 0.001);
    }

    @Test
    public void viewportIsClampedToSnapshot() {
        GlassSnapshot snapshot = snapshot(100, 100, 0.5, 10.0, 20.0);

        Rectangle2D viewport = snapshot.viewport(30.0, 50.0, 400.0, 400.0);

        assertNotNull(viewport);
        assertEquals(10.0, viewport.getMinX(), 0.001);
        assertEquals(15.0, viewport.getMinY(), 0.001);
        assertEquals(90.0, viewport.getWidth(), 0.001);
        assertEquals(85.0, viewport.getHeight(), 0.001);
    }

    @Test
    public void viewportRejectsPointsOutsideImage() {
        GlassSnapshot snapshot = snapshot(100, 100, 1.0, 0.0, 0.0);

        assertNull(snapshot.viewport(-100.0, 0.0, 10.0, 10.0));
        assertNull(snapshot.viewport(0.0, -100.0, 10.0, 10.0));
        assertNull(snapshot.viewport(100.0, 100.0, 10.0, 10.0));
    }

    @Test
    public void blurRadiusTracksSnapshotScale() {
        assertEquals(96.0, GlassBackdrop.blurRadius(192.0, 0.5), 0.001);
        assertEquals(1.0, GlassBackdrop.blurRadius(0.0, 0.5), 0.001);
    }

    @Test
    public void fiveLayersUseFiveRadiiAndIterations() {
        assertEquals(5, GlassBackdrop.layerCount());
        assertEquals(192.0, GlassBackdrop.blurSceneRadiusAt(0), 0.001);
        assertEquals(128.0, GlassBackdrop.blurSceneRadiusAt(1), 0.001);
        assertEquals(80.0, GlassBackdrop.blurSceneRadiusAt(2), 0.001);
        assertEquals(48.0, GlassBackdrop.blurSceneRadiusAt(3), 0.001);
        assertEquals(24.0, GlassBackdrop.blurSceneRadiusAt(4), 0.001);
        assertEquals(3, GlassBackdrop.iterationsAt(0));
        assertEquals(3, GlassBackdrop.iterationsAt(1));
        assertEquals(2, GlassBackdrop.iterationsAt(2));
        assertEquals(2, GlassBackdrop.iterationsAt(3));
        assertEquals(2, GlassBackdrop.iterationsAt(4));
    }

    @Test
    public void paddingCoversLargestBlur() {
        assertEquals(146, GlassBackdrop.paddingFor(0.5));
        assertEquals(290, GlassBackdrop.paddingFor(1.0));
    }

    @Test
    public void snapshotKeepsAllLayers() {
        WritableImage image = new WritableImage(100, 100);
        GlassSnapshot snapshot = new GlassSnapshot(
                List.of(image, image, image, image, image), 1.0, 0.0, 0.0, 0);

        assertEquals(5, snapshot.layerCount());
        assertEquals(image, snapshot.layer(4));
    }

    @Test
    public void rejectsEmptyLayers() {
        assertThrows(IllegalArgumentException.class,
                () -> new GlassSnapshot(List.of(), 1.0, 0.0, 0.0, 0));
    }

    @Test
    public void rejectsImagesWithDifferentSizes() {
        WritableImage first = new WritableImage(100, 100);
        WritableImage second = new WritableImage(80, 80);

        assertThrows(IllegalArgumentException.class,
                () -> new GlassSnapshot(List.of(first, second), 1.0, 0.0, 0.0, 0));
    }

    @Test
    public void rejectsNullLayer() {
        WritableImage image = new WritableImage(100, 100);

        assertThrows(NullPointerException.class,
                () -> new GlassSnapshot(Arrays.asList(image, null), 1.0, 0.0, 0.0, 0));
    }

    private static GlassSnapshot snapshot(int width, int height, double scale, double originX, double originY) {
        Image image = new WritableImage(width, height);
        return new GlassSnapshot(List.of(image, image, image, image, image), scale, originX, originY, 0);
    }
}
