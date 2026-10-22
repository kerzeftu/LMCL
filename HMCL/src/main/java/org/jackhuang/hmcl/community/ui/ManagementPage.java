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
 * Modified by LMCL team on 2026-10-22: account and instance management page.
 */
package org.jackhuang.hmcl.community.ui;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.ui.construct.TabControl;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.main.JavaManagementPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// “管理”页：侧栏“管理”入口，左侧为账户/实例/Java 二级菜单。
///
/// 账户与实例复用 [AccountsPane]、[InstancesPane]，不复制上游页面逻辑；
/// 上游页面自带的 200 px 导航在 [AccountsPane]/[InstancesPane] 内隐藏；
/// Java 管理直接复用上游 [JavaManagementPage]（该页无左侧栏）。
///
/// 线程：单例构造与页面切换均在 FX 线程。
@NotNullByDefault
public final class ManagementPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {

    private static @Nullable ManagementPage instance;

    // 顶层页面由左侧图标栏导航；标题栏只保留返回按钮，不显示主页/关闭按钮。
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(
            new State(i18n("community.management.title"), null, true, false, true));

    private final TransitionPane transitionPane = new TransitionPane();
    private final TabHeader.Tab<AccountsPane> accountsTab = new TabHeader.Tab<>("managementAccountsPage");
    private final TabHeader.Tab<InstancesPane> instancesTab = new TabHeader.Tab<>("managementInstancesPage");
    private final TabControl.Tab<JavaManagementPage> javaTab = new TabControl.Tab<>("managementJavaPage");
    private final TabHeader tab;

    private ManagementPage() {
        accountsTab.setNodeSupplier(AccountsPane::new);
        instancesTab.setNodeSupplier(InstancesPane::new);
        javaTab.setNodeSupplier(JavaManagementPage::new);

        tab = new TabHeader(transitionPane, accountsTab, instancesTab, javaTab);
        tab.select(accountsTab);

        AdvancedListBox sideBar = new AdvancedListBox()
                .startCategory(i18n("community.management.category").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, accountsTab, i18n("community.management.tab.accounts"),
                        SVG.PERSON, SVG.PERSON)
                .addNavigationDrawerTab(tab, instancesTab, i18n("community.management.tab.instances"),
                        SVG.GAMEPAD, SVG.GAMEPAD)
                .addNavigationDrawerTab(tab, javaTab, i18n("java.management"),
                        SVG.LOCAL_CAFE, SVG.LOCAL_CAFE);

        FXUtils.setLimitWidth(sideBar, 200);
        setLeft(sideBar);
        setCenter(transitionPane);
    }

    /// 获取单例；重复打开时复用同一实例。
    ///
    /// @return 页面
    @NotNull
    public static ManagementPage getInstance() {
        FXUtils.checkFxUserThread();
        if (instance == null) {
            instance = new ManagementPage();
        }
        return instance;
    }

    /// 选中账户 tab。
    public void showAccounts() {
        tab.select(accountsTab, false);
    }

    /// 选中实例 tab。
    public void showInstances() {
        tab.select(instancesTab, false);
    }

    /// 选中 Java 管理 tab。
    public void showJava() {
        tab.select(javaTab, false);
    }

    @Override
    public void onPageShown() {
        tab.onPageShown();
    }

    @Override
    public void onPageHidden() {
        tab.onPageHidden();
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
