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
package org.jackhuang.hmcl.community.ui.crash;

import org.jackhuang.hmcl.community.ui.CommunityStyles;
import com.google.gson.JsonObject;
import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.community.crash.CrashArchiver;
import org.jackhuang.hmcl.community.crash.CrashEntry;
import org.jackhuang.hmcl.community.crash.CrashHistory;
import org.jackhuang.hmcl.community.crash.LaunchPhase;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 崩溃历史面板（步骤 09 §3.6）。
///
/// 无参构造，供 14 的 `crash-history` 卡片嵌入；`setCompact(true)` 时只渲染列表行。
/// 本类不弹通知、不阻塞 FX 线程：历史读取走 IO 线程，删除/导出走异步任务。
@NotNullByDefault
public final class CrashHistoryPane extends VBox {

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final VBox list = new VBox(4);
    private final Label title = new Label();
    private boolean compact;
    private boolean loaded;

    public CrashHistoryPane() {
        CommunityStyles.apply(this);
        getStyleClass().add("community-crash-history");
        setSpacing(6);
        setPadding(new Insets(4));
        title.getStyleClass().add("title-label");
        list.getStyleClass().add("community-crash-history-list");
        getChildren().addAll(title, list);
        refresh();
    }

    /// 紧凑模式：隐藏标题，只渲染列表（14 的卡片壳负责标题与空态）。
    ///
    /// @param compact 是否紧凑
    public void setCompact(boolean compact) {
        this.compact = compact;
        title.setManaged(!compact);
        title.setVisible(!compact);
    }

    /// 重新读取并渲染历史（异步）。
    public void refresh() {
        CompletableFuture.supplyAsync(CrashHistory::load, Schedulers.io())
                .thenAcceptAsync(this::render, Schedulers.javafx())
                .exceptionally(throwable -> {
                    LOG.warning("Failed to load crash history", throwable);
                    render(List.of());
                    return null;
                });
    }

    /// 把未读计数清零（由 14 的卡片在展开时调用）。
    public void markAllSeen() {
        CrashHistory.markAllSeen();
        updateTitle(CrashHistory.load().size());
    }

    private void render(List<CrashEntry> entries) {
        loaded = true;
        list.getChildren().clear();
        updateTitle(entries.size());

        if (entries.isEmpty()) {
            Label empty = new Label(i18n("community.crash.empty"));
            empty.getStyleClass().add("subtitle-label");
            empty.setWrapText(true);
            list.getChildren().add(empty);
            return;
        }

        for (CrashEntry entry : entries) {
            list.getChildren().add(createRow(entry));
        }
    }

    private void updateTitle(int count) {
        int unseen = CrashHistory.unseenCount();
        title.setText(unseen > 0
                ? i18n("community.crash.title") + " (" + count + ", " + i18n("community.crash.unseen", unseen) + ")"
                : i18n("community.crash.title") + " (" + count + ")");
    }

