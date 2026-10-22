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

import org.jackhuang.hmcl.community.ui.CommunityStyles;
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
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LinePane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 下载源设置页：最多 5 个平级源，下载时自动测速排序。
///
/// 交互约定：
/// - 地址留空表示该槽位不配置；全部留空时下载入口会提示先配置下载源；
/// - 每个源可以单独测试连通性并显示延迟；
/// - [测速排序] 立即测试全部源，并按延迟从低到高重排列表（只改界面，保存后才落盘）；
/// - 保存后立即写 `community-settings.json` 并重建下载候选链，无需重启；
/// - 每次进入下载入口或保存后都会自动重新测速（5 分钟 TTL）。
@NotNullByDefault
public final class CommunityDownloadSettingsPane extends VBox {

    /// 地址输入框宽度。
    private static final double FIELD_WIDTH = 240;

    /// 下载源卡片。
    private final ComponentList sourcesCard = new ComponentList();

    /// 已添加的源行。
    private final List<SourceRow> sources = new ArrayList<>();

    /// 源计数标签。
    private final Label sourceCount = new Label();

    /// 添加源按钮。
    private final JFXButton addSourceButton = FXUtils.newBorderButton(i18n("community.settings.download.add"));

    /// 测速排序按钮。
    private final JFXButton sortButton = FXUtils.newBorderButton(i18n("community.settings.download.sort"));

    /// 保存按钮。
    private final JFXButton saveButton = FXUtils.newBorderButton(i18n("community.settings.download.save"));

    /// 恢复默认按钮。
    private final JFXButton resetButton = FXUtils.newBorderButton(i18n("community.settings.download.reset"));

    /// 校验/保存结果提示。
    private final Label message = new Label();

    /// 已知地址 → 显示名，用于保存时保留用户已有名称。
    private final Map<String, String> knownNames = new HashMap<>();

    /// 上次从磁盘加载时的 [CommunityDownloadSources#revision()]。
    private long loadedRevision = -1L;

    /// 最近创建的设置页；导入完成后用于刷新仍可见的列表。
    private static volatile @Nullable WeakReference<CommunityDownloadSettingsPane> lastInstance;

