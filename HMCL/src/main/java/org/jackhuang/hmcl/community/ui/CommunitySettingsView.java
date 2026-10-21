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
 * Modified by LMCL team on 2026-10-22: inline community settings for “Other”.
 */
package org.jackhuang.hmcl.community.ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// “其他 → 社区设置”内嵌页：复用 [CommunitySettingsPanel] 的分组。
///
/// 不打开对话框，直接把 panel 的分组切换区嵌进页面；适合“其他”页的
/// 200 px 二级菜单之后，视觉层级只多一层分组按钮。
///
/// 线程：构造只在 FX 线程。
@NotNullByDefault
public final class CommunitySettingsView extends VBox {

    /// 构造内嵌设置页。
    public CommunitySettingsView() {
        FXUtils.checkFxUserThread();

        setSpacing(10);
        setPadding(new Insets(10));

        Label title = new Label(i18n("community.settings.title"));
        title.getStyleClass().add("title-label");

        Parent body = CommunitySettingsPanel.createInlineView(null);
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(title, body);
    }
}
