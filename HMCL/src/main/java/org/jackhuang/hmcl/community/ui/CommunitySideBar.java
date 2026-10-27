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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.download.CommunityDownloadGuard;
import org.jackhuang.hmcl.community.download.CommunityDownloadInstaller;
import org.jackhuang.hmcl.community.ui.m3.GlassSurface;
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
public final class CommunitySideBar extends StackPane {

    /// 侧栏宽度（像素）。
    public static final double WIDTH = 64;

    /// 按钮尺寸（像素）。
    private static final double BUTTON_SIZE = 44;

    /// 主页按钮。
    private final JFXButton homeButton;

    /// 下载按钮。
    private final JFXButton downloadButton;

    /// 管理按钮。
    private final JFXButton managementButton;

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

        GlassSurface glass = new GlassSurface(0.0, GlassSurface.Kind.SIDEBAR);
        glass.setDebugName("sidebar");
        glass.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        VBox content = new VBox();
        content.getStyleClass().add("community-sidebar-content");
        content.setPadding(new Insets(12, 8, 12, 8));
        content.setSpacing(6);
        content.setAlignment(Pos.TOP_CENTER);
        content.setFillWidth(true);
        content.setMaxHeight(Double.MAX_VALUE);
        StackPane.setAlignment(content, Pos.TOP_CENTER);

        // 侧栏早于 MainPage 创建；在这里安装下载源，保证打开设置/下载页前缓存已就绪。
        try {
            CommunityDownloadInstaller.install();
        } catch (Throwable e) {
            LOG.warning("Failed to install community download sources from sidebar", e);
        }

        homeButton = createButton(SVG.HOME, "community.sidebar.home", this::openHome);
        downloadButton = createButton(SVG.DOWNLOAD, "community.sidebar.download", this::openDownloads);
        managementButton = createButton(SVG.LISTS, "community.sidebar.management", this::openManagement);
        settingsButton = createButton(SVG.SETTINGS, "community.sidebar.settings", this::openSettings);
        otherButton = createButton(SVG.MORE_HORIZ, "community.sidebar.other", this::openOther);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        content.getChildren().addAll(homeButton, downloadButton, managementButton, settingsButton, spacer, otherButton);
        getChildren().addAll(glass, content);
    }

    /// 根据当前页面更新选中态；由 `DecoratorController#onNavigated` 调用。
    ///
    /// @param page 当前页面
    public void setActive(@Nullable Node page) {
        RootPage rootPage = Controllers.getRootPage();
        boolean home = page == rootPage || (rootPage != null && page == rootPage.getMainPage());
        boolean download = page instanceof DownloadPage;
        boolean management = page instanceof ManagementPage;
        boolean settings = page instanceof LauncherSettingsPage;
        boolean other = page instanceof OtherPage;

        setActive(homeButton, home);
        setActive(downloadButton, download);
        setActive(managementButton, management);
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
            javafx.stage.Window owner = getScene() == null ? null : getScene().getWindow();
            if (!CommunityDownloadGuard.requireConfigured(owner)) {
                return;
            }
            Controllers.getDownloadPage().showGameDownloads();
            Controllers.navigate(Controllers.getDownloadPage());
        } catch (Throwable e) {
            LOG.warning("Failed to open download page", e);
        }
    }

    /// 打开“管理”页（账户 / 实例）。
    private void openManagement() {
        try {
            Controllers.navigate(ManagementPage.getInstance());
        } catch (Throwable e) {
            LOG.warning("Failed to open management page", e);
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

    /// 打开“其他”页。
    private void openOther() {
        try {
            Controllers.navigate(OtherPage.getInstance());
        } catch (Throwable e) {
            LOG.warning("Failed to open other page", e);
        }
    }
}
