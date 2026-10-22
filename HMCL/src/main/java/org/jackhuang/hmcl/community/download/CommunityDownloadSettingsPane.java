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
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
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
import java.util.Collections;
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
/// - 地址、增删行、单项测试或 [测速排序] 完成后自动保存并应用（700 ms 防抖），
///   不再依赖手动点 [保存并应用]；
/// - 每个源可以单独测试连通性并显示延迟；
/// - [测速排序] 立即测试全部源，按延迟从低到高重排列表；重排期间保持当前滚动位置；
/// - 保存后立即写 `community-settings.json` 并重建下载候选链，无需重启；
/// - 每次进入下载入口或开始文件下载前都会静默重新测速（见
///   [CommunityDownloadRanker]），先应用新顺序再继续下载。
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

    /// 当前设置页是否显示在窗口中；自动测速排序落盘时避免覆盖正在编辑的列表。
    private static volatile boolean instanceVisible;

    /// 编辑后的自动保存防抖。
    private final PauseTransition autoSaveTimer = new PauseTransition(Duration.millis(700));

    /// 是否正在从磁盘装载（装载期间不触发自动保存）。
    private boolean loading;

    /// 是否已有自动保存任务在 IO 线程执行。
    private boolean autoSaveInProgress;

    /// 待执行的自动保存是否需要强制重新测速（源集合变化时需要）。
    private boolean autoSaveForceRanking;

    /// 编辑代数：每次编辑自增；自动保存完成时若代数已变化则再存一次。
    private long editGeneration;

    /// 排序进行中标记，避免重复点击。
    private boolean sorting;

    /// 串行化手动/自动保存，避免旧快照晚于新快照落盘。
    private final Object saveLock = new Object();

    /// 保存快照序号；后发起的保存序号更大。
    private long saveSequence;

    /// 已经落盘的最大保存序号；IO 线程用它丢弃过期快照。
    private volatile long lastWrittenSequence;

    /// 构造设置页并从磁盘加载当前配置。
    public CommunityDownloadSettingsPane() {
        lastInstance = new WeakReference<>(this);
        sceneProperty().addListener((observable, oldScene, newScene) ->
                instanceVisible = newScene != null && this == visibleInstance());
        autoSaveTimer.setOnFinished(event -> autoSaveNow());
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
            onSourcesEdited(true);
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

    /// 当前是否有下载源设置页显示在窗口中。
    ///
    /// 自动测速排序完成后，若用户正在编辑下载源，则只应用内存顺序、不写盘，
    /// 避免把用户尚在输入的列表覆盖掉。
    static boolean isInstanceVisible() {
        return instanceVisible;
    }

    /// 最近创建的设置页实例，或 `null`。
    @Nullable
    private static CommunityDownloadSettingsPane visibleInstance() {
        WeakReference<CommunityDownloadSettingsPane> reference = lastInstance;
        return reference == null ? null : reference.get();
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
        autoSaveTimer.stop();
        loading = true;
        try {
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
        } finally {
            loading = false;
        }
    }

    /// 新增一个源行。
    ///
    /// @param url 初始地址，或 `null` 表示空行
    private void addSourceRow(@Nullable String url) {
        SourceRow row = new SourceRow();
        if (url != null) {
            row.urlField.setText(url);
        }
        row.urlField.textProperty().addListener((observable, oldValue, newValue) ->
                onSourcesEdited(true));
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
        ObservableList<Node> content = sourcesCard.getContent();
        if (content.size() == nodes.size()
                && content.containsAll(nodes)
                && nodes.containsAll(content)) {
            // 仅顺序变化时原地交换，避免先清空再填满导致 ScrollPane 跳到顶部。
            for (int i = 0; i < nodes.size(); i++) {
                Node desired = nodes.get(i);
                if (content.get(i) != desired) {
                    int currentIndex = content.indexOf(desired);
                    if (currentIndex > i) {
                        Collections.swap(content, i, currentIndex);
                    }
                }
            }
        } else {
            content.setAll(nodes);
        }
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
                        // 用户点“测试”说明这个源已经可用，顺手自动保存并应用。
                        onSourcesEdited(false);
                    }));
        });
        return button;
    }

    /// 编辑发生后启动 700 ms 防抖自动保存。
    ///
    /// @param forceRanking 源集合变化时需要强制重新测速；仅测试/排序时可为 `false`
    private void onSourcesEdited(boolean forceRanking) {
        if (loading) {
            return;
        }
        editGeneration++;
        autoSaveForceRanking |= forceRanking;
        autoSaveTimer.playFromStart();
    }

    /// 防抖到期后自动保存；输入尚未合法时静默等待下一次编辑。
    private void autoSaveNow() {
        if (loading) {
            return;
        }
        if (autoSaveInProgress) {
            // 上一次保存在 IO 线程还没完成；等它结束后再尝试。
            autoSaveTimer.playFromStart();
            return;
        }
        List<SourceRow> nonEmpty = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        String error = collectValidSources(nonEmpty, urls);
        if (error != null) {
            // 用户可能正输入到一半（如只输了 https://），手动保存才提示错误。
            return;
        }
        for (SourceRow row : sources) {
            if (CommunityDownloadSource.normalizeUrl(row.urlField.getText()).isEmpty()) {
                // 空白行是“待填写”的占位，自动保存时不要把它当成删除。
                return;
            }
        }
        boolean forceRanking = autoSaveForceRanking;
        autoSaveForceRanking = false;
        autoSaveInProgress = true;
        persist(configuredSources(nonEmpty), forceRanking, true, editGeneration);
    }

    /// 校验并手动保存当前配置。
    private void save() {
        autoSaveTimer.stop();
        List<SourceRow> nonEmpty = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        String error = collectValidSources(nonEmpty, urls);
        if (error != null) {
            showMessage(error, true);
            return;
        }

        setBusy(true);
        autoSaveForceRanking = false;
        persist(configuredSources(nonEmpty), true, false, editGeneration);
    }

    /// 由非空行构造下载源，尽量复用已有显示名。
    ///
    /// @param nonEmpty 非空行
    /// @return 下载源列表
    @NotNull
    private List<CommunityDownloadSource> configuredSources(@NotNull List<SourceRow> nonEmpty) {
        List<CommunityDownloadSource> configured = new ArrayList<>(nonEmpty.size());
        for (SourceRow row : nonEmpty) {
            configured.add(toSource(CommunityDownloadSource.normalizeUrl(row.urlField.getText())));
        }
        return configured;
    }

    /// 异步保存并应用下载源；自动/手动共用。
    ///
    /// @param configured 已校验的下载源
    /// @param forceRanking 是否强制重新测速
    /// @param automatic 是否为自动保存
    /// @param generation 触发保存时的编辑代数
    private void persist(@NotNull List<CommunityDownloadSource> configured,
                         boolean forceRanking,
                         boolean automatic,
                         long generation) {
        CommunityDownloadSources sourcesToSave = new CommunityDownloadSources(configured);
        long sequence = ++saveSequence;
        Schedulers.io().execute(() -> {
            try {
                boolean written;
                synchronized (saveLock) {
                    written = sequence >= lastWrittenSequence;
                    if (written) {
                        sourcesToSave.save();
                        CommunityDownloadInstaller.apply();
                        // 保存后立即按新配置测速，避免沿用旧源的 5 分钟缓存。
                        CommunityDownloadRanker.refreshAsync(forceRanking);
                        if (CommunityDownloadGuard.isConfigured() != !sourcesToSave.isEmpty()) {
                            throw new IllegalStateException("Configured download sources were not applied");
                        }
                        lastWrittenSequence = sequence;
                    }
                }
                if (!written) {
                    // 更晚的保存已经落盘，这里只复位忙碌标记，不覆盖新配置。
                    Schedulers.javafx().execute(() -> {
                        if (automatic) {
                            autoSaveInProgress = false;
                        } else {
                            setBusy(false);
                        }
                    });
                    return;
                }
                long revision = CommunityDownloadSources.revision();
                int count = sourcesToSave.size();
                Schedulers.javafx().execute(() -> {
                    loadedRevision = revision;
                    if (automatic) {
                        autoSaveInProgress = false;
                        if (generation == editGeneration) {
                            showMessage(i18n("community.settings.download.auto_saved", count), false);
                        } else {
                            autoSaveTimer.playFromStart();
                        }
                    } else {
                        setBusy(false);
                        showMessage(i18n("community.settings.download.applied", count), false);
                    }
                });
            } catch (Throwable e) {
                LOG.warning("Failed to save community download sources", e);
                Schedulers.javafx().execute(() -> {
                    if (automatic) {
                        autoSaveInProgress = false;
                    } else {
                        setBusy(false);
                    }
                    showMessage(i18n("community.settings.download.save_failed"), true);
                    if (generation != editGeneration) {
                        autoSaveTimer.playFromStart();
                    }
                });
            }
        });
    }

    /// 立即测速全部源，按延迟升序重排界面行，并自动保存应用。
    ///
    /// 重排期间保留外层 [ScrollPane] 的滚动位置，避免用户点“测速排序”后
    /// 页面突然跳到下半部分。
    private void speedTestAndSort() {
        if (sorting) {
            return;
        }
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

        autoSaveTimer.stop();
        sorting = true;
        ScrollPane scrollPane = findScrollPane();
        double scrollV = scrollPane == null ? 0 : scrollPane.getVvalue();
        double scrollH = scrollPane == null ? 0 : scrollPane.getHvalue();

        setBusy(true);
        showMessage(i18n("community.settings.download.sort.running"), false);

        List<CompletableFuture<DownloadSourceTester.Result>> futures = new ArrayList<>(urls.size());
        for (String url : urls) {
            futures.add(DownloadSourceTester.probe(url));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> Schedulers.javafx().execute(() -> {
                    boolean ok = false;
                    try {
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
                        List<String> sortedUrls = new ArrayList<>(results.size());
                        for (RowResult result : results) {
                            sources.add(result.row());
                            sortedUrls.add(CommunityDownloadSource.normalizeUrl(
                                    result.row().urlField.getText()));
                            if (result.result().available()) {
                                result.row().pane.setSubtitle(i18n("community.settings.download.test.ok.ms",
                                        result.result().latencyMillis()));
                            } else {
                                result.row().pane.setSubtitle(i18n("community.settings.download.test.fail"));
                            }
                        }
                        CommunityDownloadRanker.acceptRanking(sortedUrls);
                        refreshSourceRows();
                        ok = true;
                    } catch (Throwable e) {
                        LOG.warning("Failed to apply download source ranking", e);
                    } finally {
                        setBusy(false);
                        sorting = false;
                    }
                    if (!ok || failure != null) {
                        showMessage(i18n("community.settings.download.sort.failed"), true);
                        return;
                    }
                    showMessage(i18n("community.settings.download.sort.done"), false);
                    // 测速结果就是用户想要的顺序，自动保存并应用，无需再点保存。
                    onSourcesEdited(false);
                    if (scrollPane != null) {
                        Platform.runLater(() -> {
                            scrollPane.setVvalue(scrollV);
                            scrollPane.setHvalue(scrollH);
                        });
                    }
                }));
    }

    /// 从当前节点向上查找包裹设置内容的滚动容器。
    ///
    /// @return 滚动容器；不存在时返回 `null`
    @Nullable
    private ScrollPane findScrollPane() {
        Parent current = this;
        while (current != null) {
            if (current instanceof ScrollPane scrollPane) {
                return scrollPane;
            }
            current = current.getParent();
        }
        return null;
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
                onSourcesEdited(true);
            });
        }
    }
}
