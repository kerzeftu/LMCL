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
 *
 * Modified by LMCL team on 2026-10-23: M3 switch parsing tests.
 */
package org.jackhuang.hmcl.community.ui.m3;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// `M3Settings` 的纯函数与系统属性覆盖测试（不启动 JavaFX）。
public final class M3SettingsTest {

    @AfterEach
    public void clearProperty() {
        System.clearProperty(M3Settings.M3_PROPERTY);
        System.clearProperty(M3Settings.GLASS_PROPERTY);
    }

    @Test
    public void parseBooleanAcceptsCommonForms() {
        assertEquals(Boolean.TRUE, M3Settings.parseBoolean("on"));
        assertEquals(Boolean.TRUE, M3Settings.parseBoolean("TRUE"));
        assertEquals(Boolean.TRUE, M3Settings.parseBoolean(" yes "));
        assertEquals(Boolean.TRUE, M3Settings.parseBoolean("1"));
        assertEquals(Boolean.FALSE, M3Settings.parseBoolean("off"));
        assertEquals(Boolean.FALSE, M3Settings.parseBoolean("False"));
        assertEquals(Boolean.FALSE, M3Settings.parseBoolean("no"));
        assertEquals(Boolean.FALSE, M3Settings.parseBoolean("0"));
    }

    @Test
    public void parseBooleanReturnsNullForUnknownValues() {
        assertNull(M3Settings.parseBoolean(null));
        assertNull(M3Settings.parseBoolean(""));
        assertNull(M3Settings.parseBoolean("maybe"));
    }

    @Test
    public void systemPropertyOverridesPersistedValue() {
        System.setProperty(M3Settings.M3_PROPERTY, "on");
        assertTrue(M3Settings.isM3Enabled());

        System.setProperty(M3Settings.M3_PROPERTY, "off");
        assertFalse(M3Settings.isM3Enabled());
    }

    @Test
    public void glassModeNormalizesAndFallsBack() {
        System.setProperty(M3Settings.GLASS_PROPERTY, "ON");
        assertEquals("on", M3Settings.glassMode());

        System.setProperty(M3Settings.GLASS_PROPERTY, "invalid");
        assertEquals("auto", M3Settings.glassMode());
    }
}
