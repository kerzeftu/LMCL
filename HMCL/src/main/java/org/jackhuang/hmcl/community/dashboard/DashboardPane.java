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
package org.jackhuang.hmcl.community.dashboard;

import com.jfoenix.controls.JFXButton;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.community.ui.CommunitySettingsPanel;
import org.jackhuang.hmcl.community.update.CommunityUpdateDialog;
import org.jackhuang.hmcl.community.update.CommunityUpdateNotifier;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.main.MainPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 首页仪表盘面板：标题行（设置/编辑）+ 网格 + 编辑工具栏。
///
/// 由 [CommunityDashboard#mount(MainPage)] 创建并插入 `MainPage` 的 `StackPane` 最底层。
/// 面板不改变 `MainPage` 的任何既有子节点、不覆盖 `getChildren()`，
/// 因此上游的更新气泡、右下启动按钮仍然显示在仪表盘之上。
///
/// 持久化：编辑“完成”、`DashboardPrefs` 写入（1 秒防抖）、释放时落盘；
/// 只读模式（`schema > 1`）下禁用编辑并展示提示。
@NotNullByDefault
public final class DashboardPane extends VBox
        implements DashboardGrid.Listener, CommunitySettingsPanel.DashboardActions {

    /// 偏好写盘的防抖时长。
    private static final Duration SAVE_DEBOUNCE = Duration.seconds(1);

    /// 首页来源。
    private final MainPage mainPage;

    /// 配置读写层。
    private final DashboardConfig config;

    /// 组件注册表。
    private final WidgetRegistry registry;

    /// 组件上下文。
    private final DashboardContext context;

    /// 网格。
    private final DashboardGrid grid;

    /// 标题行。
    private final HBox titleRow;

    /// 滚动容器。
    private final ScrollPane scrollPane;

    /// 编辑工具栏。
    private final HBox editBar;

    /// 空态提示。
    private final Label emptyLabel;

    /// 设置按钮。
    private final JFXButton settingsButton;

    /// 编辑按钮。
    private final JFXButton editButton;

    /// 添加组件菜单。
    private final MenuButton addButton;

    /// 取消按钮。
    private final JFXButton cancelButton;

    /// 完成按钮。
    private final JFXButton doneButton;

    /// 编辑中的工作副本；非编辑模式为 `null`。
    private @Nullable List<DashboardItem> working;

    /// 是否有未落盘修改。
    private boolean dirty;

    /// 防抖计时器。
    private final PauseTransition saveDebounce = new PauseTransition(SAVE_DEBOUNCE);

    /// 更新提示是否已尝试过（每个进程一次）。
    private static final AtomicBoolean UPDATE_PROMPTED = new AtomicBoolean();

    /// 创建仪表盘面板。
    ///
    /// @param mainPage 首页（提供仓库与选中实例属性）
    /// @param configPath `dashboard.json` 路径
    public DashboardPane(@NotNull MainPage mainPage, @NotNull Path configPath) {
        this.mainPage = mainPage;
        this.config = DashboardConfig.load(configPath);
        this.registry = WidgetRegistry.load();

        // 首次启动：没有配置文件时写入默认布局；已存在但用户清空时保持清空。
        if (config.firstRun() && config.items().isEmpty() && !registry.isEmpty()) {
            config.setItems(defaultItems());
            persist();
        }

        this.context = new DashboardContext(
                this::currentRepository,
                mainPage.currentGameProperty(),
                descriptor -> hideItem(descriptor.id()),
                config.prefs(this::markDirty));

        this.grid = new DashboardGrid(context, registry, this);
        this.grid.setItems(config.items());

        getStyleClass().add("community-dashboard");
        setSpacing(8);
        setPadding(new Insets(4));

        Label title = new Label(i18n("community.dashboard.title"));
        title.getStyleClass().add("title-label");
        settingsButton = new JFXButton(i18n("community.dashboard.settings"));
        settingsButton.getStyleClass().add("dialog-cancel");
        settingsButton.setOnAction(event -> CommunitySettingsPanel.open(
                getScene() == null ? null : getScene().getWindow()));
        editButton = new JFXButton(i18n("community.dashboard.edit"));
        editButton.getStyleClass().add("dialog-accept");
        editButton.setOnAction(event -> startEditing());
        editButton.setDisable(config.readOnly());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleRow = new HBox(8, title, spacer, settingsButton, editButton);
        titleRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPannable(false);
        scrollPane.getStyleClass().add("edge-to-edge");
        scrollPane.setMinHeight(0);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        emptyLabel = new Label(config.readOnly()
                ? i18n("community.dashboard.readonly.higher_schema")
                : i18n("community.dashboard.empty"));
        emptyLabel.getStyleClass().add("subtitle-label");
        emptyLabel.setWrapText(true);
        updateEmptyLabel();

        addButton = new MenuButton(i18n("community.dashboard.add"));
        addButton.getStyleClass().add("dialog-accept");
        cancelButton = new JFXButton(i18n("community.dashboard.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        cancelButton.setOnAction(event -> cancelEditing());
        doneButton = new JFXButton(i18n("community.dashboard.done"));
        doneButton.getStyleClass().add("dialog-accept");
        doneButton.setOnAction(event -> finishEditing());

        Label hint = new Label(i18n("community.dashboard.hint.drag"));
        hint.getStyleClass().add("subtitle-label");
        hint.setWrapText(true);
        Region editSpacer = new Region();
        HBox.setHgrow(editSpacer, Priority.ALWAYS);
        editBar = new HBox(8, hint, editSpacer, addButton, cancelButton, doneButton);
        editBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        editBar.setVisible(false);
        editBar.setManaged(false);

        getChildren().addAll(titleRow, emptyLabel, scrollPane, editBar);

        saveDebounce.setOnFinished(event -> persist());
        CommunitySettingsPanel.setDashboardActions(this);

        promptUpdateIfNeeded();
    }

    /// 返回配置读写层（供后续步骤读取置顶列表等）。
    ///
    /// @return 配置
    @NotNull
    public DashboardConfig config() {
        return config;
    }

    /// 返回当前组件上下文。
    ///
    /// @return 上下文
    @NotNull
    public DashboardContext context() {
        return context;
    }

    /// 是否有任何已启用且已登记的组件。
    ///
    /// @return 至少一个时返回 `true`
    public boolean hasEnabledWidgets() {
        for (DashboardItem item : config.items()) {
            if (item.enabled() && registry.isRegistered(item.id())) {
                return true;
            }
        }
        return false;
    }

    /// 关闭并释放全部组件，落盘未保存修改。
    public void dispose() {
        saveDebounce.stop();
        if (dirty) {
            persist();
        }
        grid.setItems(List.of());
        CommunitySettingsPanel.setDashboardActions(null);
    }

    /// 首页重新可见时刷新全部卡片。
    public void refresh() {
        grid.refreshAll();
    }

    /// 当前仓库；调用过早（未登录/未初始化）时返回 `null`。
    ///
    /// @return 仓库或 `null`
    @Nullable
    private HMCLGameRepository currentRepository() {
        try {
            return mainPage.getRepository();
        } catch (Throwable e) {
            return null;
        }
    }

    /// 默认布局：按描述符 `order` 排列，全部启用、使用默认跨度。
    ///
    /// @return 默认布局项
    @NotNull
    private List<DashboardItem> defaultItems() {
        List<DashboardWidgetDescriptor> descriptors = new ArrayList<>(registry.all());
        descriptors.sort(Comparator.comparingInt(DashboardWidgetDescriptor::order));
        List<DashboardItem> items = new ArrayList<>(descriptors.size());
        for (DashboardWidgetDescriptor descriptor : descriptors) {
            items.add(new DashboardItem(
                    descriptor.id(), true, descriptor.order(),
                    descriptor.defaultColumns(), descriptor.defaultRows()));
        }
        return items;
    }

    /// 标记有未保存修改，并触发 1 秒防抖落盘。
    private void markDirty() {
        dirty = true;
        saveDebounce.playFromStart();
    }

    /// 立即落盘；失败只记日志（不影响 UI）。
    private void persist() {
        try {
            config.save();
            dirty = false;
        } catch (Throwable e) {
            LOG.warning("Failed to save dashboard config", e);
        }
    }

    /// 进入编辑模式：复制工作副本并显示工具栏。
    @Override
    public void startEditing() {
        if (config.readOnly() || working != null) {
            return;
        }
        working = new ArrayList<>(config.items());
        grid.setItems(working);
        grid.setEditMode(true);
        setEditingUi(true);
        rebuildAddMenu();
    }

    /// 应用工作副本到网格（编辑期间每次操作后调用）。
    private void applyWorking() {
        if (working == null) {
            return;
        }
        // 统一重排 order，避免多次交换后 order 出现重复/稀疏。
        for (int i = 0; i < working.size(); i++) {
            working.get(i).setOrder(i * 10);
        }
        working.sort(Comparator.comparingInt(DashboardItem::order));
        grid.setItems(working);
        rebuildAddMenu();
        updateEmptyLabel();
    }

    /// 完成编辑：写回配置并落盘。
    private void finishEditing() {
        if (working == null) {
            return;
        }
        config.setItems(working);
        persist();
        working = null;
        grid.setEditMode(false);
        setEditingUi(false);
        updateEmptyLabel();
    }

    /// 取消编辑：丢弃工作副本。
    private void cancelEditing() {
        if (working == null) {
            return;
        }
        working = null;
        grid.setEditMode(false);
        grid.setItems(config.items());
        setEditingUi(false);
    }

    /// 按当前配置刷新空态提示。
    private void updateEmptyLabel() {
        boolean empty = !hasEnabledWidgets();
        emptyLabel.setVisible(empty);
        emptyLabel.setManaged(empty);
    }

    /// 切换编辑相关控件可见性。
    ///
    /// @param editing 编辑模式
    private void setEditingUi(boolean editing) {
        editBar.setVisible(editing);
        editBar.setManaged(editing);
        editButton.setDisable(editing || config.readOnly());
        settingsButton.setDisable(editing);
    }

    /// 重建“添加组件”菜单：列出尚未启用/未在布局中的组件。
    private void rebuildAddMenu() {
        addButton.getItems().clear();
        List<DashboardItem> current = working != null ? working : config.items();
        for (DashboardWidgetDescriptor descriptor : registry.all()) {
            DashboardItem existing = null;
            for (DashboardItem item : current) {
                if (item.id().equals(descriptor.id())) {
                    existing = item;
                    break;
                }
            }
            if (existing != null && existing.enabled()) {
                continue;
            }
            DashboardItem found = existing;
            MenuItem menuItem = new MenuItem(i18n(descriptor.nameKey()));
            menuItem.setOnAction(event -> addItem(descriptor, found));
            addButton.getItems().add(menuItem);
        }
        addButton.setDisable(addButton.getItems().isEmpty());
    }

    /// 添加组件（已存在则重新启用）。
    ///
    /// @param descriptor 组件描述
    /// @param existing 已存在的布局项（可能为 `null`）
    private void addItem(@NotNull DashboardWidgetDescriptor descriptor, @Nullable DashboardItem existing) {
        if (working == null) {
            return;
        }
        if (existing != null) {
            existing.setEnabled(true);
        } else {
            working.add(new DashboardItem(
                    descriptor.id(), true, working.size() * 10,
                    descriptor.defaultColumns(), descriptor.defaultRows()));
        }
        applyWorking();
    }

    /// 隐藏组件（编辑中或直接隐藏）。
    ///
    /// @param id 组件 id
    private void hideItem(@NotNull String id) {
        if (working != null) {
            for (DashboardItem item : working) {
                if (item.id().equals(id)) {
                    item.setEnabled(false);
                }
            }
            applyWorking();
            return;
        }
        List<DashboardItem> items = new ArrayList<>();
        for (DashboardItem item : config.items()) {
            DashboardItem copy = item.copy();
            if (copy.id().equals(id)) {
                copy.setEnabled(false);
            }
            items.add(copy);
        }
        config.setItems(items);
        grid.setItems(config.items());
        persist();
        updateEmptyLabel();
    }

    /// 恢复默认布局（二次确认）。
    @Override
    public void resetLayout() {
        MessageDialogPane pane = new MessageDialogPane.Builder(
                i18n("community.dashboard.reset_confirm"),
                i18n("community.dashboard.reset"),
                MessageDialogPane.MessageType.QUESTION)
                .yesOrNo(() -> {
                    List<DashboardItem> defaults = defaultItems();
                    if (working != null) {
                        working = new ArrayList<>(defaults);
                        applyWorking();
                    } else {
                        config.setItems(defaults);
                        persist();
                        grid.setItems(config.items());
                        emptyLabel.setVisible(!hasEnabledWidgets());
                        emptyLabel.setManaged(emptyLabel.isVisible());
                    }
                }, null)
                .build();
        Controllers.dialog(pane);
    }

    /// 把组件按位移移动。
    ///
    /// @param id 组件 id
    /// @param delta `-1` 前移，`1` 后移
    @Override
    public void onMove(@NotNull String id, int delta) {
        if (working == null) {
            return;
        }
        int index = indexOf(working, id);
        if (index < 0) {
            return;
        }
        int target = Math.max(0, Math.min(working.size() - 1, index + delta));
        if (target == index) {
            return;
        }
        DashboardItem item = working.remove(index);
        working.add(target, item);
        applyWorking();
    }

    /// 修改组件跨度。
    ///
    /// @param id 组件 id
    /// @param colSpan 列跨度
    /// @param rowSpan 行跨度
    @Override
    public void onResize(@NotNull String id, int colSpan, int rowSpan) {
        if (working == null) {
            return;
        }
        for (DashboardItem item : working) {
            if (item.id().equals(id)) {
                item.setColSpan(colSpan);
                item.setRowSpan(rowSpan);
                applyWorking();
                return;
            }
        }
    }

    /// 隐藏组件。
    ///
    /// @param id 组件 id
    @Override
    public void onHide(@NotNull String id) {
        hideItem(id);
    }

    /// 拖拽排序。
    ///
    /// @param id 组件 id
    /// @param targetIndex 目标索引
    @Override
    public void onReorder(@NotNull String id, int targetIndex) {
        if (working == null) {
            return;
        }
        int index = indexOf(working, id);
        if (index < 0) {
            return;
        }
        int target = Math.max(0, Math.min(working.size() - 1, targetIndex));
        if (target == index) {
            return;
        }
        DashboardItem item = working.remove(index);
        working.add(target, item);
        applyWorking();
    }

    /// 查找组件索引。
    ///
    /// @param items 列表
    /// @param id 组件 id
    /// @return 索引；不存在返回 `-1`
    private static int indexOf(@NotNull List<DashboardItem> items, @NotNull String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /// 启动时检查一次更新（仅当配置了仓库）。
    private void promptUpdateIfNeeded() {
        if (!CommunityUpdateNotifier.isConfigured() || !UPDATE_PROMPTED.compareAndSet(false, true)) {
            return;
        }
        CommunityUpdateNotifier.checkAsync().whenComplete((result, error) ->
                Schedulers.javafx().execute(() -> {
                    if (error != null || result == null) {
                        return;
                    }
                    Optional<CommunityUpdateNotifier.UpdateInfo> info = result;
                    if (info.isPresent() && !CommunityUpdateDialog.isSkipped(info.get().version())) {
                        CommunityUpdateDialog.show(info.get());
                    }
                }));
    }
}
