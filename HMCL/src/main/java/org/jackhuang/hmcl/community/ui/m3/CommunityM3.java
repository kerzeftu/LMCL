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
 * Modified by LMCL team on 2026-10-22: Material 3 Expressive theme bridge spike.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.scene.Parent;
import org.glavo.m3fx.theme.M3Theme;
import org.glavo.m3fx.theme.M3ThemeManager;
import org.glavo.m3fx.tokens.M3Profile;
import org.jackhuang.hmcl.setting.FontManager;
import org.jackhuang.hmcl.theme.Themes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.URL;

/// 把 LMCL 的当前主题色与字体桥接给 m3fx。
///
/// 这里只做阶段 0 的最小验证：把 M3 主题安装在指定子树，并追加社区覆盖样式。
/// 不接管 `Scene`、不修改 `StyleSheets`，因此不会影响上游页面的样式表绑定。
/// 阶段 1 会把字体/主题色监听、动效开关和持久化配置补全。
@NotNullByDefault
public final class CommunityM3 {

    /// 社区 M3 覆盖样式表。
    private static final String STYLESHEET = "/assets/css/m3-community.css";

    /// 记录安装状态的属性键，避免重复安装。
    private static final String INSTALLED_PROPERTY = "lmcl.m3.installed";

    /// 工具类，不允许实例化。
    private CommunityM3() {
    }

    /// 在指定子树安装 M3 Expressive 主题。
    ///
    /// 必须在 JavaFX 线程调用；主题以 `Themes.getColorScheme()` 为颜色来源，
    /// 字体跟随 `FontManager`（未配置自定义字体时继承组件默认字体）。
    /// 安装后追加 [#STYLESHEET]，用于修正 `.m3-root` 带来的背景和文字色。
    ///
    /// @param root 主题作用范围的根节点
    public static void install(@NotNull Parent root) {
        if (root.getProperties().get(INSTALLED_PROPERTY) != null) {
            return;
        }

        M3Theme theme = M3Theme.fromColorScheme(M3Profile.EXPRESSIVE_2025, Themes.getColorScheme());
        M3ThemeManager.install(root, theme);
        root.getProperties().put(INSTALLED_PROPERTY, theme);
        root.getStyleClass().add("community-m3-host");

        URL stylesheet = CommunityM3.class.getResource(STYLESHEET);
        if (stylesheet != null) {
            String url = stylesheet.toExternalForm();
            if (!root.getStylesheets().contains(url)) {
                root.getStylesheets().add(url);
            }
        }

        // FontManager.getFont() 在用户没有自定义字体且系统默认字体探测失败时为 null，
        // 此时不要写 inline style，让 m3fx / 上游 CSS 的字体继承继续生效。
        String style = fontStyle(FontManager.getFont());
        if (style != null) {
            root.setStyle(style);
        }
    }

    /// 生成字体覆盖样式；字体未解析时返回 `null`，由调用方保留继承字体。
    ///
    /// 单独拆出便于测试空字体与引号/反斜杠转义，避免再次出现
    /// `FontManager.getFont()` 为 null 时的 NPE。
    @Nullable
    static String fontStyle(@Nullable FontManager.FontReference font) {
        if (font == null) {
            return null;
        }
        String family = font.family().replace("\\", "\\\\").replace("\"", "\\\"");
        return "-fx-font-family: \"" + family + "\"; -fx-background-color: transparent;";
    }
}
