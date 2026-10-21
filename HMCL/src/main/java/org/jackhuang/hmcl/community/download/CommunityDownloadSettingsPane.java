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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LinePane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.Controllers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 下载源设置页：主源 1 个 + 镜像源最多 5 个。
///
/// 交互约定：
/// - 地址留空表示“不配置”；主源留空即只使用官方源；
/// - 每行可单独测试连通性（请求 `/mc/game/version_manifest.json`）；
/// - 保存后立即写 `community-settings.json` 并重建下载候选链，无需重启。
@NotNullByDefault
public final class CommunityDownloadSettingsPane extends VBox {

    /// 地址输入框宽度。
    private static final double FIELD_WIDTH = 240;

    /// 主源行。
    private final LinePane primaryRow = new LinePane();

    /// 主源地址输入框。
    private final JFXTextField primaryField = new JFXTextField();

    /// 镜像源卡片。
    private final ComponentList mirrorsCard = new ComponentList();

    /// 已添加的镜像行。
    private final List<MirrorRow> mirrors = new ArrayList<>();

    /// 镜像计数标签。
    private final Label mirrorCount = new Label();

    /// 添加镜像按钮。
    private final JFXButton addMirrorButton = FXUtils.newBorderButton(i18n("community.settings.download.add"));

    /// 保存按钮。
    private final JFXButton saveButton = FXUtils.newBorderButton(i18n("community.settings.download.save"));

    /// 恢复默认按钮。
    private final JFXButton resetButton = FXUtils.newBorderButton(i18n("community.settings.download.reset"));

    /// 校验/保存结果提示。
    private final Label message = new Label();

    /// 已知地址 → 显示名，用于保存时保留用户已有名称。
    private final Map<String, String> knownNames = new HashMap<>();

