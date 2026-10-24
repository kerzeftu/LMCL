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
package org.jackhuang.hmcl.community.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// `RoundedHitArea` 的纯几何命中测试。
final class RoundedHitAreaTest {

    @Test
    void rejectsTransparentCorners() {
        assertFalse(RoundedHitArea.containsRounded(1, 1, 100, 40, 20));
        assertFalse(RoundedHitArea.containsRounded(99, 1, 100, 40, 20));
        assertTrue(RoundedHitArea.containsRounded(15, 5, 100, 40, 20));
    }

    @Test
    void acceptsCenterAndEdges() {
        assertTrue(RoundedHitArea.containsRounded(50, 20, 100, 40, 20));
        assertTrue(RoundedHitArea.containsRounded(0, 20, 100, 40, 20));
        assertTrue(RoundedHitArea.containsRounded(50, 0, 100, 40, 20));
    }

    @Test
    void clampsRadiusToHalfOfShortSide() {
        assertTrue(RoundedHitArea.containsRounded(1, 20, 100, 40, 999));
        assertFalse(RoundedHitArea.containsRounded(1, 1, 100, 40, 999));
    }

    @Test
    void rejectsPointsOutsideBounds() {
        assertFalse(RoundedHitArea.containsRounded(-1, 20, 100, 40, 20));
        assertFalse(RoundedHitArea.containsRounded(101, 20, 100, 40, 20));
    }
}
