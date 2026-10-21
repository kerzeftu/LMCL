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

import org.jackhuang.hmcl.community.ui.CommunityStyles;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.snapshot.SnapshotDiff;
import org.jackhuang.hmcl.community.snapshot.SnapshotService;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 快照对比对话框：展示新增、缺失、变化与失效文件。
@NotNullByDefault
final class SnapshotDiffDialog {

    private SnapshotDiffDialog() {
    }

    /// 打开对比对话框。
    ///
    /// @param versionId 实例 id
    /// @param snapshotId 快照 id
    static void open(@NotNull String versionId, @NotNull String snapshotId) {
        VBox content = new VBox(8);
        Label status = BuiltinWidgets.caption(i18n("community.snapshot.comparing"));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(320);
        org.jackhuang.hmcl.ui.construct.DialogPane root = BuiltinWidgets.dialogRoot(
                i18n("community.snapshot.compare.title", snapshotId), new VBox(8, status, scroll), 680);
        CommunityStyles.apply(root);
        Controllers.dialog(root);

        SnapshotService.diff(versionId, snapshotId)
                .whenCompleteAsync((diff, error) -> {
                    content.getChildren().clear();
                    if (error != null) {
                        status.setText(i18n("community.snapshot.compare.failed",
                                String.valueOf(error.getMessage())));
                        return;
                    }
                    status.setText(i18n("community.snapshot.compare.summary",
                            diff.added().size(), diff.missing().size(),
                            diff.changed().size(), diff.invalid().size()));
                    content.getChildren().addAll(
                            section(i18n("community.snapshot.compare.added"), diff.added()),
                            section(i18n("community.snapshot.compare.missing"), diff.missing()),
                            section(i18n("community.snapshot.compare.changed"), diff.changed()),
                            section(i18n("community.snapshot.compare.invalid"), diff.invalid()));
                }, Schedulers.javafx());
    }

    /// 构造一个分类。
    private static VBox section(String title, List<String> paths) {
        Label header = new Label(title + " (" + paths.size() + ")");
        header.getStyleClass().add("title-label");
        Label body = BuiltinWidgets.body(paths.isEmpty()
                ? i18n("community.snapshot.compare.empty")
                : join(paths));
        return new VBox(2, header, body);
    }

    /// 最多列出 30 条，避免对话框无限增长。
    private static String join(List<String> paths) {
        int limit = Math.min(paths.size(), 30);
        StringBuilder builder = new StringBuilder(String.join("\n", paths.subList(0, limit)));
        if (paths.size() > limit) {
            builder.append('\n').append("… +").append(paths.size() - limit);
        }
        return builder.toString();
    }
}
