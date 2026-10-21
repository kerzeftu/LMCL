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

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.snapshot.RestoreResult;
import org.jackhuang.hmcl.community.snapshot.SnapshotInfo;
import org.jackhuang.hmcl.community.snapshot.SnapshotService;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 快照管理对话框：列出快照、对比、回滚、删除。
@NotNullByDefault
final class SnapshotManagerDialog {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private SnapshotManagerDialog() {
    }

    /// 打开管理对话框。
    ///
    /// @param versionId 实例 id
    /// @param onChange 数据变化后的回调
    static void open(@NotNull String versionId, @NotNull Runnable onChange) {
        VBox listBox = new VBox(4);
        Label status = BuiltinWidgets.caption("");
        ScrollPane scroll = new ScrollPane(listBox);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);
        scroll.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox content = new VBox(8, status, scroll);
        VBox root = BuiltinWidgets.dialogRoot(
                i18n("community.snapshot.dialog.title", versionId), content, 720);
        load(versionId, listBox, status, onChange);
        Controllers.dialog(root);
    }

    /// 异步读取并填充列表。
    private static void load(String versionId, VBox listBox, Label status, Runnable onChange) {
        status.setText(i18n("community.snapshot.loading"));
        CompletableFuture.supplyAsync(() -> SnapshotService.list(versionId), Schedulers.io())
                .whenCompleteAsync((snapshots, error) -> {
                    listBox.getChildren().clear();
                    if (error != null) {
                        status.setText(i18n("community.snapshot.load_failed",
                                String.valueOf(error.getMessage())));
                        return;
                    }
                    status.setText(snapshots.isEmpty()
                            ? i18n("community.snapshot.none")
                            : i18n("community.snapshot.dialog.count", snapshots.size()));
                    for (SnapshotInfo info : snapshots) {
                        listBox.getChildren().add(row(versionId, info, status, onChange, listBox));
                    }
                }, Schedulers.javafx());
    }

    /// 构造一行快照。
    private static HBox row(String versionId, SnapshotInfo info, Label status,
                            Runnable onChange, VBox listBox) {
        Label time = new Label(TIME_FORMAT.format(info.at()));
        time.setMinWidth(132);
        Label reason = BuiltinWidgets.caption(i18n(
                "community.snapshot.reason." + info.reason().jsonName().replace('-', '_')));
        reason.setMinWidth(90);
        Label detail = BuiltinWidgets.caption(i18n("community.snapshot.detail",
                info.files(), formatSize(info.bytes())));
        HBox.setHgrow(detail, Priority.ALWAYS);
        Label mode = BuiltinWidgets.caption(info.manifestOnly()
                ? i18n("community.snapshot.mode.manifest_only") : "");

        HBox row = new HBox(8,
                time, reason, detail, mode,
                BuiltinWidgets.borderButton(i18n("community.snapshot.compare"),
                        () -> SnapshotDiffDialog.open(versionId, info.id())),
                BuiltinWidgets.borderButton(i18n("community.snapshot.rollback"),
                        () -> restore(versionId, info, status, onChange, listBox)),
                BuiltinWidgets.borderButton(i18n("community.snapshot.delete"),
                        () -> delete(versionId, info, status, onChange, listBox)));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /// 回滚。
    private static void restore(String versionId, SnapshotInfo info, Label status,
                                Runnable onChange, VBox listBox) {
        Controllers.confirm(i18n("community.snapshot.restore_confirm", TIME_FORMAT.format(info.at())),
                i18n("community.snapshot.title"),
                () -> {
                    status.setText(i18n("community.snapshot.restoring"));
                    SnapshotService.restore(versionId, info.id())
                            .whenCompleteAsync((result, error) -> {
                                status.setText(describeRestore(result, error));
                                load(versionId, listBox, status, onChange);
                                onChange.run();
                            }, Schedulers.javafx());
                }, null);
    }

    /// 删除。
    private static void delete(String versionId, SnapshotInfo info, Label status,
                               Runnable onChange, VBox listBox) {
        Controllers.confirm(i18n("community.snapshot.delete_confirm", TIME_FORMAT.format(info.at())),
                i18n("community.snapshot.title"),
                () -> SnapshotService.delete(info.id())
                        .whenCompleteAsync((ignored, error) -> {
                            status.setText(error == null
                                    ? i18n("community.snapshot.delete.done")
                                    : i18n("community.snapshot.delete.failed",
                                    String.valueOf(error.getMessage())));
                            load(versionId, listBox, status, onChange);
                            onChange.run();
                        }, Schedulers.javafx()),
                null);
    }

    /// 回滚结果描述。
    private static String describeRestore(RestoreResult result, Throwable error) {
        if (error != null) {
            return i18n("community.snapshot.restore.failed", String.valueOf(error.getMessage()));
        }
        if (result.failed() > 0) {
            return i18n("community.snapshot.restore.done_partial",
                    result.restored(), result.deleted(), result.failed());
        }
        return i18n("community.snapshot.restore.done", result.restored(), result.deleted());
    }

    /// 格式化大小。
    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format("%.1f MiB", bytes / 1024.0 / 1024.0);
        }
        if (bytes >= 1024L) {
            return String.format("%.1f KiB", bytes / 1024.0);
        }
        return bytes + " B";
    }
}
