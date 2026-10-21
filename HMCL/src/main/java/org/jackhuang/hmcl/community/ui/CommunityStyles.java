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

import javafx.scene.Parent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.net.URL;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 社区版界面的节点级样式表入口。
///
/// 样式表只挂到社区版自己的根节点（`Parent.getStylesheets()`），不修改上游
/// `StyleSheets`、`root.css` 或主题文件；因此用户的主题色、字体与亮暗模式仍由
/// 上游 `StyleSheets` 管理。重复调用是幂等的。
@NotNullByDefault
public final class CommunityStyles {

    /// 样式表资源路径。
    private static final String STYLESHEET = "/assets/css/community.css";

    /// 节点属性：避免对同一节点重复解析样式表。
    private static final String APPLIED_PROPERTY = "lmcl.community.styles";

    /// 工具类，不允许实例化。
    private CommunityStyles() {
    }

    /// 给节点挂载社区版样式表。
    ///
    /// 必须在 JavaFX 应用线程调用（与构造 UI 的线程一致）。
    ///
    /// @param parent 社区版界面的根节点
    public static void apply(@NotNull Parent parent) {
        if (parent.getProperties().putIfAbsent(APPLIED_PROPERTY, Boolean.TRUE) != null) {
            return;
        }
        URL url = CommunityStyles.class.getResource(STYLESHEET);
        if (url == null) {
            LOG.warning("Community stylesheet is missing: " + STYLESHEET);
            return;
        }
        String external = url.toExternalForm();
        if (!parent.getStylesheets().contains(external)) {
            parent.getStylesheets().add(external);
        }
    }
}
