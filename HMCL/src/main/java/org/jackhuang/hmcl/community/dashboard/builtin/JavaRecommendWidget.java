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
package org.jackhuang.hmcl.community.dashboard.builtin;

import com.jfoenix.controls.JFXButton;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.dashboard.DashboardContext;
import org.jackhuang.hmcl.community.dashboard.DashboardWidget;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.java.JavaChoice;
import org.jackhuang.hmcl.community.java.JavaPreference;
import org.jackhuang.hmcl.community.java.JavaRecommendPane;
import org.jackhuang.hmcl.community.jvm.JvmPresetWizard;
import org.jackhuang.hmcl.download.java.disco.DiscoJavaDistribution;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.main.JavaManagementPage;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.ui.Controllers.dialog;
import static org.jackhuang.hmcl.ui.Controllers.navigate;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 首页 Java 卡：展示偏好发行版与平台，并提供推荐/管理入口。
///
/// 卡片本身不发网络请求；“推荐 Java”对话框里的 [JavaRecommendPane] 负责远端列表，
/// 「JVM 参数」页复用步骤 11 的 [JvmPresetWizard]。
@NotNullByDefault
public final class JavaRecommendWidget extends DashboardWidget {

    /// 偏好行。
    private final Label preferenceLabel = BuiltinWidgets.caption("");

    /// 平台行。
    private final Label platformLabel = BuiltinWidgets.caption("");

    /// 回退提示行。
    private final Label fallbackLabel = BuiltinWidgets.caption("");

    /// 推荐 Java 按钮。
    private final JFXButton recommendButton = BuiltinWidgets.borderButton(
            i18n("community.java.card.recommend"), this::openRecommendDialog);

    /// 管理 Java 按钮。
    private final JFXButton manageButton = BuiltinWidgets.borderButton(
            i18n("community.java.card.manage"), () -> navigate(new JavaManagementPage()));

    /// 构造 Java 卡。
    ///
    /// @param descriptor 组件描述
    /// @param context 组件上下文
    public JavaRecommendWidget(@NotNull DashboardWidgetDescriptor descriptor,
                               @NotNull DashboardContext context) {
        super(descriptor, context);
    }

    /// 创建卡片骨架。
    ///
    /// @return 内容节点
    @Override
    @NotNull
    protected Node createContent() {
        fallbackLabel.setVisible(false);
        fallbackLabel.setManaged(false);

        HBox actions = new HBox(8, recommendButton, manageButton);
        VBox box = new VBox(3, preferenceLabel, platformLabel, fallbackLabel, actions);
        box.setFillWidth(true);
        return box;
    }

    /// 读取偏好。
    @Override
    protected void refreshAsync() {
        loadAsync(JavaPreference::get, this::apply);
    }

    /// 应用偏好。
    ///
    /// @param preference 偏好
    private void apply(@NotNull JavaPreference.Preference preference) {
        preferenceLabel.setText(i18n("community.java.card.preference", choiceLabel(preference.distribution())));

        Platform platform = Platform.SYSTEM_PLATFORM;
        String os = platform.getOperatingSystem().getCheckedName();
        String arch = platform.getArchitecture().getDisplayName();
        platformLabel.setText(i18n("community.java.card.platform", os, arch));

        boolean unsupported = preference.distribution() != null
                && !preference.distribution().isSupport(platform);
        fallbackLabel.setVisible(unsupported);
        fallbackLabel.setManaged(unsupported);
        fallbackLabel.setText(i18n("community.java.card.fallback"));
    }

    /// 把发行版映射为展示文本。
    ///
    /// @param distribution 发行版或 `null`
    /// @return 展示文本
    @NotNull
    private static String choiceLabel(DiscoJavaDistribution distribution) {
        if (distribution == null) {
            return i18n("community.java.preference.any");
        }
        return i18n(JavaChoice.of(distribution).i18nKey());
    }

    /// 打开推荐 Java / JVM 参数对话框。
    private void openRecommendDialog() {
        JavaRecommendPane javaPane = new JavaRecommendPane();
        javaPane.setPrefHeight(440);

        JvmPresetWizard wizard = new JvmPresetWizard();
        HMCLGameRepository repository = context.repository().get();
        wizard.setRepository(repository);
        wizard.refresh();

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab javaTab = new Tab(i18n("community.java.title"), javaPane);
        Tab jvmTab = new Tab(i18n("community.jvm.title"), wizard);
        tabs.getTabs().addAll(javaTab, jvmTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        VBox body = new VBox(8, tabs);
        org.jackhuang.hmcl.ui.construct.DialogPane root = BuiltinWidgets.dialogRoot(i18n("community.java.card.dialog"), body, 760);
        JFXButton close = BuiltinWidgets.borderButton(i18n("button.ok"),
                () -> root.fireEvent(new DialogCloseEvent()));
        body.getChildren().add(BuiltinWidgets.dialogActions(close));
        dialog(root);
        javaPane.refresh();
    }
}
