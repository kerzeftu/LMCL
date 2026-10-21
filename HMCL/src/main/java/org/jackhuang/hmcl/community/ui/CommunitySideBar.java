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
 * Modified by LMCL team on 2026-10-22: global 64px icon sidebar used by the
 * Axolotl-inspired home page.
 */
package org.jackhuang.hmcl.community.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.download.DownloadPage;
import org.jackhuang.hmcl.ui.main.LauncherSettingsPage;
import org.jackhuang.hmcl.ui.main.RootPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 全局 64px 图标侧栏，风格参考美西螈启动器。
///
/// 放在 [org.jackhuang.hmcl.ui.decorator.DecoratorController] 的 `HBox` 中，
/// 左侧固定宽度、右侧为 [org.jackhuang.hmcl.ui.construct.Navigator]，因此
/// 在主页、下载、设置等所有页面都可见。
///
/// 线程：构造、样式与点击回调都在 FX 线程。
@NotNullByDefault
public final class CommunitySideBar extends VBox {

    /// 侧栏宽度（像素）。
    public static final double WIDTH = 64;

    /// 按钮尺寸（像素）。
    private static final double BUTTON_SIZE = 44;

    /// 主页按钮。
    private final JFXButton homeButton;

    /// 下载按钮。
    private final JFXButton downloadButton;

    /// 设置按钮。
    private final JFXButton settingsButton;

    /// “其他”按钮。
    private final JFXButton otherButton;

    /// 构造侧栏。
    public CommunitySideBar() {
        getStyleClass().add("community-sidebar");
        CommunityStyles.apply(this);
        setPrefWidth(WIDTH);
        setMinWidth(WIDTH);
        setMaxWidth(WIDTH);
        setMaxHeight(Double.MAX_VALUE);
        setPadding(new Insets(12, 8, 12, 8));
        setSpacing(6);
        setAlignment(Pos.TOP_CENTER);

        homeButton = createButton(SVG.HOME, "community.sidebar.home", this::openHome);
        downloadButton = createButton(SVG.DOWNLOAD, "community.sidebar.download", this::openDownloads);
        settingsButton = createButton(SVG.SETTINGS, "community.sidebar.settings", this::openSettings);
        otherButton = createButton(SVG.MORE_HORIZ, "community.sidebar.other", this::showOtherMenu);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(homeButton, downloadButton, settingsButton, spacer, otherButton);
    }

    /// 根据当前页面更新选中态；由 `DecoratorController#onNavigated` 调用。
    ///
    /// @param page 当前页面
    public void setActive(@Nullable Node page) {
        RootPage rootPage = Controllers.getRootPage();
        boolean home = page == rootPage || (rootPage != null && page == rootPage.getMainPage());
        boolean download = page instanceof DownloadPage;
        boolean settings = page instanceof LauncherSettingsPage;
        boolean other = !home && !download && !settings;

        setActive(homeButton, home);
        setActive(downloadButton, download);
        setActive(settingsButton, settings);
        setActive(otherButton, other);
    }

    /// 创建图标按钮。
    @NotNull
    private JFXButton createButton(@NotNull SVG icon, @NotNull String tooltipKey, @NotNull Runnable action) {
        JFXButton button = new JFXButton();
        button.getStyleClass().add("community-sidebar-button");
        button.setGraphic(icon.createIcon(20));
        button.setPrefSize(BUTTON_SIZE, BUTTON_SIZE);
        button.setMinSize(BUTTON_SIZE, BUTTON_SIZE);
        button.setMaxSize(BUTTON_SIZE, BUTTON_SIZE);
        button.setFocusTraversable(false);
        button.setOnAction(event -> action.run());
        FXUtils.installFastTooltip(button, i18n(tooltipKey));
        return button;
    }

    /// 设置选中样式。
    private static void setActive(@NotNull JFXButton button, boolean active) {
        if (active) {
            if (!button.getStyleClass().contains("active")) {
                button.getStyleClass().add("active");
            }
        } else {
            button.getStyleClass().remove("active");
        }
    }

    /// 回到主页根页；从下载/设置页返回时保留 Navigator 的返回栈。
    private void openHome() {
        try {
            Controllers.navigate(Controllers.getRootPage());
        } catch (Throwable e) {
            LOG.warning("Failed to navigate to home", e);
        }
    }

    /// 打开下载页并定位到游戏下载。
    private void openDownloads() {
        try {
            Controllers.getDownloadPage().showGameDownloads();
            Controllers.navigate(Controllers.getDownloadPage());
        } catch (Throwable e) {
            LOG.warning("Failed to open download page", e);
        }
    }

    /// 打开设置页并尽量定位到当前实例设置。
    private void openSettings() {
        try {
            HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
            if (repository != null) {
                Controllers.getSettingsPage().showGameSettings(repository);
            }
        } catch (Throwable e) {
            LOG.warning("Failed to prepare launcher settings", e);
        }
        try {
            Controllers.navigate(Controllers.getSettingsPage());
        } catch (Throwable e) {
            LOG.warning("Failed to open launcher settings", e);
        }
    }

    /// 显示“其他”菜单。
    private void showOtherMenu() {
        VBox menu = new VBox(2);
        menu.getStyleClass().add("community-sidebar-menu");
        // JFXPopup creates a separate scene; attach the community stylesheet to
        // the popup root so its menu items do not fall back to unreadable
        // defaults (grey text on a dark popup background).
        CommunityStyles.apply(menu);

        JFXPopup popup = new JFXPopup(menu);
        popup.setAutoHide(true);

        addMenuItem(menu, "community.sidebar.other.instances", popup, () ->
                Controllers.navigate(Controllers.getGameListPage()));
        addMenuItem(menu, "community.sidebar.other.accounts", popup, () ->
                Controllers.navigate(Controllers.getAccountListPage()));
        addMenuItem(menu, "community.sidebar.other.community", popup, () ->
                CommunitySettingsPanel.open(getWindow()));
        addMenuItem(menu, "community.sidebar.other.feedback", popup, this::openFeedback);
        addMenuItem(menu, "community.sidebar.other.about", popup, () ->
                CommunitySettingsPanel.open(getWindow(), "about"));

        popup.show(otherButton,
                JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, 0);
    }

    /// 添加菜单项。
    private void addMenuItem(@NotNull VBox menu, @NotNull String key,
                             @NotNull JFXPopup popup, @NotNull Runnable action) {
        JFXButton item = new JFXButton(i18n(key));
        item.getStyleClass().add("community-sidebar-menu-item");
        // Inline fallback: the popup scene does not inherit the launcher theme,
        // so keep the label readable even if the stylesheet is unavailable.
        item.setStyle("-fx-text-fill: #ffffff;");
        item.setMaxWidth(Double.MAX_VALUE);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setOnAction(event -> {
            popup.hide();
            action.run();
        });
        menu.getChildren().add(item);
    }

    /// 打开反馈页。
    private void openFeedback() {
        try {
            Controllers.getSettingsPage().showFeedback();
            Controllers.navigate(Controllers.getSettingsPage());
        } catch (Throwable e) {
            LOG.warning("Failed to open feedback page", e);
        }
    }

    /// 当前窗口；`getScene()` 尚不可用时返回 `null`。
    @Nullable
    private Window getWindow() {
        return getScene() == null ? null : getScene().getWindow();
    }
}
