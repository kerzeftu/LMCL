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
 *
 * Modified by LMCL team on 2026-10-22: community home page which replaces the
 * upstream 200px text sidebar with a two-column, Axolotl-inspired layout.
 */
package org.jackhuang.hmcl.community.dashboard.builtin;

import org.jackhuang.hmcl.community.ui.CommunityStyles;
import com.jfoenix.controls.JFXButton;
import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.community.dashboard.DashboardConfig;
import org.jackhuang.hmcl.community.dashboard.DashboardContext;
import org.jackhuang.hmcl.community.dashboard.DashboardItem;
import org.jackhuang.hmcl.community.dashboard.DashboardWidget;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.dashboard.WidgetRegistry;
import org.jackhuang.hmcl.community.launch.CommunityLaunchHooks;
import org.jackhuang.hmcl.community.launch.LaunchEvent;
import org.jackhuang.hmcl.community.launch.LaunchEventListener;
import org.jackhuang.hmcl.community.launch.LaunchEventKind;
import org.jackhuang.hmcl.community.ui.CommunitySettingsPanel;
import org.jackhuang.hmcl.community.ui.m3.M3Settings;
import org.jackhuang.hmcl.community.ui.m3.M3SpikePane;
import org.jackhuang.hmcl.community.update.CommunityUpdateDialog;
import org.jackhuang.hmcl.community.update.CommunityUpdateNotifier;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.community.ui.ManagementPage;
import org.jackhuang.hmcl.ui.account.AccountAdvancedListItem;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.main.LauncherSettingsPage;
import org.jackhuang.hmcl.ui.main.MainPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Axolotl 风格的社区主页：顶部导航/账户/运行状态，下方主列 + 右侧信息栏。
///
/// 与旧 [org.jackhuang.hmcl.community.dashboard.DashboardPane] 的差异：
/// - 不再使用可拖拽的 12 行网格，而是固定的“主列 + 右栏”分栏；
/// - 上游 200px 文本侧栏已移除；实例/下载/设置入口在全局 64px 侧栏，账户与实例管理入口在左栏账户卡（打开“管理”页）；
/// - 组件仍由 [WidgetRegistry] 与 [DashboardWidget] 提供，显隐/重置写入
///   `dashboard.json`，保留用户已有的 `pinnedInstanceIds` 与 `widgetProps`；
/// - 自定义入口跳转“设置 > 外观”，编辑区内嵌在 `PersonalizationPage`。
///
/// 线程：构造与所有 UI 操作只在 FX 线程；`dashboard.json` 读盘在构造时同步完成
/// （小文件，与上游 `DashboardPane` 一致），组件的磁盘读取仍走 `DashboardWidget#loadAsync`。
@NotNullByDefault
public final class HomePane extends VBox implements CommunitySettingsPanel.DashboardActions {

    /// 更新提示只尝试一次（与旧 `DashboardPane` 的静态去重一致）。
    private static final AtomicBoolean UPDATE_PROMPTED = new AtomicBoolean();

    /// 默认放在左信息栏的组件；其余组件默认放右栏。
    ///
    /// 这里只描述默认栏位，不再描述高度。卡片高度由标题、内容与 footer 的
    /// `prefHeight` 自动计算，避免内容不足时出现固定长度的空白条。
    private static final Set<String> LEFT_COLUMN_WIDGETS = Set.of(
            "activity-calendar",
            "recent-worlds");

    /// 左信息栏宽度（像素）。
    private static final int INFO_WIDTH = 340;

    /// 主列/信息栏间距（像素）。
    private static final int COLUMN_GAP = 16;

    /// 主页四周留白（像素）；避免卡片/滚动条紧贴窗口边框。
    private static final Insets HOME_PADDING = new Insets(10, 12, 12, 12);

    /// 首页。
    private final MainPage mainPage;

    /// `dashboard.json` 的读写层。
    private final DashboardConfig config;

    /// 组件注册表。
    private final WidgetRegistry registry;

    /// 组件上下文。
    private final DashboardContext context;

    /// 当前已创建的组件。
    private final Map<String, DashboardWidget> widgets = new LinkedHashMap<>();

