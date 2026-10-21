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
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.dashboard.DashboardContext;
import org.jackhuang.hmcl.community.dashboard.DashboardWidget;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.snapshot.SnapshotInfo;
import org.jackhuang.hmcl.community.snapshot.SnapshotReason;
import org.jackhuang.hmcl.community.snapshot.SnapshotScope;
import org.jackhuang.hmcl.community.snapshot.SnapshotService;
import org.jackhuang.hmcl.community.snapshot.RestoreResult;
import org.jackhuang.hmcl.task.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 实例快照卡片：最近一次成功启动对应的快照、回滚与手动标记。
@NotNullByDefault
public final class SnapshotWidget extends DashboardWidget {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Label lastLabel = BuiltinWidgets.caption("");
    private final Label detailLabel = BuiltinWidgets.caption("");
    private final Label sharedLabel = BuiltinWidgets.caption("");
    private final Label statusLabel = BuiltinWidgets.caption("");
    private final JFXButton rollbackButton = BuiltinWidgets.borderButton(
            i18n("community.snapshot.rollback_last"), this::rollbackLast);
    private final JFXButton markButton = BuiltinWidgets.borderButton(
            i18n("community.snapshot.mark_current"), this::markCurrent);
    private final JFXButton manageButton = BuiltinWidgets.borderButton(
            i18n("community.snapshot.manage"), this::manage);
    private final ChangeListener<@Nullable String> selectionListener =
            (observable, oldValue, newValue) -> refreshAsync();

    private List<SnapshotInfo> snapshots = List.of();
    private boolean hasSelection;

    /// 构造快照卡。
    ///
    /// @param descriptor 组件描述
    /// @param context 组件上下文
    public SnapshotWidget(@NotNull DashboardWidgetDescriptor descriptor,
                          @NotNull DashboardContext context) {
        super(descriptor, context);
        sharedLabel.getStyleClass().add("community-snapshot-warning");
        statusLabel.getStyleClass().add("subtitle-label");
        setSpacing(4);
    }

    @Override
    @NotNull
    protected Node createContent() {
        HBox buttons = new HBox(8, rollbackButton, markButton, manageButton);
        VBox content = new VBox(4, lastLabel, detailLabel, sharedLabel, statusLabel, buttons);
        content.setMinWidth(0);
        HBox.setHgrow(detailLabel, Priority.ALWAYS);
        context.selectedInstanceId().addListener(selectionListener);
        onDispose(() -> context.selectedInstanceId().removeListener(selectionListener));
        updateButtons();
        return content;
    }

    @Override
    protected void refreshAsync() {
        String versionId = context.selectedInstanceId().getValue();
        if (versionId == null) {
            snapshots = List.of();
            hasSelection = false;
            lastLabel.setText(i18n("community.snapshot.none"));
            detailLabel.setText(i18n("community.snapshot.no_instance"));
            sharedLabel.setText("");
            updateButtons();
            return;
        }
        hasSelection = true;
        this.lastLabel.setText(i18n("community.snapshot.loading"));
        this.detailLabel.setText("");
        this.sharedLabel.setText("");
        loadAsync(() -> new View(SnapshotService.scopeOf(versionId), SnapshotService.list(versionId)),
                view -> {
                    snapshots = view.snapshots();
                    render(view.scope());
                });
    }

    /// 渲染快照摘要。
    ///
    /// @param scope 当前作用域
    private void render(@Nullable SnapshotScope scope) {
        SnapshotInfo latest = latestSuccess();
        if (latest == null) {
            lastLabel.setText(i18n("community.snapshot.none"));
            detailLabel.setText("");
        } else {
            lastLabel.setText(i18n("community.snapshot.last", TIME_FORMAT.format(latest.at())));
            detailLabel.setText(i18n("community.snapshot.detail",
                    latest.files(), formatSize(latest.bytes())));
        }
        sharedLabel.setText(scope != null && scope.shared()
                ? i18n("community.snapshot.shared_warning", scope.instanceIds().size())
                : "");
        if (latest != null && latest.manifestOnly()) {
            statusLabel.setText(i18n("community.snapshot.mode.manifest_only"));
        } else {
            statusLabel.setText("");
        }
        updateButtons();
    }

