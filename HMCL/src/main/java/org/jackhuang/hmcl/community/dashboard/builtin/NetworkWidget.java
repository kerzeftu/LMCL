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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.dashboard.DashboardContext;
import org.jackhuang.hmcl.community.dashboard.DashboardWidget;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.download.CommunityDownloadSource;
import org.jackhuang.hmcl.community.download.CommunityDownloadSources;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.main.LauncherSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 首页网络卡：只读展示当前全部下载源，并提供配置入口。
///
/// 数据只来自 [CommunityDownloadSources]；不读取上游 `versionListSource` / `fileDownloadSource`，
/// 也不做任何测速或网络探测。每个已配置源都显示一行名称和一行完整 URL，数量如实显示。
@NotNullByDefault
public final class NetworkWidget extends DashboardWidget {

    /// 源数量行。
    private final Label countLabel = BuiltinWidgets.caption("");

    /// 逐源列表。
    private final VBox sourceList = new VBox(4);

    /// 空态标题。
    private final Label emptyLabel = BuiltinWidgets.caption(i18n("community.network.empty"));

    /// 空态提示。
    private final Label emptyHint = BuiltinWidgets.caption(i18n("community.network.empty.hint"));

    /// 空态容器。
    private VBox emptyBox;

    /// 配置按钮。
    private final JFXButton configureButton = BuiltinWidgets.borderButton(
            i18n("community.network.configure"), this::openSettings);

    /// 配置失败提示。
    private final Label errorLabel = BuiltinWidgets.caption(i18n("community.network.error"));

    /// 构造网络卡。
    ///
    /// @param descriptor 组件描述
    /// @param context 组件上下文
    public NetworkWidget(@NotNull DashboardWidgetDescriptor descriptor,
                         @NotNull DashboardContext context) {
        super(descriptor, context);
    }

    /// 创建卡片骨架。
    ///
    /// @return 内容节点
    @Override
    @NotNull
    protected Node createContent() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        emptyHint.getStyleClass().add("community-source-hint");
        emptyBox = new VBox(2, emptyLabel, emptyHint);
        emptyBox.getStyleClass().add("community-source-empty");
        emptyBox.setVisible(false);
        emptyBox.setManaged(false);

        HBox footer = BuiltinWidgets.cardFooter(configureButton);
        VBox body = new VBox(4, countLabel, sourceList, emptyBox, errorLabel);
        body.setFillWidth(true);
        return BuiltinWidgets.cardContent(body, footer);
    }

    /// 读取下载源配置。
    @Override
    protected void refreshAsync() {
        loadAsync(CommunityDownloadSources::load, this::apply);
    }

    /// 应用配置。
    ///
    /// @param sources 下载源集合
    private void apply(@NotNull CommunityDownloadSources sources) {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        sourceList.getChildren().clear();

        int count = sources.size();
        countLabel.setText(i18n("community.network.count", count, CommunityDownloadSources.MAX_SOURCES));

        boolean empty = sources.isEmpty();
        emptyBox.setVisible(empty);
        emptyBox.setManaged(empty);
        if (empty) {
            return;
        }

        for (int i = 0; i < count; i++) {
            sourceList.getChildren().add(sourceRow(i + 1, sources.sources().get(i)));
        }
    }

    /// 构建一个下载源行：第一行序号 + 名称，第二行完整 URL。
    ///
    /// @param index 从 1 开始的序号
    /// @param source 下载源
    /// @return 源行
    @NotNull
    private Node sourceRow(int index, @NotNull CommunityDownloadSource source) {
        String url = source.url();
        @Nullable String configuredName = source.name();
        String name = configuredName == null || configuredName.isBlank()
                ? CommunityDownloadSource.defaultName(url)
                : configuredName;

        Label nameLabel = BuiltinWidgets.caption(
                i18n("community.network.source.item", index, BuiltinWidgets.shorten(name, 32)));
        nameLabel.getStyleClass().add("community-source-name");
        nameLabel.setMinWidth(0);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setTooltip(new Tooltip(name));

        Label urlLabel = BuiltinWidgets.caption(url);
        urlLabel.getStyleClass().add("community-source-url");
        urlLabel.setWrapText(true);
        urlLabel.setMinWidth(0);
        urlLabel.setMaxWidth(Double.MAX_VALUE);
        urlLabel.setTooltip(new Tooltip(url));

        VBox row = new VBox(1, nameLabel, urlLabel);
        row.getStyleClass().add("community-source-row");
        row.setFillWidth(true);
        return row;
    }

    /// 打开设置页的“下载”tab。
    private void openSettings() {
        try {
            LauncherSettingsPage page = Controllers.getSettingsPage();
            page.showDownloadSettings();
            Controllers.navigate(page);
        } catch (Throwable e) {
            LOG.warning("Failed to open download settings", e);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }
}
