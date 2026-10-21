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
package org.jackhuang.hmcl.community.update;

import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// “发现新版本”提示弹窗。
///
/// **只提示、不下载、不替换文件**：按钮只有 [前往下载]（打开浏览器）与 [稍后]（记录
/// `updateSkippedVersion`，同一版本不再提示）。这是步骤 05 定下的产品约束，
/// 任何调用方都不得在此加入下载逻辑。
@NotNullByDefault
public final class CommunityUpdateDialog {

    /// 发布说明最多展示的行数，超出部分折叠为省略号。
    private static final int MAX_NOTES_LINES = 3;

    /// 本类只提供静态方法。
    private CommunityUpdateDialog() {
    }

    /// 弹出更新提示。
    ///
    /// @param info 新版本信息
    public static void show(@NotNull CommunityUpdateNotifier.UpdateInfo info) {
        String notes = trimNotes(info.notes());
        String body = i18n("community.update.body", info.version());
        if (!notes.isEmpty()) {
            body = body + "\n\n" + notes;
        }

        MessageDialogPane pane = new MessageDialogPane.Builder(
                body,
                i18n("community.update.title"),
                MessageDialogPane.MessageType.INFO)
                .addAction(i18n("community.update.download"), () -> FXUtils.openLink(info.releaseUrl()))
                .addCancel(i18n("community.update.later"), () -> {
                    try {
                        CommunitySettings.get().setUpdateSkippedVersion(info.version());
                    } catch (Throwable ignored) {
                        // 记录失败只影响“下次是否再提示”，不允许影响关闭弹窗。
                    }
                })
                .build();
        Controllers.dialog(pane);
    }

    /// 判断该版本是否已被用户选择“稍后”（同版本不再自动提示）。
    ///
    /// @param version 版本号
    /// @return 已跳过返回 `true`
    public static boolean isSkipped(@NotNull String version) {
        try {
            return version.equals(CommunitySettings.get().updateSkippedVersion());
        } catch (Throwable e) {
            return false;
        }
    }

    /// 截断发布说明：最多 [MAX_NOTES_LINES] 行、总长 [CommunityUpdateNotifier] 已限制。
    ///
    /// @param notes 原始说明
    /// @return 截断后的文本（可能为空串）
    @NotNull
    private static String trimNotes(@NotNull String notes) {
        String normalized = notes.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalized.isEmpty()) {
            return "";
        }
        String[] lines = normalized.split("\n", -1);
        if (lines.length <= MAX_NOTES_LINES) {
            return normalized;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < MAX_NOTES_LINES; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        builder.append("…");
        return builder.toString();
    }
}
