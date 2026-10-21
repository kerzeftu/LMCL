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
 * Modified by LMCL team on 2026-10-22: embeddable instance manager for “Other”.
 */
package org.jackhuang.hmcl.community.ui;

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.directory.GameDirectoryPage;
import org.jackhuang.hmcl.ui.versions.GameListPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// “其他 → 实例”内嵌页：实例列表 + 新建/导入/游戏目录/全局设置工具栏。
///
/// 复用上游 [GameListPage] 的 center 列表，隐藏其左侧 200 px 游戏目录导航；
/// 游戏目录选择改为顶部 [MenuButton]，因此切换目录、新建目录仍可用。
///
/// 线程：构造只在 FX 线程；按钮动作仍由上游在 FX 线程执行。
@NotNullByDefault
public final class InstancesPane extends VBox {

    /// 上游实例列表页；只使用其 center 列表，left 在构造时隐藏。
    private final GameListPage page = new GameListPage();

    /// 游戏目录选择菜单；替代被隐藏的上游左侧目录列表。
    private final MenuButton directoryMenu = new MenuButton();

    /// 构造实例页。
    public InstancesPane() {
        FXUtils.checkFxUserThread();

        setSpacing(10);
        setPadding(new Insets(10));

        page.getLeft().setManaged(false);
        page.getLeft().setVisible(false);
        page.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(page, Priority.ALWAYS);

        directoryMenu.getStyleClass().add("jfx-button");
        directoryMenu.setOnShowing(event -> rebuildDirectoryMenu());
        GameDirectoryManager.selectedGameDirectoryProperty().addListener(
                (observable, oldValue, newValue) -> updateDirectoryText());
        updateDirectoryText();

        HBox toolbar = new HBox(8,
                directoryMenu,
                borderButton("install.new_game", Versions::addNewGame),
                borderButton("install.modpack", Versions::importModpack),
                borderButton("settings.type.global.manage", page::modifyGlobalGameSettings));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(toolbar, page);
    }

    /// 更新目录按钮文案为当前选中目录的显示名。
    private void updateDirectoryText() {
        GameDirectory selected = GameDirectoryManager.selectedGameDirectoryProperty().get();
        directoryMenu.setText(selected == null
                ? i18n("game_directory")
                : GameDirectoryManager.getGameDirectoryDisplayName(selected));
    }

    /// 每次弹出菜单时重建，保证目录列表最新。
    private void rebuildDirectoryMenu() {
        directoryMenu.getItems().clear();

        for (GameDirectory directory : GameDirectoryManager.getGameDirectories()) {
            MenuItem item = new MenuItem(GameDirectoryManager.getGameDirectoryDisplayName(directory));
            item.setOnAction(event -> GameDirectoryManager.setSelectedGameDirectory(directory));
            directoryMenu.getItems().add(item);
        }

        if (!directoryMenu.getItems().isEmpty()) {
            directoryMenu.getItems().add(new SeparatorMenuItem());
        }

        MenuItem add = new MenuItem(i18n("game_directory.new"));
        add.setOnAction(event -> Controllers.navigate(new GameDirectoryPage(null)));
        directoryMenu.getItems().add(add);
    }

    /// 创建带 HMCL 边框风格的按钮。
    ///
    /// @param key i18n key
    /// @param action 点击动作
    /// @return 按钮
    @NotNull
    private static JFXButton borderButton(@NotNull String key, @NotNull Runnable action) {
        JFXButton button = FXUtils.newBorderButton(i18n(key));
        button.setOnAction(event -> action.run());
        return button;
    }
}
