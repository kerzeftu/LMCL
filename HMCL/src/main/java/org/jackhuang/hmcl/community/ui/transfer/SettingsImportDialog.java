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
package org.jackhuang.hmcl.community.ui.transfer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.config.CommunitySettingsBundle;
import org.jackhuang.hmcl.community.config.SettingsChange;
import org.jackhuang.hmcl.community.config.SettingsExportService;
import org.jackhuang.hmcl.community.config.SettingsImportService;
import org.jackhuang.hmcl.community.config.section.DownloadSourcesSectionExporter;
import org.jackhuang.hmcl.community.download.CommunityDownloadSettingsPane;
import org.jackhuang.hmcl.community.ui.CommunityStyles;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.ComponentSublist;
import org.jackhuang.hmcl.ui.construct.DialogPane;
import org.jackhuang.hmcl.task.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// `.lmcl.json` 导入预览对话框：列出变更，用户点“应用”后才写盘。
@NotNullByDefault
public final class SettingsImportDialog extends DialogPane {

    private final CommunitySettingsBundle bundle;
    private final SettingsImportService.ImportPreview preview;

    private SettingsImportDialog(@NotNull CommunitySettingsBundle bundle,
                                 @NotNull SettingsImportService.ImportPreview preview) {
        this.bundle = bundle;
        this.preview = preview;
        CommunityStyles.apply(this);

        setTitle(i18n("community.settings.transfer.import.title"));
        setPrefWidth(680);

        VBox content = new VBox(10);
        content.setPadding(new Insets(4, 0, 0, 0));

        Label summary = new Label(i18n("community.settings.transfer.import.summary",
                bundle.product(), bundle.productVersion(), bundle.sections().size(), preview.changes().size()));
        summary.setWrapText(true);
        summary.getStyleClass().add("subtitle-label");
        content.getChildren().add(summary);

        if (!preview.warnings().isEmpty()) {
            Label warning = new Label(String.join("\n", preview.warnings()));
            warning.setWrapText(true);
            warning.getStyleClass().addAll("hint", "warn");
            content.getChildren().add(warning);
        }
        if (!preview.errors().isEmpty()) {
            Label error = new Label(String.join("\n", preview.errors()));
            error.setWrapText(true);
            error.getStyleClass().addAll("hint", "error");
            content.getChildren().add(error);
        }

        if (preview.changes().isEmpty()) {
            Label empty = new Label(i18n("community.settings.transfer.import.no_changes"));
            empty.getStyleClass().add("subtitle-label");
            content.getChildren().add(empty);
        } else {
            Map<String, List<SettingsChange>> grouped = new LinkedHashMap<>();
            for (SettingsChange change : preview.changes()) {
                grouped.computeIfAbsent(change.section(), key -> new ArrayList<>()).add(change);
            }

            ComponentList list = new ComponentList();
            for (Map.Entry<String, List<SettingsChange>> entry : grouped.entrySet()) {
                ComponentSublist group = new ComponentSublist();
                group.setTitle(sectionTitle(entry.getKey()) + " (" + entry.getValue().size() + ")");
                for (SettingsChange change : entry.getValue()) {
                    group.getContent().add(buildChangeRow(change));
                }
                list.getContent().add(group);
            }
            content.getChildren().add(list);
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(380);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setBody(scroll);
        setValid(preview.errors().isEmpty());
    }

    private static @NotNull String sectionTitle(@NotNull String section) {
        var exporter = SettingsExportService.find(section);
        return exporter == null ? section : i18n(exporter.titleKey());
    }

    private static @NotNull StackPane buildChangeRow(@NotNull SettingsChange change) {
        String titleText = change.label() != null ? change.label() : change.key();
        Label title = new Label(titleText);
        title.getStyleClass().add("title-label");
        title.setMinWidth(0);
        title.setMaxWidth(140);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setTooltip(new Tooltip(titleText));

        String newValue = change.newValue() == null
                ? i18n("community.settings.transfer.value.absent")
                : change.newValue();
        VBox valueBox = new VBox(2);
        valueBox.setAlignment(Pos.CENTER_LEFT);
        valueBox.setMinWidth(0);
        valueBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(valueBox, Priority.ALWAYS);
        if (newValue.indexOf('\n') >= 0) {
            // 已格式化过的变化摘要（如下载源、主页布局）：逐行展示，不再拼 JSON 原文。
            for (String line : newValue.split("\\n", -1)) {
                valueBox.getChildren().add(createValueLabel(line));
            }
        } else {
            String oldValue = change.oldValue() == null
                    ? i18n("community.settings.transfer.value.absent")
                    : change.oldValue();
            valueBox.getChildren().add(createValueLabel(oldValue + "  →  " + newValue));
        }

        HBox line = new HBox(12, title, valueBox);
        line.getStyleClass().add("line-component-container");
        // 外层 ComponentList 的 options-list-item 已有 16px 左内边距；这里用 48px，
        // 使可见左边距从 32px 增至 64px（原来的两倍）。
        line.setPadding(new Insets(10, 16, 10, 48));
        line.setAlignment(Pos.CENTER_LEFT);

        StackPane row = new StackPane(line);
        row.getStyleClass().addAll("line-pane", "line-component");
        row.setMinHeight(48);
        return row;
    }

    private static @NotNull Label createValueLabel(@NotNull String text) {
        Label label = new Label(text);
        label.getStyleClass().add("subtitle-label");
        label.setWrapText(false);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setTooltip(new Tooltip(text));
        return label;
    }

    @Override
    protected void onAccept() {
        setLoading();
        SettingsImportService.ImportResult result = SettingsImportService.apply(bundle);
        if (!result.success()) {
            onFailure(i18n("community.settings.transfer.import.failed", result.failures().size()));
            return;
        }
        if (result.applied().contains(DownloadSourcesSectionExporter.ID)) {
            CommunityDownloadSettingsPane.refreshVisible();
        }
        Controllers.showToast(i18n("community.settings.transfer.import.done", result.applied().size()));
        onSuccess();
    }

    /// 从文件异步读取并打开预览；任何失败都弹提示，不抛出。
    ///
    /// @param path `.lmcl.json` 路径
    public static void open(@NotNull Path path) {
        Schedulers.io().execute(() -> {
            try {
                CommunitySettingsBundle bundle = CommunitySettingsBundle.read(path);
                SettingsImportService.ImportPreview preview = SettingsImportService.preview(bundle);
                Schedulers.javafx().execute(() -> Controllers.dialog(new SettingsImportDialog(bundle, preview)));
            } catch (Throwable e) {
                LOG.warning("Failed to read settings file " + path, e);
                Schedulers.javafx().execute(() -> Controllers.dialog(
                        i18n("community.settings.transfer.import.read_failed", e.getMessage()),
                        i18n("community.settings.transfer.import.title"),
                        org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType.ERROR));
            }
        });
    }
}
