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

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.jackhuang.hmcl.community.config.CommunitySettingsBundle;
import org.jackhuang.hmcl.community.config.SettingsExportService;
import org.jackhuang.hmcl.community.config.SettingsSectionExporter;
import org.jackhuang.hmcl.community.ui.CommunityStyles;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LinePane;
import org.jackhuang.hmcl.task.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// “其他 → 工具 → 设置导入导出”页。
///
/// 勾选要导出的设置类，导出 `.lmcl.json`；也可选择已有 `.lmcl.json` 打开预览。
/// 拖入文件由 [org.jackhuang.hmcl.community.config.CommunityConfigDnD] 处理。
@NotNullByDefault
public final class SettingsTransferPane extends VBox {

    private final Map<String, CheckBox> checks = new LinkedHashMap<>();
    private final Label status = new Label();

    /// 构造页面。
    public SettingsTransferPane() {
        FXUtils.checkFxUserThread();
        CommunityStyles.apply(this);
        setSpacing(10);
        setPadding(new Insets(10));
        setFillWidth(true);

        Label title = new Label(i18n("community.settings.transfer.title"));
        title.getStyleClass().add("title-label");

        Label hint = new Label(i18n("community.settings.transfer.hint"));
        hint.setWrapText(true);
        hint.getStyleClass().addAll("hint", "info");

        ComponentList list = new ComponentList();
        for (SettingsSectionExporter exporter : SettingsExportService.exporters()) {
            CheckBox check = new CheckBox(i18n(exporter.titleKey()));
            check.setSelected(exporter.id().equals("downloadSources")
                    || exporter.id().equals("appearance")
                    || exporter.id().equals("home"));
            checks.put(exporter.id(), check);

            LinePane row = new LinePane();
            row.setTitle(i18n(exporter.titleKey()));
            row.setRight(check);
            list.getContent().add(row);
        }

        JFXButton selectAll = FXUtils.newBorderButton(i18n("community.settings.transfer.select_all"));
        selectAll.setOnAction(event -> checks.values().forEach(check -> check.setSelected(true)));
        JFXButton selectNone = FXUtils.newBorderButton(i18n("community.settings.transfer.select_none"));
        selectNone.setOnAction(event -> checks.values().forEach(check -> check.setSelected(false)));

        JFXButton importButton = FXUtils.newBorderButton(i18n("community.settings.transfer.import"));
        importButton.setOnAction(event -> chooseImport());
        JFXButton exportButton = FXUtils.newBorderButton(i18n("community.settings.transfer.export"));
        exportButton.setOnAction(event -> chooseExport());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, selectAll, selectNone, spacer, importButton, exportButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        status.getStyleClass().add("subtitle-label");
        getChildren().setAll(title, hint, list, actions, status);
    }

    private void chooseImport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("community.settings.transfer.import"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                i18n("community.settings.transfer.file_filter"), "*" + CommunitySettingsBundle.FILE_SUFFIX));
        Window window = getScene() == null ? null : getScene().getWindow();
        java.io.File file = chooser.showOpenDialog(window);
        if (file != null) {
            SettingsImportDialog.open(file.toPath());
        }
    }

    private void chooseExport() {
        Set<String> selected = new LinkedHashSet<>();
        checks.forEach((id, check) -> {
            if (check.isSelected()) {
                selected.add(id);
            }
        });
        if (selected.isEmpty()) {
            status.setText(i18n("community.settings.transfer.nothing_selected"));
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("community.settings.transfer.export"));
        chooser.setInitialFileName(SettingsExportService.suggestedFileName());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                i18n("community.settings.transfer.file_filter"), "*" + CommunitySettingsBundle.FILE_SUFFIX));
        Window window = getScene() == null ? null : getScene().getWindow();
        java.io.File file = chooser.showSaveDialog(window);
        if (file == null) {
            return;
        }
        Path target = file.toPath();
        CommunitySettingsBundle bundle;
        try {
            bundle = SettingsExportService.export(selected);
        } catch (Throwable e) {
            LOG.warning("Failed to collect settings for export", e);
            status.setText(i18n("community.settings.transfer.export_failed"));
            return;
        }
        Schedulers.io().execute(() -> {
            try {
                SettingsExportService.write(target, bundle);
                Schedulers.javafx().execute(() -> {
                    status.setText(i18n("community.settings.transfer.export_done", target.getFileName().toString()));
                    Controllers.showToast(i18n("community.settings.transfer.export_done", target.getFileName().toString()));
                });
            } catch (Throwable e) {
                LOG.warning("Failed to write settings file " + target, e);
                Schedulers.javafx().execute(() -> status.setText(i18n("community.settings.transfer.export_failed")));
            }
        });
    }
}
