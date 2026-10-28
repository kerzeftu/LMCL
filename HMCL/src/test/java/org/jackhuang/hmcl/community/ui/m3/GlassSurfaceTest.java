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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [GlassSurface] 的纯静态规则测试。
public final class GlassSurfaceTest {

    @Test
    public void materialUsesFiveLayers() {
        assertEquals(5, GlassSurface.maxLayers());
    }

    @Test
    public void oneByOneCardsUseCompactWeights() {
        assertTrue(GlassSurface.isCompact(200.0, 100.0));
        assertTrue(GlassSurface.isCompact(320.0, 140.0));
        assertFalse(GlassSurface.isCompact(280.0, 150.0));
        assertFalse(GlassSurface.isCompact(600.0, 200.0));
    }
}
