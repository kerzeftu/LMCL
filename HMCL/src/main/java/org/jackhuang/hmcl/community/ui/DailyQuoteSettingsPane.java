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
package org.jackhuang.hmcl.community.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXToggleButton;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.quote.DailyQuote;
import org.jackhuang.hmcl.community.quote.DailyQuoteEvents;
import org.jackhuang.hmcl.community.quote.DailyQuoteService;
import org.jackhuang.hmcl.community.quote.DailyQuoteSettings;
import org.jackhuang.hmcl.community.quote.QuoteSource;
import org.jackhuang.hmcl.community.quote.QuoteSourceType;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.LinePane;
import org.jackhuang.hmcl.ui.construct.LineToggleButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 设置·外观中的每日一言设置。
///
/// 只负责 UI 与保存；主页通过 [DailyQuoteEvents] 得知变更并重新读取配置。
/// 多源选择规则：启用多个源时，每次刷新随机抽取一个；全部失败时静默回退。
@NotNullByDefault
public final class DailyQuoteSettingsPane extends VBox {

    private final ComponentList sourceList = new ComponentList();
    private final ComponentList localList = new ComponentList();
    private final Label sourceHint = new Label();
    private final Label previewText = new Label();
    private final Label previewMeta = new Label();
    private final JFXButton refreshButton;
    private final JFXButton addSourceButton;
    private final DailyQuoteService previewService = new DailyQuoteService();
    private final LineToggleButton displayToggle = new LineToggleButton();
    private final LineToggleButton onlineToggle = new LineToggleButton();
    private final LineToggleButton localMcToggle = new LineToggleButton();
    private final LineToggleButton localPoetryToggle = new LineToggleButton();

    private DailyQuoteSettings.Config config;
    private CompletableFuture<Void> saveChain = CompletableFuture.completedFuture(null);
    private boolean updating;
    private boolean loading = true;