    /// 左栏（固定账号卡 + 用户放到左栏的组件）。
    private final VBox infoColumn = new VBox(12);

    /// 右栏（用户放到右栏的组件）。
    private final VBox instanceColumn = new VBox(12);

    /// 固定账号卡；重建左右栏时复用，避免重复绑定全局账号属性。
    private @Nullable VBox accountCard;

    /// 运行状态文案。
    private final Label runStatus = new Label();

    /// 问候语。
    private final Label greeting = new Label();

    /// 左信息栏独立滚动容器。
    private final ScrollPane infoScroll = new ScrollPane();

    /// 右实例栏独立滚动容器。
    private final ScrollPane instanceScroll = new ScrollPane();

    /// 落盘防抖。
    private final PauseTransition saveDebounce = new PauseTransition(Duration.seconds(1));

    /// 账户切换监听。
    private final ChangeListener<Account> accountListener = (observable, oldValue, newValue) -> {
        greeting.setText(greetingText());
    };

    /// 启动事件监听。
    private final LaunchEventListener launchListener = this::onLaunchEvent;

    /// 正在启动的实例。
    private final Set<String> launching = new LinkedHashSet<>();

    /// 正在运行的实例。
    private final Set<String> running = new LinkedHashSet<>();

    /// 当前挂载的主页；设置页保存后用它即时刷新。
    private static @Nullable WeakReference<HomePane> active;

    /// 是否有未落盘的变更。
    private boolean dirty;

    /// 是否已释放。
    private boolean disposed;

    /// 构造主页。
    ///
    /// @param mainPage 上游首页（提供 repository / currentGame）
    /// @param configPath `dashboard.json` 路径
    public HomePane(@NotNull MainPage mainPage, @NotNull Path configPath) {
        this.mainPage = mainPage;
        active = new WeakReference<>(this);
        this.config = DashboardConfig.load(configPath);
        this.registry = WidgetRegistry.load();
        this.context = new DashboardContext(
                this::currentRepository,
                mainPage.currentGameProperty(),
                this::hideWidget,
                config.prefs(this::markDirty));

        CommunityStyles.apply(this);
        getStyleClass().add("community-home");
        setSpacing(12);
        setFillWidth(true);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setPadding(HOME_PADDING);

        if (!config.readOnly() && config.firstRun() && config.items().isEmpty()) {
            config.setItems(defaultItems());
            persist();
        }

        saveDebounce.setOnFinished(event -> {
            dirty = false;
            persist();
        });

        getChildren().add(buildHeader());
        getChildren().add(buildColumns());

        rebuildBody();

        Accounts.selectedAccountProperty().addListener(accountListener);
        CommunityLaunchHooks.addListener(launchListener);
        CommunitySettingsPanel.setDashboardActions(this);
        updateRunStatus();
        promptUpdateIfNeeded();
    }

    /// 返回是否至少有一个可展示组件；组件注册表为空时首页回退到上游。
    ///
    /// @return 是否有组件
    public boolean hasEnabledWidgets() {
        return !registry.isEmpty();
    }

