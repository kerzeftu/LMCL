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
 * Modified by LMCL team on 2026-10-22: memory optimization entry in “Other”.
 */
package org.jackhuang.hmcl.community.ui;

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.memory.CommunityMemorySettingsPane;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// “其他 → 内存优化”内嵌页。
///
/// 上半部分是自动回收的只读指标与开关（复用 [CommunityMemorySettingsPane]），
/// 下半部分提供前往“设置 → 游戏 → 启动器设置”的入口；启动前优化本身由
/// [org.jackhuang.hmcl.community.memory.MemoryLaunchOptimizationTask] 执行。
///
/// 线程：构造只在 FX 线程。
@NotNullByDefault
public final class MemoryOptimizationView extends VBox {

    /// 构造内存优化页。
    public MemoryOptimizationView() {
        FXUtils.checkFxUserThread();

        CommunityStyles.apply(this);
        setSpacing(10);
        setPadding(new Insets(10));
        setFillWidth(true);

        Label title = new Label(i18n("community.memory.optimization.title"));
        title.getStyleClass().add("title-label");

        Label hint = new Label(i18n("community.memory.optimization.hint"));
        hint.setWrapText(true);
        hint.getStyleClass().addAll("hint", "info");

        CommunityMemorySettingsPane status = new CommunityMemorySettingsPane();

        JFXButton openSettings = FXUtils.newBorderButton(i18n("community.memory.optimization.open_settings"));
        openSettings.setOnAction(event -> openGameSettings());
        HBox actions = new HBox(openSettings);
        actions.setAlignment(Pos.CENTER_RIGHT);

        getChildren().setAll(title, hint, status, actions);
    }

    /// 打开设置页的全局游戏设置；没有选中实例时退回设置页首页。
    private void openGameSettings() {
        try {
            Controllers.getSettingsPage().showGameSettings(GameDirectoryManager.getSelectedRepository());
        } catch (Throwable e) {
            LOG.debug("failed to open game settings for memory optimization", e);
        }
        Controllers.navigate(Controllers.getSettingsPage());
    }
}