    /// 创建设置面板。
    public DailyQuoteSettingsPane() {
        setSpacing(10);
        // 设置页在 FX 线程构造；先用内存默认值渲染，再从 io 线程加载真实配置。
        config = DailyQuoteSettings.defaults();

        ComponentList mainList = new ComponentList();
        displayToggle.setTitle(i18n("community.dailyQuote.display"));
        displayToggle.setSubtitle(i18n("community.dailyQuote.display.subtitle"));
        displayToggle.setSelected(config.enabled());
        displayToggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (updating) {
                return;
            }
            config = config.withEnabled(newValue);
            save();
        });

        onlineToggle.setTitle(i18n("community.dailyQuote.online"));
        onlineToggle.setSubtitle(i18n("community.dailyQuote.online.subtitle"));
        onlineToggle.setSelected(config.online());
        onlineToggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (updating) {
                return;
            }
            config = config.withOnline(newValue);
            save();
            rebuildSources();
            updateControls();
        });
        mainList.getContent().addAll(displayToggle, onlineToggle);

        localMcToggle.setTitle(i18n("community.dailyQuote.local.mc"));
        localMcToggle.setSubtitle(i18n("community.dailyQuote.local.mc.subtitle"));
        localMcToggle.setSelected(config.localMc());
        localMcToggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (updating) {
                return;
            }
            if (!newValue && !config.localPoetry()) {
                revertToggle(localMcToggle, true);
                previewMeta.setText(i18n("community.dailyQuote.local.none"));
                return;
            }
            config = config.withLocalMc(newValue);
            save();
            updateControls();
        });

        localPoetryToggle.setTitle(i18n("community.dailyQuote.local.poetry"));
        localPoetryToggle.setSubtitle(i18n("community.dailyQuote.local.poetry.subtitle"));
        localPoetryToggle.setSelected(config.localPoetry());
        localPoetryToggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (updating) {
                return;
            }
            if (!newValue && !config.localMc()) {
                revertToggle(localPoetryToggle, true);
                previewMeta.setText(i18n("community.dailyQuote.local.none"));
                return;
            }
            config = config.withLocalPoetry(newValue);
            save();
            updateControls();
        });
        localList.getContent().addAll(localMcToggle, localPoetryToggle);

        addSourceButton = FXUtils.newBorderButton(i18n("community.dailyQuote.source.add"));
        addSourceButton.setOnAction(event -> openAddSourceDialog());

        refreshButton = FXUtils.newBorderButton(i18n("community.dailyQuote.refresh"));
        refreshButton.setOnAction(event -> refreshPreview());

        sourceHint.setText(i18n("community.dailyQuote.sources.hint"));
        sourceHint.setWrapText(true);
        sourceHint.getStyleClass().add("subtitle-label");

        VBox previewBox = new VBox(6);
        previewText.setWrapText(true);
        previewText.getStyleClass().add("community-daily-quote-preview");
        previewMeta.setWrapText(true);
        previewMeta.getStyleClass().add("subtitle-label");
        HBox previewActions = new HBox(8, refreshButton);
        previewActions.setAlignment(Pos.CENTER_RIGHT);
        previewBox.getChildren().addAll(previewText, previewMeta, previewActions);

        getChildren().addAll(
                mainList,
                ComponentList.createComponentListTitle(i18n("community.dailyQuote.local")),
                localList,
                ComponentList.createComponentListTitle(i18n("community.dailyQuote.sources")),
                sourceList,
                sourceHint,
                addSourceButton,
                ComponentList.createComponentListTitle(i18n("community.dailyQuote.preview")),
                previewBox);

        CommunityStyles.apply(this);
        rebuildSources();
        updateControls();
        DailyQuote initial = previewService.initialQuote();
        previewText.setText(initial.displayLine());
        previewMeta.setText(i18n("community.dailyQuote.preview.hint"));
        loadConfigAsync();
    }

    /// 在 io 线程读取设置，避免设置页构造时阻塞 FX 线程。
    private void loadConfigAsync() {
        CompletableFuture.supplyAsync(DailyQuoteSettings::load, Schedulers.io())
                .whenCompleteAsync((loaded, error) -> {
                    if (error != null) {
                        LOG.warning("Failed to load daily quote settings", error);
                        loading = false;
                        updateControls();
                        return;
                    }
                    if (loaded == null) {
                        loading = false;
                        updateControls();
                        return;
                    }
                    config = loaded;
                    loading = false;
                    updating = true;
                    try {
                        displayToggle.setSelected(config.enabled());
                        onlineToggle.setSelected(config.online());
                        localMcToggle.setSelected(config.localMc());
                        localPoetryToggle.setSelected(config.localPoetry());
                    } finally {
                        updating = false;
                    }
                    rebuildSources();
                    updateControls();
                }, Schedulers.javafx());
    }

    /// 重新构建源行。
    private void rebuildSources() {
        sourceList.getContent().clear();
        for (QuoteSource source : config.sources()) {
            LinePane row = new LinePane();
            row.setTitle(sourceTitle(source));
            row.setSubtitle(sourceSubtitle(source));

            HBox right = new HBox(8);
            right.setAlignment(Pos.CENTER_RIGHT);

            JFXToggleButton toggle = new JFXToggleButton();
            toggle.setSelected(source.enabled());
            toggle.setSize(8);
            FXUtils.setLimitHeight(toggle, 30);
            toggle.setDisable(!config.online());
            toggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
                if (updating) {
                    return;
                }
                updateSource(source.id(), newValue);
            });
            right.getChildren().add(toggle);

            if (source.type() == QuoteSourceType.CUSTOM) {
                JFXButton delete = new JFXButton();
                delete.getStyleClass().add("toggle-icon-tiny");
                delete.setGraphic(SVG.DELETE.createIcon(16));
                FXUtils.installFastTooltip(delete, i18n("community.dailyQuote.source.delete"));
                delete.setOnAction(event -> deleteSource(source));
                right.getChildren().add(delete);
            }

            row.setRight(right);
            sourceList.getContent().add(row);
        }
    }

    /// 修改某个源的启用状态。
    private void updateSource(@NotNull String id, boolean enabled) {
        List<QuoteSource> sources = new ArrayList<>(config.sources().size());
        for (QuoteSource source : config.sources()) {
            sources.add(source.id().equals(id) ? source.withEnabled(enabled) : source);
        }
        config = config.withSources(sources);
        save();
        updateControls();
    }

    /// 删除自定义源。
    private void deleteSource(@NotNull QuoteSource source) {
        if (source.type() != QuoteSourceType.CUSTOM) {
            return;
        }
        Controllers.confirm(
                i18n("community.dailyQuote.source.delete.confirm", source.name()),
                i18n("community.dailyQuote.title"),
                () -> {
                    List<QuoteSource> sources = new ArrayList<>(config.sources());
                    sources.removeIf(item -> item.id().equals(source.id()));
                    config = config.withSources(sources);
                    save();
                    rebuildSources();
                    updateControls();
                },
                null);
    }

    /// 打开“添加自定义源”对话框。
    private void openAddSourceDialog() {
        JFXDialogLayout dialog = new JFXDialogLayout();
        dialog.setHeading(new Label(i18n("community.dailyQuote.source.add.title")));
        dialog.setPrefWidth(460);

        JFXTextField nameField = new JFXTextField();
        nameField.setPromptText(i18n("community.dailyQuote.source.add.name"));
        JFXTextField urlField = new JFXTextField();
        urlField.setPromptText(i18n("community.dailyQuote.source.add.url"));
        Label error = new Label();
        error.getStyleClass().add("subtitle-label");
        error.setWrapText(true);

        VBox body = new VBox(10, nameField, urlField, error);
        dialog.setBody(body);

        JFXButton cancel = new JFXButton(i18n("button.cancel"));
        cancel.getStyleClass().add("dialog-cancel");
        cancel.setOnAction(event -> dialog.fireEvent(new DialogCloseEvent()));
        JFXButton add = new JFXButton(i18n("button.ok"));
        add.getStyleClass().add("dialog-accept");
        add.setOnAction(event -> {
            try {
                String name = nameField.getText() == null ? "" : nameField.getText().trim();
                String url = urlField.getText() == null ? "" : urlField.getText().trim();
                String id = "custom-" + Long.toString(System.currentTimeMillis(), 36);
                QuoteSource source = new QuoteSource(id, QuoteSourceType.CUSTOM, name, url, true);
                List<QuoteSource> sources = new ArrayList<>(config.sources());
                sources.add(source);
                config = config.withSources(sources);
                save();
                rebuildSources();
                updateControls();
                dialog.fireEvent(new DialogCloseEvent());
            } catch (IllegalArgumentException e) {
                error.setText(i18n("community.dailyQuote.source.add.invalid"));
            }
        });
        dialog.setActions(cancel, add);
        Controllers.dialog(dialog);
        Platform.runLater(nameField::requestFocus);
    }

    /// 手动获取一条预览。
    private void refreshPreview() {
        refreshButton.setDisable(true);
        previewMeta.setText(i18n("community.dailyQuote.refreshing"));
        previewService.refresh(true, result -> {
            refreshButton.setDisable(false);
            previewText.setText(result.quote().displayLine());
            if (result.error() != null) {
                previewMeta.setText(i18n("community.dailyQuote.refresh.failed"));
            } else if (result.fromNetwork()) {
                previewMeta.setText(i18n("community.dailyQuote.refresh.success"));
            } else {
                previewMeta.setText(i18n("community.dailyQuote.preview.hint"));
            }
            updateControls();
        });
    }

    /// 在 io 线程保存配置，成功后再通知主页；连续修改串行写入，避免旧值覆盖新值。
    private void save() {
        DailyQuoteSettings.Config snapshot = config;
        saveChain = saveChain.handle((ignored, error) -> null)
                .thenRunAsync(() -> DailyQuoteSettings.save(snapshot), Schedulers.io())
                .whenCompleteAsync((ignored, error) -> {
                    if (error != null) {
                        LOG.warning("Failed to save daily quote settings", error);
                    } else {
                        DailyQuoteEvents.fireChanged();
                    }
                }, Schedulers.javafx());
    }

    /// 恢复开关到实际配置值，避免监听器再次触发保存。
    private void revertToggle(@NotNull LineToggleButton toggle, boolean value) {
        updating = true;
        try {
            toggle.setSelected(value);
        } finally {
            updating = false;
        }
    }

    /// 根据开关状态刷新可用性。
    private void updateControls() {
        updating = true;
        try {
            if (loading) {
                displayToggle.setDisable(true);
                onlineToggle.setDisable(true);
                localMcToggle.setDisable(true);
                localPoetryToggle.setDisable(true);
                addSourceButton.setDisable(true);
                sourceList.setDisable(true);
                refreshButton.setDisable(true);
                return;
            }
            displayToggle.setDisable(false);
            onlineToggle.setDisable(false);
            localMcToggle.setDisable(false);
            localPoetryToggle.setDisable(false);
            boolean online = config.online();
            addSourceButton.setDisable(!online);
            sourceList.setDisable(!online);
            boolean hasLocal = config.hasLocalSource();
            boolean hasOnline = online && !config.enabledSources().isEmpty();
            refreshButton.setDisable(!config.enabled() || (!hasLocal && !hasOnline));
            if (!hasLocal && !hasOnline) {
                previewMeta.setText(i18n("community.dailyQuote.source.none"));
            }
        } finally {
            updating = false;
        }
    }

    private @NotNull String sourceTitle(@NotNull QuoteSource source) {
        return switch (source.type()) {
            case HITOKOTO -> i18n("community.dailyQuote.source.hitokoto");
            case JINRISHICI -> i18n("community.dailyQuote.source.jinrishici");
            case CUSTOM -> source.name();
        };
    }

    private @NotNull String sourceSubtitle(@NotNull QuoteSource source) {
        return switch (source.type()) {
            case HITOKOTO -> i18n("community.dailyQuote.source.hitokoto.desc");
            case JINRISHICI -> i18n("community.dailyQuote.source.jinrishici.desc");
            case CUSTOM -> Objects.requireNonNullElse(source.host(), source.url());
        };
    }
}