    /// 构造设置页并从磁盘加载当前配置。
    public CommunityDownloadSettingsPane() {
        lastInstance = new WeakReference<>(this);
        CommunityStyles.apply(this);
        setSpacing(10);
        setPadding(new Insets(10));
        setFillWidth(true);

        Label hint = new Label(i18n("community.settings.download.hint"));
        hint.setWrapText(true);
        hint.getStyleClass().addAll("hint", "info");

        Label title = new Label(i18n("community.settings.download.sources"));
        title.getStyleClass().add("title-label");

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, headerSpacer, sourceCount);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 0, 0, 10));

        sourcesCard.getStyleClass().add("card-non-transparent");

        addSourceButton.setOnAction(e -> {
            addSourceRow(null);
            refreshSourceRows();
        });
        sortButton.setOnAction(e -> speedTestAndSort());
        saveButton.setOnAction(e -> save());
        resetButton.setOnAction(e -> confirmReset());

        HBox sourceActions = new HBox(8, addSourceButton, sortButton);
        sourceActions.setPadding(new Insets(0, 0, 0, 10));

        Region actionsSpacer = new Region();
        HBox.setHgrow(actionsSpacer, Priority.ALWAYS);
        HBox actions = new HBox(8, actionsSpacer, resetButton, saveButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        message.setWrapText(true);
        message.setManaged(false);
        message.setVisible(false);

        getChildren().setAll(hint, header, sourcesCard, sourceActions, actions, message);

        loadFromDisk();
    }

    /// 重新从磁盘读取配置并刷新列表。
    ///
    /// 导入 `.lmcl.json` 或从其他页面回到“设置 → 下载”时调用，避免长生命周期
    /// 的设置页显示导入前的旧列表。
    public void reload() {
        FXUtils.checkFxUserThread();
        loadFromDisk();
    }

    /// 刷新当前仍可见的设置页；由导入完成后调用。
    ///
    /// 未显示时不做事，页面下次显示会走 [CommunityDownloadSettingsPane#reloadIfChanged()]。
    public static void refreshVisible() {
        FXUtils.checkFxUserThread();
        WeakReference<CommunityDownloadSettingsPane> reference = lastInstance;
        CommunityDownloadSettingsPane pane = reference == null ? null : reference.get();
        if (pane != null && pane.getScene() != null) {
            pane.reloadIfChanged();
        }
    }

    /// 仅当磁盘配置被其他入口（如导入）保存过时刷新；不会覆盖尚未保存的编辑。
    public void reloadIfChanged() {
        FXUtils.checkFxUserThread();
        if (loadedRevision != CommunityDownloadSources.revision()) {
            loadFromDisk();
        }
    }

    /// 从磁盘读取配置并填充 UI。
    private void loadFromDisk() {
        long revision = CommunityDownloadSources.revision();
        sources.clear();
        knownNames.clear();
        CommunityDownloadSources loaded = CommunityDownloadSources.load();
        for (CommunityDownloadSource source : loaded.sources()) {
            knownNames.put(source.url(), source.name());
            addSourceRow(source.url());
        }
        refreshSourceRows();
        message.setText("");
        message.setManaged(false);
        message.setVisible(false);
        loadedRevision = revision;
    }

    /// 新增一个源行。
    ///
    /// @param url 初始地址，或 `null` 表示空行
    private void addSourceRow(@Nullable String url) {
        SourceRow row = new SourceRow();
        if (url != null) {
            row.urlField.setText(url);
        }
        sources.add(row);
    }

    /// 重建源卡片内容、序号与计数。
    private void refreshSourceRows() {
        List<Node> nodes = new ArrayList<>();
        if (sources.isEmpty()) {
            LinePane empty = new LinePane();
            empty.setTitle(i18n("community.settings.download.empty"));
            empty.setSubtitle(i18n("community.settings.download.empty.hint"));
            nodes.add(empty);
        } else {
            for (int i = 0; i < sources.size(); i++) {
                SourceRow row = sources.get(i);
                row.pane.setTitle(i18n("community.settings.download.source", i + 1));
                nodes.add(row.pane);
            }
        }
        sourcesCard.getContent().setAll(nodes);
        sourceCount.setText(i18n("community.settings.download.count", sources.size(),
                CommunityDownloadSources.MAX_SOURCES));
        addSourceButton.setDisable(sources.size() >= CommunityDownloadSources.MAX_SOURCES);
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
            DownloadSourceTester.probe(url).whenComplete((result, error) ->
                    Schedulers.javafx().execute(() -> {
                        button.setDisable(false);
                        if (error == null && result != null && result.available()) {
                            row.setSubtitle(i18n("community.settings.download.test.ok.ms",
                                    result.latencyMillis()));
                        } else {
                            row.setSubtitle(i18n("community.settings.download.test.fail"));
                        }
                    }));
        });
        return button;
    }

    /// 校验并保存当前配置。
    private void save() {
        List<SourceRow> nonEmpty = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        String error = collectValidSources(nonEmpty, urls);
        if (error != null) {
            showMessage(error, true);
            return;
        }

        List<CommunityDownloadSource> configured = new ArrayList<>(nonEmpty.size());
        for (SourceRow row : nonEmpty) {
            configured.add(toSource(CommunityDownloadSource.normalizeUrl(row.urlField.getText())));
        }
        CommunityDownloadSources sourcesToSave = new CommunityDownloadSources(configured);

        setBusy(true);
        Schedulers.io().execute(() -> {
            try {
                sourcesToSave.save();
                CommunityDownloadInstaller.apply();
                // 保存后立即按新配置测速，避免沿用旧源的 5 分钟缓存。
                CommunityDownloadRanker.refreshAsync(true);
                if (CommunityDownloadGuard.isConfigured() != !sourcesToSave.isEmpty()) {
                    throw new IllegalStateException("Configured download sources were not applied");
                }
                int count = sourcesToSave.size();
                Schedulers.javafx().execute(() -> {
                    setBusy(false);
                    showMessage(i18n("community.settings.download.applied", count), false);
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

    /// 立即测速全部源，按延迟升序重排界面行；结果需保存后才落盘。
    private void speedTestAndSort() {
        List<SourceRow> rows = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        String error = collectValidSources(rows, urls);
        if (error != null) {
            showMessage(error, true);
            return;
        }
        if (rows.isEmpty()) {
            showMessage(i18n("community.settings.download.sort.empty"), true);
            return;
        }

        setBusy(true);
        showMessage(i18n("community.settings.download.sort.running"), false);

        List<CompletableFuture<DownloadSourceTester.Result>> futures = new ArrayList<>(urls.size());
        for (String url : urls) {
            futures.add(DownloadSourceTester.probe(url));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> Schedulers.javafx().execute(() -> {
                    List<RowResult> results = new ArrayList<>(rows.size());
                    for (int i = 0; i < rows.size(); i++) {
                        DownloadSourceTester.Result result;
                        try {
                            result = futures.get(i).join();
                        } catch (Throwable e) {
                            result = DownloadSourceTester.Result.failure();
                        }
                        results.add(new RowResult(rows.get(i), result));
                    }
                    results.sort((a, b) -> {
                        if (a.result().available() != b.result().available()) {
                            return a.result().available() ? -1 : 1;
                        }
                        if (!a.result().available()) {
                            return 0;
                        }
                        return Long.compare(a.result().latencyMillis(), b.result().latencyMillis());
                    });

                    sources.clear();
                    for (RowResult result : results) {
                        sources.add(result.row());
                        if (result.result().available()) {
                            result.row().pane.setSubtitle(i18n("community.settings.download.test.ok.ms",
                                    result.result().latencyMillis()));
                        } else {
                            result.row().pane.setSubtitle(i18n("community.settings.download.test.fail"));
                        }
                    }
                    refreshSourceRows();
                    setBusy(false);
                    if (failure != null) {
                        showMessage(i18n("community.settings.download.sort.failed"), true);
                    } else {
                        showMessage(i18n("community.settings.download.sort.done"), false);
                    }
                }));
    }

    /// 收集非空源行并校验；返回错误文案或 `null`。
    ///
    /// @param nonEmpty 输出参数：非空行
    /// @param urls 输出参数：规范化地址
    /// @return 错误文案或 `null`
    @Nullable
    private String collectValidSources(@NotNull List<SourceRow> nonEmpty,
                                       @NotNull List<String> urls) {
        for (SourceRow row : sources) {
            String url = CommunityDownloadSource.normalizeUrl(row.urlField.getText());
            if (url.isEmpty()) {
                continue;
            }
            if (!CommunityDownloadSource.isValidUrl(url)) {
                return i18n("community.settings.download.error.invalid", url);
            }
            urls.add(url);
            nonEmpty.add(row);
        }
        if (nonEmpty.size() > CommunityDownloadSources.MAX_SOURCES) {
            return i18n("community.settings.download.error.too_many",
                    CommunityDownloadSources.MAX_SOURCES);
        }
        String duplicate = CommunityDownloadSources.findDuplicate(urls);
        if (duplicate != null) {
            return i18n("community.settings.download.error.duplicate", duplicate);
        }
        return null;
    }

    /// 按地址构造下载源，尽量复用已有显示名。
    ///
    /// @param url 规范化地址
    /// @return 下载源
    @NotNull
    private CommunityDownloadSource toSource(@NotNull String url) {
        return CommunityDownloadSource.of(knownNames.getOrDefault(url, ""), url);
    }

    /// 确认后清空全部下载源（不内置任何源）。
    private void confirmReset() {
        MessageDialogPane pane = new MessageDialogPane.Builder(
                i18n("community.settings.download.reset.confirm"),
                i18n("community.settings.download.reset"),
                MessageDialogPane.MessageType.QUESTION)
                .yesOrNo(() -> {
                    sources.clear();
                    knownNames.clear();
                    refreshSourceRows();

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

    /// 保存/测速/重置期间禁用操作。
    ///
    /// @param busy 是否忙碌
    private void setBusy(boolean busy) {
        saveButton.setDisable(busy);
        resetButton.setDisable(busy);
        sortButton.setDisable(busy);
        addSourceButton.setDisable(busy || sources.size() >= CommunityDownloadSources.MAX_SOURCES);
        for (SourceRow row : sources) {
            row.urlField.setDisable(busy);
            row.testButton.setDisable(busy);
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

    /// 一次测速结果与对应行。
    ///
    /// @param row 行
    /// @param result 探测结果
    private record RowResult(@NotNull SourceRow row, @NotNull DownloadSourceTester.Result result) {
    }

    /// 下载源一行。
    private final class SourceRow {

        /// 行容器。
        private final LinePane pane = new LinePane();

        /// 地址输入框。
        private final JFXTextField urlField = new JFXTextField();

        /// 测试按钮。
        private final JFXButton testButton = createTestButton(
                () -> CommunityDownloadSource.normalizeUrl(urlField.getText()), pane);

        /// 删除按钮。
        private final JFXButton removeButton = FXUtils.newBorderButton(
                i18n("community.settings.download.remove"));

        /// 构造一行。
        private SourceRow() {
            urlField.setPromptText(i18n("community.settings.download.url.prompt"));
            FXUtils.setLimitWidth(urlField, FIELD_WIDTH);

            HBox right = new HBox(8, urlField, testButton, removeButton);
            right.setAlignment(Pos.CENTER_RIGHT);
            pane.setRight(right);

            removeButton.setOnAction(e -> {
                sources.remove(this);
                refreshSourceRows();
            });
        }
    }
}