    /// 最近一次成功启动产生的快照；回滚只针对它，避免误回滚到 pre-restore/manual。
    ///
    /// @return 快照，或 `null`
    @Nullable
    private SnapshotInfo latestSuccess() {
        for (SnapshotInfo snapshot : snapshots) {
            if (snapshot.reason() == SnapshotReason.AUTO_START) {
                return snapshot;
            }
        }
        return null;
    }

    /// 更新按钮可用性。
    private void updateButtons() {
        boolean ready = hasSelection && latestSuccess() != null;
        rollbackButton.setDisable(!ready);
        markButton.setDisable(!hasSelection);
        manageButton.setDisable(!hasSelection);
    }

    /// 回滚到最近一次成功启动对应的快照。
    private void rollbackLast() {
        String versionId = context.selectedInstanceId().getValue();
        if (versionId == null) {
            return;
        }
        SnapshotInfo latest = latestSuccess();
        if (latest == null) {
            return;
        }
        org.jackhuang.hmcl.ui.Controllers.confirm(
                i18n("community.snapshot.restore_confirm", TIME_FORMAT.format(latest.at())),
                i18n("community.snapshot.title"),
                () -> doRestore(versionId, latest.id()),
                null);
    }

    /// 执行回滚并异步刷新。
    ///
    /// @param versionId 实例 id
    /// @param snapshotId 快照 id
    private void doRestore(String versionId, String snapshotId) {
        statusLabel.setText(i18n("community.snapshot.restoring"));
        SnapshotService.restore(versionId, snapshotId)
                .whenCompleteAsync((result, error) -> {
                    if (error != null) {
                        statusLabel.setText(i18n("community.snapshot.restore.failed",
                                String.valueOf(error.getMessage())));
                        return;
                    }
                    statusLabel.setText(describeRestore(result));
                    refreshAsync();
                }, Schedulers.javafx());
    }

    /// 手动标记当前状态为可用。
    private void markCurrent() {
        String versionId = context.selectedInstanceId().getValue();
        if (versionId == null) {
            return;
        }
        statusLabel.setText(i18n("community.snapshot.capturing"));
        SnapshotService.capture(versionId, SnapshotReason.MANUAL)
                .whenCompleteAsync((result, error) -> {
                    if (error != null) {
                        statusLabel.setText(i18n("community.snapshot.capture.failed",
                                String.valueOf(error.getMessage())));
                    } else if (result.skipped()) {
                        statusLabel.setText(i18n("community.snapshot.capture.skipped",
                                result.skipReason()));
                    } else {
                        statusLabel.setText(i18n("community.snapshot.capture.done"));
                    }
                    refreshAsync();
                }, Schedulers.javafx());
    }

    /// 打开管理对话框。
    private void manage() {
        String versionId = context.selectedInstanceId().getValue();
        if (versionId != null) {
            SnapshotManagerDialog.open(versionId, this::refreshAsync);
        }
    }

    /// 回滚结果的可读描述。
    ///
    /// @param result 回滚结果
    /// @return 文案
    @NotNull
    private String describeRestore(@NotNull RestoreResult result) {
        if (result.failed() > 0) {
            return i18n("community.snapshot.restore.done_partial",
                    result.restored(), result.deleted(), result.failed());
        }
        return i18n("community.snapshot.restore.done", result.restored(), result.deleted());
    }

    /// 格式化字节数。
    ///
    /// @param bytes 字节
    /// @return 可读文本
    @NotNull
    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format("%.1f MiB", bytes / 1024.0 / 1024.0);
        }
        if (bytes >= 1024L) {
            return String.format("%.1f KiB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    /// 一次读取的结果：作用域与快照列表。
    ///
    /// @param scope 作用域
    /// @param snapshots 快照
    private record View(@Nullable SnapshotScope scope, @NotNull List<SnapshotInfo> snapshots) {
    }
}