    private HBox createRow(CrashEntry entry) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 6, 4, 6));
        row.getStyleClass().add("md-list-cell");

        Label time = new Label(DISPLAY_TIME.format(entry.at().atZone(ZoneId.systemDefault())));
        time.getStyleClass().add("subtitle-label");
        time.setMinWidth(84);

        Label version = new Label(entry.versionId());
        version.setMaxWidth(180);

        Label reason = new Label(reasonText(entry));
        reason.getStyleClass().add("subtitle-label");
        reason.setWrapText(false);
        HBox.setHgrow(reason, Priority.ALWAYS);

        JFXButton details = new JFXButton(i18n("community.crash.action.details"));
        details.getStyleClass().add("dialog-accept");
        details.setOnAction(event -> showDetails(entry));

        row.getChildren().addAll(time, version, reason);
        if (entry.snapshotId() != null) {
            JFXButton rollback = new JFXButton(i18n("community.crash.action.rollback"));
            rollback.setOnAction(event -> confirmRollback(entry));
            row.getChildren().add(rollback);
        }
        row.getChildren().add(details);
        return row;
    }

    private String reasonText(CrashEntry entry) {
        String phase = i18n(entry.phase() == LaunchPhase.RUNTIME
                ? "community.crash.phase.runtime" : "community.crash.phase.startup");
        if (entry.reasonRule() != null) {
            return phase + " · " + i18n("game.crash.reason." + entry.reasonRule().toLowerCase(Locale.ROOT));
        }
        if (!entry.keywords().isEmpty()) {
            return phase + " · " + i18n("game.crash.reason.stacktrace");
        }
        return phase + " · " + i18n("community.crash.card.unknown_reason");
    }

    private void showDetails(CrashEntry entry) {
        Path archiveDir = CrashArchiver.archiveDir(entry);
        if (!Files.isDirectory(archiveDir)) {
            Controllers.dialog(i18n("community.crash.error.archive_missing"),
                    i18n("community.crash.detail.title"), MessageType.WARNING);
            return;
        }

        JsonObject meta = readMeta(archiveDir);
        VBox root = new VBox(10);
        root.setPadding(new Insets(16));
        root.setPrefWidth(560);
        root.getStyleClass().add("jfx-dialog-layout");

        Label header = new Label(i18n("community.crash.detail.title") + " · " + entry.archiveId());
        header.getStyleClass().add("title-label");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);
        ColumnConstraints keyColumn = new ColumnConstraints();
        keyColumn.setMinWidth(110);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(keyColumn, valueColumn);

        addRow(grid, 0, "community.crash.detail.created", metaString(meta, "createdAt"));
        addRow(grid, 1, "community.crash.detail.version", entry.versionId());
        addRow(grid, 2, "community.crash.detail.game_version", entry.gameVersion());
        addRow(grid, 3, "community.crash.detail.phase", i18n(entry.phase() == LaunchPhase.RUNTIME
                ? "community.crash.phase.runtime" : "community.crash.phase.startup"));
        addRow(grid, 4, "community.crash.detail.exit_type", metaString(meta, "exitType"));
        addRow(grid, 5, "community.crash.detail.alive", metaString(meta, "aliveSeconds") + "s");
        if (entry.reasonRule() != null) {
            addRow(grid, 6, "community.crash.detail.rules", entry.reasonRule());
        } else if (!entry.keywords().isEmpty()) {
            addRow(grid, 6, "community.crash.detail.keywords", String.join(", ", entry.keywords()));
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(240);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(8, 0, 0, 0));

        JFXButton openFolder = new JFXButton(i18n("community.crash.action.open_folder"));
        openFolder.setOnAction(event -> FXUtils.openFolder(archiveDir));

        JFXButton export = new JFXButton(i18n("community.crash.action.export_zip"));
        export.setOnAction(event -> exportArchive(entry, archiveDir));

        JFXButton rollback = new JFXButton(i18n("community.crash.action.rollback"));
        rollback.setVisible(entry.snapshotId() != null);
        rollback.setManaged(entry.snapshotId() != null);
        rollback.setOnAction(event -> confirmRollback(entry));

        JFXButton delete = new JFXButton(i18n("community.crash.action.delete"));
        delete.setOnAction(event -> confirmDelete(entry));

        JFXButton close = new JFXButton(i18n("community.crash.action.close"));
        close.getStyleClass().add("dialog-accept");
        close.setOnAction(event -> root.fireEvent(new DialogCloseEvent()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        actions.getChildren().addAll(openFolder, export, rollback, delete, spacer, close);

        Label warning = new Label(i18n("community.crash.export.warning"));
        warning.getStyleClass().add("subtitle-label");
        warning.setWrapText(true);

        root.getChildren().addAll(header, scroll, warning, actions);
        root.addEventHandler(DialogCloseEvent.CLOSE, event -> {
        });
        Controllers.dialog(root);
    }

    private void exportArchive(CrashEntry entry, Path archiveDir) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("community.crash.action.export_zip"));
        chooser.setInitialFileName(entry.archiveId() + ".zip");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Zip", "*.zip"));
        Path target = FileUtils.toPath(chooser.showSaveDialog(Controllers.getStage()));
        if (target == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                CrashArchiver.export(archiveDir, target);
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, Schedulers.io()).thenRunAsync(
                () -> Controllers.dialog(i18n("community.crash.export.done"),
                        i18n("community.crash.action.export_zip"), MessageType.SUCCESS),
                Schedulers.javafx()).exceptionally(throwable -> {
            LOG.warning("Failed to export crash archive", throwable);
            Controllers.dialog(i18n("community.crash.export.failed"),
                    i18n("community.crash.action.export_zip"), MessageType.ERROR);
            return null;
        });
    }

    private void confirmDelete(CrashEntry entry) {
        Controllers.confirm(i18n("community.crash.delete.confirm"), i18n("community.crash.action.delete"), () -> {
            boolean deleted = CrashHistory.delete(entry.archiveId());
            if (deleted) {
                Controllers.dialog(i18n("community.crash.delete.done"));
            }
            refresh();
        }, null);
    }

    private void confirmRollback(CrashEntry entry) {
        if (entry.snapshotId() == null) {
            return;
        }
        if (!Files.isDirectory(CrashArchiver.archiveDir(entry))) {
            Controllers.dialog(i18n("community.crash.error.archive_missing"));
            return;
        }
        Controllers.confirm(i18n("community.crash.rollback.confirm"), i18n("community.crash.action.rollback"), () -> {
            try {
                Class<?> service = Class.forName("org.jackhuang.hmcl.community.snapshot.SnapshotService");
                service.getMethod("restore", String.class, String.class)
                        .invoke(null, entry.versionId(), entry.snapshotId());
                Controllers.dialog(i18n("community.crash.rollback.started"));
            } catch (ClassNotFoundException e) {
                Controllers.dialog(i18n("community.crash.rollback.unavailable"));
            } catch (Throwable e) {
                LOG.warning("Failed to rollback snapshot", e);
                Controllers.dialog(i18n("community.crash.rollback.failed"));
            }
        }, null);
    }

    private JsonObject readMeta(Path archiveDir) {
        try {
            Path file = archiveDir.resolve("meta.json");
            if (Files.isRegularFile(file)) {
                JsonObject json = JsonUtils.GSON.fromJson(Files.readString(file), JsonObject.class);
                return json != null ? json : new JsonObject();
            }
        } catch (Throwable e) {
            LOG.warning("Failed to read crash meta", e);
        }
        return new JsonObject();
    }

    private String metaString(JsonObject meta, String key) {
        try {
            if (meta.has(key) && !meta.get(key).isJsonNull()) {
                return meta.get(key).getAsString();
            }
        } catch (Throwable ignored) {
        }
        return "-";
    }

    private void addRow(GridPane grid, int row, String key, String value) {
        Label keyLabel = new Label(i18n(key));
        keyLabel.getStyleClass().add("subtitle-label");
        Label valueLabel = new Label(value);
        valueLabel.setWrapText(true);
        grid.add(keyLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }

    /// 历史是否已完成首次加载（供卡片判断空态）。
    ///
    /// @return 是否已加载
    public boolean isLoaded() {
        return loaded;
    }
}
