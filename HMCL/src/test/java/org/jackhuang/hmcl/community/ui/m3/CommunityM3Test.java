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
 * Modified by LMCL team on 2026-10-22: null-font regression test for M3 spike.
 */
package org.jackhuang.hmcl.community.ui.m3;

import org.jackhuang.hmcl.setting.FontManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// `CommunityM3` 的字体样式纯函数测试（不启动 JavaFX）。
public final class CommunityM3Test {

    @Test
    public void nullFontKeepsInheritedFont() {
        assertNull(CommunityM3.fontStyle(null));
    }

    @Test
    public void fontFamilyIsQuotedAndEscaped() {
        assertEquals(
                "-fx-font-family: \"A\\\"B\\\\C\"; -fx-background-color: transparent;",
                CommunityM3.fontStyle(new FontManager.FontReference("A\"B\\C")));
    }
}
