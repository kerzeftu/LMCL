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

import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.account.AddAuthlibInjectorServerPane;
import org.jackhuang.hmcl.ui.account.CreateAccountPane;
import org.jackhuang.hmcl.ui.account.MicrosoftAccountLoginPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 账户相关的公共操作。
///
/// 管理页的账户工具栏与首页账户卡共用这些入口，避免复制登录/只读确认逻辑。
/// 所有方法都必须在 JavaFX 线程调用；只读确认的回调在用户确认后执行。
@NotNullByDefault
public final class AccountActions {

    /// 工具类，不允许实例化。
    private AccountActions() {
    }

    /// 打开 Microsoft 登录。
    public static void openMicrosoft() {
        withAccountWriteAccess(() -> Controllers.dialog(new MicrosoftAccountLoginPane()));
    }

    /// 打开离线账户创建。
    public static void openOffline() {
        withAccountWriteAccess(() -> Controllers.dialog(new CreateAccountPane(Accounts.FACTORY_OFFLINE)));
    }

    /// 打开指定认证服务器的账户创建。
    ///
    /// @param server 认证服务器
    public static void openAuthServerAccount(@NotNull AuthlibInjectorServer server) {
        withAccountWriteAccess(() -> Controllers.dialog(new CreateAccountPane(server)));
    }

    /// 打开“新增认证服务器”。
    public static void openAddAuthServer() {
        if (SettingsManager.isAuthlibInjectorServersReadOnly()) {
            Controllers.confirmBackupAndOverwrite(i18n("account.injector.server.storage.read_only"), () -> {
                SettingsManager.forceOverwriteAuthlibInjectorServers();
                Controllers.dialog(new AddAuthlibInjectorServerPane());
            });
        } else {
            Controllers.dialog(new AddAuthlibInjectorServerPane());
        }
    }

    /// 账户文件只读时先确认覆盖再执行。
    ///
    /// @param action 账户写入动作
    public static void withAccountWriteAccess(@NotNull Runnable action) {
        if (SettingsManager.isUserGameAccountsReadOnly()) {
            Controllers.confirmBackupAndOverwrite(i18n("account.storage.read_only"), () -> {
                SettingsManager.forceOverwriteUserGameAccounts();
                action.run();
            });
        } else {
            action.run();
        }
    }
}