    /// 释放所有组件与监听器。
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        saveDebounce.stop();
        if (dirty) {
            dirty = false;
            persist();
        }
        Accounts.selectedAccountProperty().removeListener(accountListener);
        CommunityLaunchHooks.removeListener(launchListener);
        for (DashboardWidget widget : widgets.values()) {
            try {
                widget.dispose();
            } catch (Throwable e) {
                LOG.warning("Failed to dispose home widget " + widget.descriptor().id(), e);
            }
        }
        widgets.clear();
        CommunitySettingsPanel.setDashboardActions(null);
        WeakReference<HomePane> ref = active;
        if (ref != null && ref.get() == this) {
            active = null;
        }
    }

    /// 刷新全部组件（从设置页返回时使用）。
    public void refresh() {
        for (DashboardWidget widget : widgets.values()) {
            widget.requestRefresh();
        }
    }

    /// 恢复默认显隐。
    @Override
    public void resetLayout() {
        if (config.readOnly()) {
            return;
        }
        config.setItems(defaultItems());
        persist();
        rebuildBody();
    }

    /// 打开设置 > 外观中的主页组件自定义区。
    @Override
    public void startEditing() {
        if (config.readOnly()) {
            Controllers.dialog(i18n("community.home.readonly"), null, MessageDialogPane.MessageType.WARNING);
            return;
        }
        LauncherSettingsPage page = Controllers.getSettingsPage();
        page.showHomeCustomization();
        Controllers.navigate(page);
    }

    /// 设置页保存主页配置后，从磁盘重载并重建左右栏。
    public void reloadFromDisk() {
        if (disposed) {
            return;
        }
        config.reload();
        rebuildBody();
    }

    /// 通知当前挂载的主页配置已变化；没有挂载时什么都不做。
    public static void notifyConfigurationChanged() {
        WeakReference<HomePane> ref = active;
        HomePane pane = ref != null ? ref.get() : null;
        if (pane != null && !pane.disposed) {
            pane.reloadFromDisk();
        }
    }

    /// 构建顶部：问候与运行状态。
    ///
    /// 实例/下载/设置入口在全局 `CommunitySideBar`；账户入口在左栏账户卡（打开 `ManagementPage` 的账户页）。
    /// 页头右侧只保留“自定义”按钮，用于首页组件显隐设置，避免与全局导航重复。
    @NotNull
    private HBox buildHeader() {
        greeting.setText(greetingText());
        greeting.getStyleClass().addAll("title-label", "community-headline");

        runStatus.getStyleClass().addAll("subtitle-label", "community-label", "community-status", "community-status-idle");
        HBox statusRow = new HBox(8, BuiltinWidgets.caption(i18n("community.home.subtitle")), runStatus);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        VBox titles = new VBox(4, greeting, statusRow);

        JFXButton customizeButton = BuiltinWidgets.borderButton(
                i18n("community.home.customize"), this::startEditing);
        HBox header = new HBox(10, titles, BuiltinWidgets.spacer(), customizeButton);
        if (M3Settings.isM3Enabled()) {
            Label m3Badge = new Label("M3");
            m3Badge.getStyleClass().add("community-m3-badge");
            header.getChildren().add(2, m3Badge);
        }
        if (Boolean.getBoolean("lmcl.m3.spike")) {
            header.getChildren().add(2, BuiltinWidgets.borderButton("M3 Spike", M3SpikePane::open));
        }
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 2, 0));
        return header;
    }

    /// 构建左右两条独立滚动的列（只执行一次）。
    @NotNull
    private HBox buildColumns() {
        infoColumn.setFillWidth(true);
        infoColumn.setMinWidth(300);
        infoColumn.setMaxWidth(380);

        instanceColumn.setFillWidth(true);
        instanceColumn.setMinWidth(0);

        configureColumnScroll(infoScroll, INFO_WIDTH);
        configureColumnScroll(instanceScroll, Region.USE_COMPUTED_SIZE);
        HBox.setHgrow(instanceScroll, Priority.ALWAYS);

        infoScroll.setContent(infoColumn);
        instanceScroll.setContent(instanceColumn);

        HBox columns = new HBox(COLUMN_GAP, infoScroll, instanceScroll);
        columns.setFillHeight(true);
        columns.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(columns, Priority.ALWAYS);
        return columns;
    }

    /// 配置单列滚动：垂直滚动、无横向滚动、内容随视口宽度调整。
    private void configureColumnScroll(@NotNull ScrollPane pane, double prefWidth) {
        pane.setFitToWidth(true);
        pane.setPannable(true);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pane.setPrefWidth(prefWidth);
        pane.setMinWidth(300);
        pane.setMaxWidth(prefWidth == Region.USE_COMPUTED_SIZE ? Double.MAX_VALUE : 420);
        pane.setMaxHeight(Double.MAX_VALUE);
        pane.getStyleClass().add("edge-to-edge");
    }

    /// 重建左栏与右栏。
    ///
    /// 栏位与顺序来自 `dashboard.json` 的 `column`/`order`；配置中缺失的组件
    /// 按注册表默认值补齐，保证升级后新增组件仍能显示。账号卡固定在左栏顶部，
    /// 不参与组件排序。
    private void rebuildBody() {
        for (DashboardWidget widget : widgets.values()) {
            try {
                widget.dispose();
            } catch (Throwable e) {
                LOG.warning("Failed to dispose home widget " + widget.descriptor().id(), e);
            }
        }
        widgets.clear();
        infoColumn.getChildren().clear();
        instanceColumn.getChildren().clear();

        if (accountCard == null) {
            accountCard = buildAccountCard();
        }
        infoColumn.getChildren().add(accountCard);
        for (DashboardItem item : itemsForHome()) {
            if (!item.enabled()) {
                continue;
            }
            String column = item.column() != null ? item.column() : defaultColumn(item.id());
            VBox target = DashboardItem.COLUMN_LEFT.equals(column) ? infoColumn : instanceColumn;
            addWidget(target, item.id());
        }

        if (instanceColumn.getChildren().isEmpty()) {
            Label empty = BuiltinWidgets.body(i18n("community.home.empty"));
            empty.getStyleClass().add("subtitle-label");
            instanceColumn.getChildren().add(empty);
        }

        // 左右列分别作为两个 ScrollPane 的内容，互不影响滚动位置。
    }

    /// 返回主页要渲染的完整组件列表（含配置中缺失的默认组件），按 `order` 稳定排序。
    private List<DashboardItem> itemsForHome() {
        Map<String, DashboardItem> configured = new LinkedHashMap<>();
        for (DashboardItem item : config.items()) {
            if (registry.byId(item.id()) != null && !configured.containsKey(item.id())) {
                configured.put(item.id(), item);
            }
        }

        List<DashboardItem> result = new ArrayList<>(configured.values());
        for (DashboardWidgetDescriptor descriptor : registry.all()) {
            if (!descriptor.isValid() || configured.containsKey(descriptor.id())) {
                continue;
            }
            result.add(new DashboardItem(
                    descriptor.id(),
                    true,
                    descriptor.order(),
                    descriptor.defaultColumns(),
                    descriptor.defaultRows(),
                    defaultColumn(descriptor.id())));
        }
        result.sort(Comparator.comparingInt(DashboardItem::order));
        return result;
    }

    /// 组件默认栏位：游玩记录/日历在左，其余在右。
    static String defaultColumn(@NotNull String id) {
        return LEFT_COLUMN_WIDGETS.contains(id)
                ? DashboardItem.COLUMN_LEFT
                : DashboardItem.COLUMN_RIGHT;
    }

    /// 添加一个组件；未登记时跳过。
    ///
    /// 不设置固定高度：`minHeight` 取 `prefHeight`，卡片内容多高就多高；
    /// 各列滚动由所属 `ScrollPane` 负责。组件内部仍需把多余高度让给 body，
    /// 使 footer 在卡片被父容器拉高时保持贴底。
    private void addWidget(@NotNull VBox column, @NotNull String id) {
        DashboardWidget widget = registry.create(id, context);
        if (widget == null) {
            return;
        }
        widget.setMinHeight(Region.USE_PREF_SIZE);
        widget.setPrefHeight(Region.USE_COMPUTED_SIZE);
        widget.setMaxHeight(Region.USE_PREF_SIZE);
        widget.setMaxWidth(Double.MAX_VALUE);
        widgets.put(id, widget);
        column.getChildren().add(widget);
        widget.initialize();
        widget.requestRefresh();
    }

    /// 构建“当前账号”卡片；账户列表入口也从这里进入。
    @NotNull
    private VBox buildAccountCard() {
        Label title = new Label(i18n("community.home.account.title"));
        title.getStyleClass().addAll("title-label", "community-title");

        AccountAdvancedListItem item = new AccountAdvancedListItem();
        item.accountProperty().bind(Accounts.selectedAccountProperty());
        item.setOnAction(event -> {
            ManagementPage page = ManagementPage.getInstance();
            page.showAccounts();
            Controllers.navigate(page);
        });
        item.setPrefHeight(48);

        VBox card = new VBox(6, title, item);
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(Region.USE_PREF_SIZE);
        return card;
    }

    /// 读取实例仓库；失败返回 `null`。
    @Nullable
    private HMCLGameRepository currentRepository() {
        try {
            return mainPage.getRepository();
        } catch (Throwable e) {
            return null;
        }
    }

    /// 生成全部组件启用的默认布局。
    @NotNull
    private List<DashboardItem> defaultItems() {
        List<DashboardItem> items = new ArrayList<>();
        for (DashboardWidgetDescriptor descriptor : registry.all()) {
            if (!descriptor.isValid()) {
                continue;
            }
            items.add(new DashboardItem(
                    descriptor.id(),
                    true,
                    descriptor.order(),
                    descriptor.defaultColumns(),
                    descriptor.defaultRows(),
                    defaultColumn(descriptor.id())));
        }
        return items;
    }

    /// 由组件回调隐藏自身。
    private void hideWidget(@NotNull DashboardWidgetDescriptor descriptor) {
        if (config.readOnly()) {
            return;
        }
        List<DashboardItem> items = new ArrayList<>();
        boolean found = false;
        for (DashboardItem item : config.items()) {
            if (item.id().equals(descriptor.id())) {
                items.add(new DashboardItem(item.id(), false, item.order(), item.colSpan(), item.rowSpan(), item.column()));
                found = true;
            } else {
                items.add(item.copy());
            }
        }
        if (!found) {
            items.add(new DashboardItem(
                    descriptor.id(),
                    false,
                    descriptor.order(),
                    descriptor.defaultColumns(),
                    descriptor.defaultRows(),
                    defaultColumn(descriptor.id())));
        }
        config.setItems(items);
        persist();
        rebuildBody();
    }

    /// 标记需要落盘。
    private void markDirty() {
        dirty = true;
        saveDebounce.playFromStart();
    }

    /// 立即落盘。
    private void persist() {
        if (config.readOnly()) {
            return;
        }
        try {
            config.save();
        } catch (IOException e) {
            LOG.warning("Failed to save home configuration", e);
        }
    }

    /// 切换账户显示。
    @NotNull
    private String greetingText() {
        Account account = Accounts.getSelectedAccount();
        if (account == null) {
            return i18n("community.home.greeting.guest");
        }
        String profileName = account.getProfileName();
        String name = StringUtils.isBlank(profileName) ? account.getProfileID().toString() : profileName;
        return i18n("community.home.greeting", name);
    }

    /// 更新运行状态文案。
    private void updateRunStatus() {
        if (disposed) {
            return;
        }
        runStatus.getStyleClass().removeAll("community-status-ok", "community-status-warn", "community-status-idle");
        if (!launching.isEmpty()) {
            runStatus.setText(i18n("community.home.status.launching", first(launching)));
            runStatus.getStyleClass().add("community-status-warn");
        } else if (!running.isEmpty()) {
            runStatus.setText(i18n("community.home.status.running", first(running)));
            runStatus.getStyleClass().add("community-status-ok");
        } else {
            runStatus.setText(i18n("community.home.status.none"));
            runStatus.getStyleClass().add("community-status-idle");
        }
    }

    /// 取得集合中第一个 id。
    @NotNull
    private static String first(@NotNull Set<String> ids) {
        return ids.iterator().next();
    }

    /// 处理启动事件；事件可能来自任意线程。
    private void onLaunchEvent(@NotNull LaunchEvent event) {
        Schedulers.javafx().execute(() -> {
            if (disposed) {
                return;
            }
            LaunchEventKind kind = event.kind();
            if (kind == LaunchEventKind.REQUESTED) {
                launching.add(event.versionId());
            } else if (kind == LaunchEventKind.PROCESS_CREATED || kind == LaunchEventKind.SURVIVED_THRESHOLD) {
                launching.remove(event.versionId());
                running.add(event.versionId());
            } else if (kind == LaunchEventKind.EXITED_NORMAL
                    || kind == LaunchEventKind.EXITED_ABNORMAL
                    || kind == LaunchEventKind.ABORTED_BY_USER
                    || kind == LaunchEventKind.LAUNCH_FAILED) {
                launching.remove(event.versionId());
                running.remove(event.versionId());
            }
            updateRunStatus();
        });
    }

    /// 启动时检查一次更新（仅当配置了仓库）；只提示、不下载。
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
