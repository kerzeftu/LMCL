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

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 首页网络卡：只读展示当前下载源配置，并提供配置入口。
///
/// 数据只来自 [CommunityDownloadSources]；不读取上游 `versionListSource` / `fileDownloadSource`，
/// 也不做任何测速或网络探测。
@NotNullByDefault
public final class NetworkWidget extends DashboardWidget {

    /// 源行。
    private final Label sourceLabel = BuiltinWidgets.caption("");

    /// 源数量行。
    private final Label countLabel = BuiltinWidgets.caption("");

    /// 零内置说明行（不表示官方兜底）。
    private final Label fallbackLabel = BuiltinWidgets.caption(i18n("community.network.fallback"));

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

        HBox footer = new HBox(8, fallbackLabel, BuiltinWidgets.spacer(), configureButton);
        VBox box = new VBox(3, sourceLabel, countLabel, errorLabel, footer);
        box.setFillWidth(true);
        return box;
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

        if (sources.isEmpty()) {
            sourceLabel.setText(i18n("community.network.source.none"));
        } else {
            CommunityDownloadSource first = sources.sources().get(0);
            sourceLabel.setText(i18n("community.network.source",
                    BuiltinWidgets.shorten(first.name(), 16),
                    CommunityDownloadSource.defaultName(first.url())));
        }
        countLabel.setText(i18n("community.network.sources",
                sources.size(), CommunityDownloadSources.MAX_SOURCES));
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
