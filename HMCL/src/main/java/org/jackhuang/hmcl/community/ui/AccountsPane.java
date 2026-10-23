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
 * Modified by LMCL team on 2026-10-22: embeddable account manager for “Other”.
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
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.account.AccountListPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// “管理 → 账户”内嵌页：账户列表 + 新增账户工具栏。
///
/// 复用上游 [AccountListPage] 的列表皮肤，但隐藏其左侧 200 px 导航，
/// 由本页顶部工具栏提供 Microsoft/离线/认证服务器入口；不新增上游侵入。
///
/// 线程：构造与菜单操作只在 FX 线程。
@NotNullByDefault
public final class AccountsPane extends VBox {

    /// 上游账户列表页；只使用其 center 列表，left 在构造时隐藏。
    private final AccountListPage page = new AccountListPage();

    /// 构造账户页。
    public AccountsPane() {
        FXUtils.checkFxUserThread();

        setSpacing(10);
        setPadding(new Insets(10));

        page.getLeft().setManaged(false);
        page.getLeft().setVisible(false);
        page.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        page.accountsProperty().bindContent(Accounts.getAccounts());
        page.selectedAccountProperty().bindBidirectional(Accounts.selectedAccountProperty());
        page.authServersProperty().bindContentBidirectional(SettingsManager.getAuthlibInjectorServers());
        VBox.setVgrow(page, Priority.ALWAYS);

        HBox toolbar = new HBox(8,
                borderButton("account.methods.microsoft", AccountActions::openMicrosoft),
                borderButton("account.methods.offline", AccountActions::openOffline),
                createAuthServerMenu());
        toolbar.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(toolbar, page);
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

    /// 认证服务器账户菜单：现有服务器的登录入口 + 新增服务器。
    ///
    /// @return 菜单按钮
    @NotNull
    private static MenuButton createAuthServerMenu() {
        MenuButton menu = new MenuButton(i18n("account.injector.server"));
        menu.getStyleClass().add("jfx-button");
        menu.setOnShowing(event -> rebuildAuthServerMenu(menu));
        return menu;
    }

    /// 重新填充认证服务器菜单（每次弹出时读取最新服务器列表）。
    ///
    /// @param menu 菜单
    private static void rebuildAuthServerMenu(@NotNull MenuButton menu) {
        menu.getItems().clear();

        for (AuthlibInjectorServer server : SettingsManager.getAuthlibInjectorServers()) {
            MenuItem item = new MenuItem(server.getName());
            item.setOnAction(event -> AccountActions.openAuthServerAccount(server));
            menu.getItems().add(item);
        }

        if (!menu.getItems().isEmpty()) {
            menu.getItems().add(new SeparatorMenuItem());
        }

        MenuItem addServer = new MenuItem(i18n("account.injector.add"));
        addServer.setOnAction(event -> AccountActions.openAddAuthServer());
        menu.getItems().add(addServer);
    }

}
