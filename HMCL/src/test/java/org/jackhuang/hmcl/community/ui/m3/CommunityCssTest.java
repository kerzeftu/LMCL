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

import javafx.css.CssParser;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 社区 CSS 的语法冒烟测试。
public final class CommunityCssTest {

    @Test
    public void parsesCommunityStylesheets() throws Exception {
        assertParses("/assets/css/community.css");
        assertParses("/assets/css/m3-community.css");
    }

    @Test
    public void frostedMaterialDeclaresFiveLayerWeights() throws Exception {
        String css = read("/assets/css/m3-community.css");
        for (int i = 0; i < GlassSurface.maxLayers(); i++) {
            assertTrue(css.contains(".lmcl-m3-glass-layer-" + i), "missing layer " + i);
        }
        assertTrue(css.contains(".lmcl-m3-glass-compact .lmcl-m3-glass-layer-4"),
                "missing compact layer weight");
        assertTrue(css.contains(".lmcl-m3-glass .community-home .community-material-card > .card"),
                "material card surface must be transparent so the glass layers show through");
    }

    private static void assertParses(String resource) throws Exception {
        URL url = CommunityCssTest.class.getResource(resource);
        assertNotNull(url, resource);
        CssParser errors = new CssParser();
        errors.parse(url);
        assertTrue(CssParser.errorsProperty().isEmpty(),
                resource + " -> " + CssParser.errorsProperty());
    }

    private static String read(String resource) throws Exception {
        URL url = CommunityCssTest.class.getResource(resource);
        assertNotNull(url, resource);
        try (var stream = url.openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
