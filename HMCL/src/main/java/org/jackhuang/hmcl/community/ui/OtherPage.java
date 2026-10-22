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
import org.jackhuang.hmcl.community.ui.transfer.SettingsTransferPane;
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
/// - 工具：内存优化、设置导入导出；
/// - 支持：帮助、反馈、关于 LMCL。
///
/// 账户与实例已迁到 [ManagementPage]；社区设置入口已移除，相关功能分散到
/// 下载设置、主页组件与各 tab 内。
///
/// 线程：单例构造与页面切换均在 FX 线程。
@NotNullByDefault
public final class OtherPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {

    private static @Nullable OtherPage instance;

    // 顶层页面由左侧图标栏导航，不再显示标题栏左上角的“返回主界面”按钮。
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(
            new State(i18n("community.other.title"), null, false, false, true));

    private final TransitionPane transitionPane = new TransitionPane();
    private final TabHeader.Tab<MemoryOptimizationView> memoryTab = new TabHeader.Tab<>("communityMemoryOptimizationPage");
    private final TabHeader.Tab<SettingsTransferPane> transferTab = new TabHeader.Tab<>("communitySettingsTransferPage");
    private final TabHeader.Tab<HelpPage> helpTab = new TabHeader.Tab<>("communityHelpPage");
    private final TabHeader.Tab<FeedbackPage> feedbackTab = new TabHeader.Tab<>("communityFeedbackPage");
    private final TabHeader.Tab<AboutLMCLPage> aboutTab = new TabHeader.Tab<>("communityAboutPage");
    private final TabHeader tab;

    private OtherPage() {
        memoryTab.setNodeSupplier(MemoryOptimizationView::new);
        transferTab.setNodeSupplier(SettingsTransferPane::new);
        helpTab.setNodeSupplier(HelpPage::new);
        feedbackTab.setNodeSupplier(FeedbackPage::new);
        aboutTab.setNodeSupplier(AboutLMCLPage::new);

        tab = new TabHeader(transitionPane, memoryTab, transferTab, helpTab, feedbackTab, aboutTab);
        tab.select(memoryTab);

        AdvancedListBox sideBar = new AdvancedListBox()
                .startCategory(i18n("community.other.category.tools").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, memoryTab, i18n("community.other.tab.memory"), SVG.TUNE, SVG.TUNE)
                .addNavigationDrawerTab(tab, transferTab, i18n("community.other.tab.transfer"), SVG.FILE_OPEN, SVG.FILE_OPEN)
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

    /// 选中内存优化 tab。
    public void showMemoryOptimization() {
        tab.select(memoryTab, false);
    }

    /// 选中设置导入导出 tab。
    public void showSettingsTransfer() {
        tab.select(transferTab, false);
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
