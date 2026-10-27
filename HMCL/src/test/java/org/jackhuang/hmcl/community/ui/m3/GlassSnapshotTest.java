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
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;

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
        assertEquals(32.0, GlassBackdrop.blurRadius(64.0, 0.5), 0.001);
        assertEquals(1.0, GlassBackdrop.blurRadius(0.0, 0.5), 0.001);
    }

    @Test
    public void rejectsImagesWithDifferentSizes() {
        WritableImage far = new WritableImage(100, 100);
        WritableImage near = new WritableImage(80, 80);

        assertThrows(IllegalArgumentException.class,
                () -> new GlassSnapshot(far, near, 1.0, 0.0, 0.0, 0));
    }

    private static GlassSnapshot snapshot(int width, int height, double scale, double originX, double originY) {
        WritableImage image = new WritableImage(width, height);
        return new GlassSnapshot(image, image, scale, originX, originY, 0);
    }
}
