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

import javafx.stage.Window;
import org.jackhuang.hmcl.community.ui.CommunitySettingsPanel;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 在进入需要网络的下载入口前检查是否已配置下载源。
///
/// 缓存由 [CommunityDownloadInstaller#apply()] 在 IO 线程读取配置后刷新，
/// 避免在 JavaFX 线程上读文件。
@NotNullByDefault
public final class CommunityDownloadGuard {

    private static volatile boolean configured;

    private CommunityDownloadGuard() {
    }

    /// 由安装器在读取配置后刷新缓存。
    static void refresh(boolean value) {
        configured = value;
    }

    /// 当前是否已配置至少一个下载源（读缓存，不访问磁盘）。
    public static boolean isConfigured() {
        return configured;
    }

    /// 未配置时弹提示并返回 `false`；已配置返回 `true`。
    ///
    /// 必须在 JavaFX 线程调用。
    public static boolean requireConfigured(@Nullable Window owner) {
        if (configured) {
            // 进入下载入口时刷新测速（受 5 分钟 TTL 限制），让候选顺序自动跟随当前网络。
            CommunityDownloadRanker.refreshAsync(false);
            return true;
        }
        // 安装器可能在设置初始化完成前失败，或启动路径未经过侧栏；这里做一次兜底，
        // 保证“设置里保存的源”在下载入口处一定已应用到 DownloadProvider。
        try {
            CommunityDownloadInstaller.install();
        } catch (Throwable e) {
            org.jackhuang.hmcl.util.logging.Logger.LOG.warning(
                    "Failed to initialize community download sources", e);
        }
        if (configured) {
            return true;
        }

        MessageDialogPane dialog = new MessageDialogPane.Builder(
                i18n("community.download.not_configured"),
                i18n("community.download.not_configured.title"),
                MessageDialogPane.MessageType.WARNING)
                .addAction(i18n("community.download.not_configured.go_settings"),
                        () -> CommunitySettingsPanel.open(owner, "download"))
                .addCancel(i18n("button.cancel"), null)
                .build();
        Controllers.dialog(dialog);
        return false;
    }
}
