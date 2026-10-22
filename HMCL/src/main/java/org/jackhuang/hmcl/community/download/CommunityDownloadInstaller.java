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
package org.jackhuang.hmcl.community.download;

import javafx.beans.InvalidationListener;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.DownloadProviderWrapper;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 把用户配置的下载源接入上游下载链路。
///
/// 做法是**替换** `DownloadProviders` 内部 `DownloadProviderWrapper` 的实现：
/// 候选顺序 = 主源 → 镜像；**不追加任何官方/内置源**；未配置时使用
/// [MissingDownloadProvider]，所有网络操作明确失败。
/// 上游的“下载源”设置仍然存在，但每次变更都会被本类重新覆盖为社区配置，
/// 从而保证“默认不内置下载源、以设置里的自定义源为准”。
///
/// 安装时机：[CommunityDownloadInstaller#install()] 必须在
/// `DownloadProviders.init()` 与 `SettingsManager` 初始化之后调用；侧栏构造时调用一次，
/// 首页挂载时再调用一次作为兜底（幂等）。
@NotNullByDefault
public final class CommunityDownloadInstaller {

    /// 是否已安装（幂等）。
    private static volatile boolean installed;

    /// 上游下载源设置变化时重新覆盖（注册顺序晚于上游，因此后执行）。
    private static final InvalidationListener ON_SETTINGS_CHANGED = observable -> apply();

    /// 本类只提供静态方法。
    private CommunityDownloadInstaller() {
    }

    /// 安装 provider 覆盖（幂等；失败只记日志，不影响启动）。
    public static synchronized void install() {
        if (installed) {
            return;
        }
        DownloadProvider current = DownloadProviders.getDownloadProvider();
        if (!(current instanceof DownloadProviderWrapper)) {
            LOG.warning("Unexpected download provider type: " + current.getClass().getName());
            return;
        }
        settings().versionListSourceProperty().addListener(ON_SETTINGS_CHANGED);
        settings().fileDownloadSourceProperty().addListener(ON_SETTINGS_CHANGED);
        installed = true;
        apply();
    }

    /// 按当前设置重建并应用 provider。
    public static void apply() {
        try {
            DownloadProvider current = DownloadProviders.getDownloadProvider();
            if (!(current instanceof DownloadProviderWrapper wrapper)) {
                return;
            }
            CommunityDownloadSources sources = CommunityDownloadSources.load();
            wrapper.setProvider(build(sources));
            CommunityDownloadGuard.refresh(!sources.isEmpty());
        } catch (Throwable e) {
            LOG.warning("Failed to apply community download sources", e);
        }
    }

    /// 构建候选链：只用用户配置的源（主源 → 镜像）；空配置返回 [MissingDownloadProvider]。
    ///
    /// @param sources 自定义下载源
    /// @return 自动候选 provider 或未配置 provider
    @NotNull
    static DownloadProvider build(@NotNull CommunityDownloadSources sources) {
        return CommunityDownloadProvider.of(sources);
    }
}
