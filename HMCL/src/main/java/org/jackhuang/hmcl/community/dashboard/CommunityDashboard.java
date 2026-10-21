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
package org.jackhuang.hmcl.community.dashboard;

import javafx.scene.Node;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.community.download.CommunityDownloadInstaller;
import org.jackhuang.hmcl.ui.main.MainPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 首页仪表盘的挂载入口。`MainPage` 只调用 [CommunityDashboard#mount(MainPage)]，
/// 失败/禁用/无组件时返回 `null`，此时首页与上游完全一致。
///
/// 调试开关：
/// - `-Dcommunity.dashboard.enabled=false` 关闭仪表盘；
/// - `-Dcommunity.dashboard.failMount=true` 强制挂载抛异常（验证容错路径）。
@NotNullByDefault
public final class CommunityDashboard {

    /// 是否启用仪表盘的属性名。
    public static final String ENABLED_PROPERTY = "community.dashboard.enabled";

    /// 强制挂载失败的属性名（仅调试）。
    public static final String FAIL_MOUNT_PROPERTY = "community.dashboard.failMount";

    /// 当前已挂载的面板。
    private static volatile @Nullable DashboardPane mounted;

    /// 本类只提供静态方法。
    private CommunityDashboard() {
    }

    /// 创建面板并返回给 `MainPage`；任何异常都返回 `null`（首页绝不因仪表盘白屏）。
    ///
    /// @param mainPage 首页
    /// @return 面板节点，或 `null` 表示不挂载
    @Nullable
    public static Node mount(@NotNull MainPage mainPage) {
        // 即使仪表盘被关闭，也要安装自定义下载源（设置面板仍可用）。
        try {
            CommunityDownloadInstaller.install();
        } catch (Throwable e) {
            LOG.warning("Failed to install community download sources", e);
        }

        if (!Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"))) {
            return null;
        }

        try {
            if (Boolean.getBoolean(FAIL_MOUNT_PROPERTY)) {
                throw new IllegalStateException("forced dashboard mount failure");
            }
            Path configPath = Metadata.HMCL_LOCAL_HOME
                    .resolve("config")
                    .resolve(DashboardConfig.FILE_NAME);
            DashboardPane pane = new DashboardPane(mainPage, configPath);
            if (!pane.hasEnabledWidgets()) {
                pane.dispose();
                return null;
            }
            mounted = pane;
            return pane;
        } catch (Throwable e) {
            LOG.error("Failed to mount community dashboard", e);
            return null;
        }
    }

    /// 返回当前已挂载的面板。
    ///
    /// @return 面板或 `null`
    @Nullable
    public static DashboardPane mounted() {
        return mounted;
    }

    /// 释放当前面板（退出/重载首页前调用）。
    public static void disposeAll() {
        DashboardPane pane = mounted;
        mounted = null;
        if (pane != null) {
            pane.dispose();
        }
    }
}
