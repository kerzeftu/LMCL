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
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 社区 CSS 的语法冒烟测试。
@NotNullByDefault
public final class CommunityCssTest {

    /// 玻璃透明规则的选择器；四类，必须压过 `.lmcl-m3 .community-home .card` 的三类实色规则。
    private static final String GLASS_SURFACE_SELECTOR =
            ".lmcl-m3-glass .community-home .community-material-card > .card";

    /// 三类实色规则的选择器。
    private static final String OPAQUE_CARD_SELECTOR =
            ".lmcl-m3 .community-home .card";

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
        assertTrue(ruleBody(css, GLASS_SURFACE_SELECTOR).contains("-fx-background-color: transparent"),
                "material card surface must be transparent so the glass layers show through");
    }

    /// 玻璃透明规则必须位于三类实色规则之后，且确实声明透明背景。
    @Test
    public void materialCardTransparencyComesAfterOpaqueRule() throws Exception {
        String css = stripComments(read("/assets/css/m3-community.css"));

        int opaqueIndex = ruleIndex(css, OPAQUE_CARD_SELECTOR);
        int transparentIndex = ruleIndex(css, GLASS_SURFACE_SELECTOR);
        assertTrue(transparentIndex > opaqueIndex,
                "glass surface rule must come after the opaque card rule to win the cascade");

        String body = ruleBody(css, GLASS_SURFACE_SELECTOR);
        assertTrue(body.contains("-fx-background-color: transparent"), body);
        assertTrue(body.contains("-fx-border-color: transparent"), body);
    }

    /// 材质卡的透明规则只保留一份声明式来源，旧的 redundant 规则不能复活。
    @Test
    public void communityStylesheetNoLongerRedefinesMaterialCardTransparency() throws Exception {
        String css = stripComments(read("/assets/css/community.css"));
        assertFalse(css.contains(".lmcl-m3-glass .community-material-card > .card"),
                "duplicate transparency rule must live only in m3-community.css");
    }

    /// 解析样式表并确认没有语法错误。
    ///
    /// @param resource 类路径资源
    private static void assertParses(String resource) throws Exception {
        URL url = CommunityCssTest.class.getResource(resource);
        assertNotNull(url, resource);
        CssParser errors = new CssParser();
        errors.parse(url);
        assertTrue(CssParser.errorsProperty().isEmpty(),
                resource + " -> " + CssParser.errorsProperty());
    }

    /// 读取类路径资源为 UTF-8 文本。
    ///
    /// @param resource 类路径资源
    /// @return 文本内容
    private static String read(String resource) throws Exception {
        URL url = CommunityCssTest.class.getResource(resource);
        assertNotNull(url, resource);
        try (var stream = url.openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /// 去掉 CSS 注释，避免注释里的选择器干扰规则定位。
    ///
    /// @param css 原始样式表
    /// @return 去注释后的样式表
    private static String stripComments(String css) {
        return css.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    /// 定位选择器对应规则的起始下标。
    ///
    /// @param css      去注释后的样式表
    /// @param selector 选择器字面量
    /// @return 规则起始下标（指向选择器行）
    private static int ruleIndex(String css, String selector) {
        Matcher matcher = rulePattern(selector).matcher(css);
        assertTrue(matcher.find(), "missing rule " + selector);
        return matcher.start();
    }

    /// 提取选择器对应规则的声明块内容。
    ///
    /// @param css      去注释后的样式表
    /// @param selector 选择器字面量
    /// @return 花括号内的声明文本
    private static String ruleBody(String css, String selector) {
        Matcher matcher = Pattern.compile(
                "(?m)^\\s*" + Pattern.quote(selector) + "\\s*\\{([^}]*)\\}").matcher(css);
        assertTrue(matcher.find(), "missing rule " + selector);
        return matcher.group(1);
    }

    /// 构造定位“以某选择器独立成行开头”的规则正则。
    ///
    /// @param selector 选择器字面量
    /// @return 规则定位正则
    private static Pattern rulePattern(String selector) {
        return Pattern.compile("(?m)^\\s*" + Pattern.quote(selector) + "\\s*\\{");
    }
}