    /// 构造设置页并从磁盘加载当前配置。
    public CommunityDownloadSettingsPane() {
        setSpacing(10);
        setPadding(new Insets(10));
        setFillWidth(true);

        Label hint = new Label(i18n("community.settings.download.hint"));
        hint.setWrapText(true);
        hint.getStyleClass().addAll("hint", "info");

        primaryField.setPromptText(i18n("community.settings.download.url.prompt"));
        FXUtils.setLimitWidth(primaryField, FIELD_WIDTH);
        HBox primaryRight = new HBox(8, primaryField,
                createTestButton(() -> CommunityDownloadSource.normalizeUrl(primaryField.getText()),
                        primaryRow));
        primaryRight.setAlignment(Pos.CENTER_RIGHT);

        primaryRow.setTitle(i18n("community.settings.download.primary"));
        primaryRow.setSubtitle(i18n("community.settings.download.primary.hint"));
        primaryRow.setRight(primaryRight);

        ComponentList primaryCard = new ComponentList();
        primaryCard.getStyleClass().add("card-non-transparent");
        primaryCard.getContent().setAll(primaryRow);

        Label mirrorsTitle = new Label(i18n("community.settings.download.mirror"));
        mirrorsTitle.getStyleClass().add("title-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox mirrorsHeader = new HBox(8, mirrorsTitle, spacer, mirrorCount);
        mirrorsHeader.setAlignment(Pos.CENTER_LEFT);
        mirrorsHeader.setPadding(new Insets(4, 0, 0, 10));

        mirrorsCard.getStyleClass().add("card-non-transparent");

        addMirrorButton.setOnAction(e -> {
            addMirrorRow(null);
            refreshMirrorRows();
        });

        HBox mirrorActions = new HBox(8, addMirrorButton);
        mirrorActions.setPadding(new Insets(0, 0, 0, 10));

        saveButton.setOnAction(e -> save());
        resetButton.setOnAction(e -> confirmReset());

        Region actionsSpacer = new Region();
        HBox.setHgrow(actionsSpacer, Priority.ALWAYS);
        HBox actions = new HBox(8, actionsSpacer, resetButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        message.setWrapText(true);
        message.setManaged(false);
        message.setVisible(false);

        getChildren().setAll(hint, primaryCard, mirrorsHeader, mirrorsCard, mirrorActions, actions, message);

        loadFromDisk();
    }

    /// 从磁盘读取配置并填充 UI。
    private void loadFromDisk() {
        CommunityDownloadSources sources = CommunityDownloadSources.load();
        for (CommunityDownloadSource source : sources.orderedSources()) {
            knownNames.put(source.url(), source.name());
        }
        if (sources.primary() != null) {
            primaryField.setText(sources.primary().url());
        }
        for (CommunityDownloadSource mirror : sources.mirrors()) {
            addMirrorRow(mirror.url());
        }
        refreshMirrorRows();
    }

    /// 新增一个镜像行。
    ///
    /// @param url 初始地址，或 `null` 表示空行
    private void addMirrorRow(@Nullable String url) {
        MirrorRow row = new MirrorRow();
        if (url != null) {
            row.urlField.setText(url);
        }
        mirrors.add(row);
    }

    /// 重建镜像卡片内容与计数。
    private void refreshMirrorRows() {
        List<Node> nodes = new ArrayList<>();
        if (mirrors.isEmpty()) {
            LinePane empty = new LinePane();
            empty.setTitle(i18n("community.settings.download.mirror.empty"));
            empty.setSubtitle(i18n("community.settings.download.mirror.empty.hint"));
            nodes.add(empty);
        } else {
            for (int i = 0; i < mirrors.size(); i++) {
                MirrorRow row = mirrors.get(i);
                row.pane.setTitle(i18n("community.settings.download.mirror") + " " + (i + 1));
                nodes.add(row.pane);
            }
        }
        mirrorsCard.getContent().setAll(nodes);
        mirrorCount.setText(i18n("community.settings.download.count", mirrors.size(),
                CommunityDownloadSources.MAX_MIRRORS));
        addMirrorButton.setDisable(mirrors.size() >= CommunityDownloadSources.MAX_MIRRORS);
    }

    /// 创建“测试”按钮。
    ///
    /// @param urlSupplier 地址来源
    /// @param row 状态显示行
    /// @return 按钮
    @NotNull
    private JFXButton createTestButton(@NotNull Supplier<String> urlSupplier,
                                       @NotNull LinePane row) {
        JFXButton button = FXUtils.newBorderButton(i18n("community.settings.download.test"));
        button.setOnAction(e -> {
            String url = CommunityDownloadSource.normalizeUrl(urlSupplier.get());
            if (!CommunityDownloadSource.isValidUrl(url)) {
                row.setSubtitle(i18n("community.settings.download.error.invalid", url));
                return;
            }
            button.setDisable(true);
            row.setSubtitle(i18n("community.settings.download.testing"));
            DownloadSourceTester.test(url).whenComplete((ok, error) ->
                    Schedulers.javafx().execute(() -> {
                        button.setDisable(false);
                        boolean success = error == null && Boolean.TRUE.equals(ok);
                        row.setSubtitle(i18n(success
                                ? "community.settings.download.test.ok"
                                : "community.settings.download.test.fail"));
                    }));
        });
        return button;
    }

    /// 校验并保存当前配置。
    private void save() {
        String primaryUrl = CommunityDownloadSource.normalizeUrl(primaryField.getText());
        if (!primaryUrl.isEmpty() && !CommunityDownloadSource.isValidUrl(primaryUrl)) {
            showMessage(i18n("community.settings.download.error.invalid", primaryUrl), true);
            return;
        }

        List<String> urls = new ArrayList<>();
        if (!primaryUrl.isEmpty()) {
            urls.add(primaryUrl);
        }
        List<MirrorRow> nonEmptyMirrors = new ArrayList<>();
        for (MirrorRow row : mirrors) {
            String url = CommunityDownloadSource.normalizeUrl(row.urlField.getText());
            if (url.isEmpty()) {
                continue;
            }
            if (!CommunityDownloadSource.isValidUrl(url)) {
                showMessage(i18n("community.settings.download.error.invalid", url), true);
                return;
            }
            urls.add(url);
            nonEmptyMirrors.add(row);
        }
        if (nonEmptyMirrors.size() > CommunityDownloadSources.MAX_MIRRORS) {
            showMessage(i18n("community.settings.download.error.too_many",
                    CommunityDownloadSources.MAX_MIRRORS), true);
            return;
        }
        String duplicate = CommunityDownloadSources.findDuplicate(urls);
        if (duplicate != null) {
            showMessage(i18n("community.settings.download.error.duplicate", duplicate), true);
            return;
        }

        CommunityDownloadSource primary = primaryUrl.isEmpty() ? null : toSource(primaryUrl);
        List<CommunityDownloadSource> mirrorSources = new ArrayList<>();
        for (MirrorRow row : nonEmptyMirrors) {
            mirrorSources.add(toSource(CommunityDownloadSource.normalizeUrl(row.urlField.getText())));
        }
        CommunityDownloadSources sources = new CommunityDownloadSources(primary, mirrorSources);

        setBusy(true);
        Schedulers.io().execute(() -> {
            try {
                sources.save();
                CommunityDownloadInstaller.apply();
                Schedulers.javafx().execute(() -> {
                    setBusy(false);
                    showMessage(i18n("community.settings.download.saved"), false);
                });
            } catch (Throwable e) {
                LOG.warning("Failed to save community download sources", e);
                Schedulers.javafx().execute(() -> {
                    setBusy(false);
                    showMessage(i18n("community.settings.download.save_failed"), true);
                });
            }
        });
    }

    /// 按地址构造下载源，尽量复用已有显示名。
    ///
    /// @param url 规范化地址
    /// @return 下载源
    @NotNull
    private CommunityDownloadSource toSource(@NotNull String url) {
        return CommunityDownloadSource.of(knownNames.getOrDefault(url, ""), url);
    }

    /// 确认后恢复默认（仅官方源）。
    private void confirmReset() {
        MessageDialogPane pane = new MessageDialogPane.Builder(
                i18n("community.settings.download.reset.confirm"),
                i18n("community.settings.download.reset"),
                MessageDialogPane.MessageType.QUESTION)
                .yesOrNo(() -> {
                    primaryField.setText("");
                    mirrors.clear();
                    knownNames.clear();
                    refreshMirrorRows();

                    setBusy(true);
                    Schedulers.io().execute(() -> {
                        try {
                            CommunityDownloadSources.empty().save();
                            CommunityDownloadInstaller.apply();
                            Schedulers.javafx().execute(() -> {
                                setBusy(false);
                                showMessage(i18n("community.settings.download.reset.done"), false);
                            });
                        } catch (Throwable e) {
                            LOG.warning("Failed to reset community download sources", e);
                            Schedulers.javafx().execute(() -> {
                                setBusy(false);
                                showMessage(i18n("community.settings.download.save_failed"), true);
                            });
                        }
                    });
                }, null)
                .build();
        Controllers.dialog(pane);
    }

    /// 保存/重置期间禁用操作。
    ///
    /// @param busy 是否忙碌
    private void setBusy(boolean busy) {
        saveButton.setDisable(busy);
        resetButton.setDisable(busy);
        addMirrorButton.setDisable(busy || mirrors.size() >= CommunityDownloadSources.MAX_MIRRORS);
        primaryField.setDisable(busy);
        for (MirrorRow row : mirrors) {
            row.urlField.setDisable(busy);
            row.removeButton.setDisable(busy);
        }
    }

    /// 显示提示信息。
    ///
    /// @param text 文案
    /// @param error 是否为错误
    private void showMessage(@NotNull String text, boolean error) {
        message.setText(text);
        message.getStyleClass().setAll(error ? "error-label" : "hint");
        message.setManaged(true);
        message.setVisible(true);
    }

    /// 镜像源一行。
    private final class MirrorRow {

        /// 行容器。
        private final LinePane pane = new LinePane();

        /// 地址输入框。
        private final JFXTextField urlField = new JFXTextField();

        /// 删除按钮。
        private final JFXButton removeButton = FXUtils.newBorderButton(
                i18n("community.settings.download.remove"));

        /// 构造一行。
        private MirrorRow() {
            urlField.setPromptText(i18n("community.settings.download.url.prompt"));
            FXUtils.setLimitWidth(urlField, FIELD_WIDTH);

            HBox right = new HBox(8, urlField,
                    createTestButton(() -> CommunityDownloadSource.normalizeUrl(urlField.getText()), pane),
                    removeButton);
            right.setAlignment(Pos.CENTER_RIGHT);
            pane.setRight(right);

            removeButton.setOnAction(e -> {
                mirrors.remove(this);
                refreshMirrorRows();
            });
        }
    }
}
