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
/// 第一阶段只包含帮助、反馈、关于 LMCL 三项。账户、实例与社区设置需要
/// 先拆成可嵌入的 Pane 后再作为 tab 接入，本页不提供“点击后跳转完整页”
/// 的临时入口，避免上线用户已否定的导航形态。
///
/// 线程：单例构造与页面切换均在 FX 线程。
@NotNullByDefault
public final class OtherPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {

    private static @Nullable OtherPage instance;

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(
            State.fromTitle(i18n("community.other.title")));

    private final TransitionPane transitionPane = new TransitionPane();
    private final TabHeader.Tab<HelpPage> helpTab = new TabHeader.Tab<>("communityHelpPage");
    private final TabHeader.Tab<FeedbackPage> feedbackTab = new TabHeader.Tab<>("communityFeedbackPage");
    private final TabHeader.Tab<AboutLMCLPage> aboutTab = new TabHeader.Tab<>("communityAboutPage");
    private final TabHeader tab;

    private OtherPage() {
        helpTab.setNodeSupplier(HelpPage::new);
        feedbackTab.setNodeSupplier(FeedbackPage::new);
        aboutTab.setNodeSupplier(AboutLMCLPage::new);

        tab = new TabHeader(transitionPane, helpTab, feedbackTab, aboutTab);
        tab.select(aboutTab);

        AdvancedListBox sideBar = new AdvancedListBox()
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
