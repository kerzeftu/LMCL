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
 * Modified by LMCL team on 2026-10-22: dedicated “Other” page.
 */
package org.jackhuang.hmcl.community.ui;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jackhuang.hmcl.community.ui.about.AboutLMCLPage;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.main.FeedbackPage;
import org.jackhuang.hmcl.ui.main.HelpPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// “其他”页：与下载、设置并列的独立页面，左侧为 HMCL 风格二级菜单。
///
/// 分组：
/// - 管理：[AccountsPane]（账户内嵌）、[InstancesPane]（实例内嵌）、
///   [CommunitySettingsView]（社区设置内嵌）；
/// - 支持：帮助、反馈、关于 LMCL。
///
/// 账户/实例直接内嵌可操作列表，不提供“点击后跳转完整页”的临时入口；
/// 上游页面自带的 200 px 左侧导航在内嵌时隐藏，操作入口由本页或内嵌页
/// 顶部工具栏提供。
///
/// 线程：单例构造与页面切换均在 FX 线程。
@NotNullByDefault
public final class OtherPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {

    private static @Nullable OtherPage instance;

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(
            State.fromTitle(i18n("community.other.title")));

    private final TransitionPane transitionPane = new TransitionPane();
    private final TabHeader.Tab<AccountsPane> accountsTab = new TabHeader.Tab<>("communityAccountsPage");
    private final TabHeader.Tab<InstancesPane> instancesTab = new TabHeader.Tab<>("communityInstancesPage");
    private final TabHeader.Tab<CommunitySettingsView> communityTab = new TabHeader.Tab<>("communitySettingsPage");
    private final TabHeader.Tab<HelpPage> helpTab = new TabHeader.Tab<>("communityHelpPage");
    private final TabHeader.Tab<FeedbackPage> feedbackTab = new TabHeader.Tab<>("communityFeedbackPage");
    private final TabHeader.Tab<AboutLMCLPage> aboutTab = new TabHeader.Tab<>("communityAboutPage");
    private final TabHeader tab;

    private OtherPage() {
        accountsTab.setNodeSupplier(AccountsPane::new);
        instancesTab.setNodeSupplier(InstancesPane::new);
        communityTab.setNodeSupplier(CommunitySettingsView::new);
        helpTab.setNodeSupplier(HelpPage::new);
        feedbackTab.setNodeSupplier(FeedbackPage::new);
        aboutTab.setNodeSupplier(AboutLMCLPage::new);

        tab = new TabHeader(transitionPane, accountsTab, instancesTab, communityTab,
                helpTab, feedbackTab, aboutTab);
        tab.select(accountsTab);

        AdvancedListBox sideBar = new AdvancedListBox()
                .startCategory(i18n("community.other.category.manage").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, accountsTab, i18n("community.other.tab.accounts"), SVG.PERSON, SVG.PERSON)
                .addNavigationDrawerTab(tab, instancesTab, i18n("community.other.tab.instances"), SVG.GAMEPAD, SVG.GAMEPAD)
                .addNavigationDrawerTab(tab, communityTab, i18n("community.other.tab.community"), SVG.SETTINGS, SVG.SETTINGS_FILL)
                .startCategory(i18n("community.other.category.support").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, helpTab, i18n("community.other.tab.help"), SVG.HELP, SVG.HELP_FILL)
                .addNavigationDrawerTab(tab, feedbackTab, i18n("community.other.tab.feedback"), SVG.FEEDBACK, SVG.FEEDBACK_FILL)
                .addNavigationDrawerTab(tab, aboutTab, i18n("community.other.tab.about"), SVG.INFO, SVG.INFO_FILL);

        FXUtils.setLimitWidth(sideBar, 200);
        setLeft(sideBar);
        setCenter(transitionPane);
    }

    /// 获取单例；重复打开时复用同一实例。
    ///
    /// @return 页面
    @NotNull
    public static OtherPage getInstance() {
        FXUtils.checkFxUserThread();
        if (instance == null) {
            instance = new OtherPage();
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

    /// 选中社区设置 tab。
    public void showCommunitySettings() {
        tab.select(communityTab, false);
    }

    /// 选中帮助 tab。
    public void showHelp() {
        tab.select(helpTab, false);
    }

    /// 选中反馈 tab。
    public void showFeedback() {
        tab.select(feedbackTab, false);
    }

    /// 选中关于 tab。
    public void showAbout() {
        tab.select(aboutTab, false);
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
