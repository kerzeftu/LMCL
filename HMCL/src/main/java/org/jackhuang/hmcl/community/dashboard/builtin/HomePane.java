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
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
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
import org.jackhuang.hmcl.community.quote.DailyQuote;
import org.jackhuang.hmcl.community.quote.DailyQuoteEvents;
import org.jackhuang.hmcl.community.quote.DailyQuoteService;
import org.jackhuang.hmcl.community.quote.DailyQuoteSettings;
import org.jackhuang.hmcl.community.quote.QuoteOrigin;
import org.jackhuang.hmcl.community.ui.CommunitySettingsPanel;
import org.jackhuang.hmcl.community.ui.m3.GlassEdgeFade;
import org.jackhuang.hmcl.community.ui.m3.M3SpikePane;
import org.jackhuang.hmcl.community.update.CommunityUpdateDialog;
import org.jackhuang.hmcl.community.update.CommunityUpdateNotifier;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
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
import java.time.Instant;
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
    ///
    /// 页面底部保留 8 px 留白：左列视口底边落在「页底 - 8」；右列再在内层框架
    /// 里内缩 [INSTANCE_VIEWPORT_BOTTOM_INSET]（视口底边 = 页底 - 64），底部渐隐带
    /// 外缘下方仍有页面背景，卡片不再直接贴住窗口底边。
    private static final Insets HOME_PADDING = new Insets(10, 12, 8, 12);

    /// 上游浮动启动按钮（230×57）会盖在主页右下角，旧实现曾用它作为右列框架的
    /// 底部外边距，把整列视口抬高到按钮上方。
    ///
    /// v3.17.34 起右列改用 [INSTANCE_VIEWPORT_BOTTOM_INSET] 把滚动视口底边抬到
    /// 按钮上方：视口底边 = 页底 - 8 - 56 = 页底 - 64，内容（含卡片 footer）在
    /// 任意滚动位置都不会进入 230×57 按钮的覆盖/点击区，底部条由渐隐带填充。
    /// 该常量仅作语义/文档参考（64 = 56 + 8），不得再当作外边距使用。
    ///
    /// @deprecated v3.17.32 起无引用，保留仅作语义参考；后续版本可删除。
    @Deprecated
    private static final int LAUNCH_PANE_CLEARANCE = 64;

    /// 右列滚动视口的底部内缩（像素）。
    ///
    /// 启动按钮高 57 px（`root.css .launch-pane`）：视口底边停在页底 - 64 px
    /// （页底 8 px + 内缩 56 px），最后一张卡的 footer 最高只能到页底 - 64 px，
    /// 落在按钮顶（页底 - 57 px）上方 7 px；滚动中途内容也不会进入按钮的
    /// 覆盖/点击区。底部 56 px 条由底部渐隐带填充，不再压住卡片 footer。
    private static final double INSTANCE_VIEWPORT_BOTTOM_INSET = 56;

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
    private @Nullable HomeAccountCard accountCard;

    /// 运行状态文案。
    private final Label runStatus = new Label();

    /// 问候语。
    private final Label greeting = new Label();

    /// 每日一言。
    private final Label quoteLabel = new Label();

    /// 每日一言的完整文本提示。
    private final Tooltip quoteTooltip = new Tooltip();

    /// 每日一言服务。
    private final DailyQuoteService quoteService = new DailyQuoteService();
    private @Nullable DailyQuote currentQuote;

    /// 每日一言自动刷新计时器。
    private final Timeline quoteTimer = new Timeline();

    /// 当前挂载的窗口。
    private @Nullable Window quoteWindow;

    /// 每日一言显隐变化。
    private final ChangeListener<Boolean> quoteVisibilityListener =
            (observable, oldValue, newValue) -> updateQuoteTimer();

    /// 窗口聚焦变化。
    private final ChangeListener<Boolean> quoteFocusListener =
            (observable, oldValue, newValue) -> updateQuoteTimer();

    /// Scene 的窗口变化。
    private final ChangeListener<Window> quoteWindowListener =
            (observable, oldValue, newValue) -> attachQuoteWindow(newValue);

    /// Scene 变化。
    private final ChangeListener<Scene> quoteSceneListener = (observable, oldScene, newScene) -> {
        if (oldScene != null) {
            oldScene.windowProperty().removeListener(quoteWindowListener);
        }
        detachQuoteWindow();
        if (newScene != null) {
            newScene.windowProperty().addListener(quoteWindowListener);
            attachQuoteWindow(newScene.getWindow());
        }
    };

    /// 设置页修改每日一言后刷新。
    private final Runnable quoteChangedListener = this::onQuoteSettingsChanged;

    /// 左信息栏独立滚动容器。
    private final ScrollPane infoScroll = new ScrollPane();

    /// 右实例栏独立滚动容器。
    private final ScrollPane instanceScroll = new ScrollPane();

    /// 左列顶部边缘渐隐带。
    private final GlassEdgeFade infoTopFade = new GlassEdgeFade(GlassEdgeFade.Edge.TOP);

    /// 右列顶部边缘渐隐带。
    private final GlassEdgeFade instanceTopFade = new GlassEdgeFade(GlassEdgeFade.Edge.TOP);

    /// 左列底部边缘渐隐带。
    private final GlassEdgeFade infoBottomFade = new GlassEdgeFade(GlassEdgeFade.Edge.BOTTOM);

    /// 右列底部边缘渐隐带。
    private final GlassEdgeFade instanceBottomFade = new GlassEdgeFade(GlassEdgeFade.Edge.BOTTOM);

    /// 两列滚动位置、视口或内容高度变化时重算上下过渡带的距离。
    private final InvalidationListener columnScrollListener = observable -> updateColumnEdgeDistances();

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
        // 页头自身下内边距 2 px + 此处 4 px = 页头与两列滚动区之间 6 px 可见间距。
        setSpacing(4);
        setFillWidth(true);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        // 页底留白 8 px 由 HOME_PADDING 提供；右列启动按钮安全区改由实例列内容 padding 承担。
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
        installQuoteRefresh();

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
        quoteTimer.stop();
        quoteTimer.getKeyFrames().clear();
        detachQuoteWindow();
        Scene quoteScene = getScene();
        if (quoteScene != null) {
            quoteScene.windowProperty().removeListener(quoteWindowListener);
        }
        sceneProperty().removeListener(quoteSceneListener);
        visibleProperty().removeListener(quoteVisibilityListener);
        DailyQuoteEvents.removeListener(quoteChangedListener);
        if (dirty) {
            dirty = false;
            persist();
        }
        Accounts.selectedAccountProperty().removeListener(accountListener);
        CommunityLaunchHooks.removeListener(launchListener);
        if (accountCard != null) {
            accountCard.dispose();
        }
        infoScroll.vvalueProperty().removeListener(columnScrollListener);
        infoScroll.viewportBoundsProperty().removeListener(columnScrollListener);
        instanceScroll.vvalueProperty().removeListener(columnScrollListener);
        instanceScroll.viewportBoundsProperty().removeListener(columnScrollListener);
        infoColumn.layoutBoundsProperty().removeListener(columnScrollListener);
        instanceColumn.layoutBoundsProperty().removeListener(columnScrollListener);
        infoTopFade.dispose();
        infoBottomFade.dispose();
        instanceTopFade.dispose();
        instanceBottomFade.dispose();
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
        updateColumnEdgeDistances();
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

    /// 初始化每日一言：内置回退、10 分钟计时、可见/聚焦控制与设置变更监听。
    private void installQuoteRefresh() {
        // 先用内置语料占位；设置从 io 线程读取，避免 FX 线程磁盘 IO。
        applyQuote(quoteService.initialQuote());
        applyQuoteConfiguration();
        quoteService.reloadConfigAsync(config -> {
            if (!disposed) {
                applyQuoteConfiguration();
                // 配置加载完成后按用户选中的本地源重选；否则会一直显示默认语料的句子。
                if (currentQuote == null || currentQuote.origin() == QuoteOrigin.BUILTIN) {
                    applyQuote(quoteService.initialQuote());
                }
            }
        });

        quoteTimer.getKeyFrames().setAll(new KeyFrame(Duration.minutes(10), event -> {
            if (!disposed && quoteService.canAutoRefresh(Instant.now())) {
                quoteService.refresh(false, result -> {
                    if (!disposed) {
                        applyQuote(result.quote());
                    }
                });
            }
        }));
        quoteTimer.setCycleCount(Animation.INDEFINITE);

        sceneProperty().addListener(quoteSceneListener);
        visibleProperty().addListener(quoteVisibilityListener);
        DailyQuoteEvents.addListener(quoteChangedListener);
        if (getScene() != null) {
            getScene().windowProperty().addListener(quoteWindowListener);
            attachQuoteWindow(getScene().getWindow());
        }
    }

    /// 应用一条每日一言。
    private void applyQuote(@NotNull DailyQuote quote) {
        currentQuote = quote;
        String line = quote.displayLine();
        quoteLabel.setText(line);
        quoteTooltip.setText(line);
    }

    /// 根据配置显示/隐藏每日一言，并更新计时器状态。
    private void applyQuoteConfiguration() {
        DailyQuoteSettings.Config config = quoteService.config();
        boolean visible = config.enabled();
        quoteLabel.setVisible(visible);
        quoteLabel.setManaged(visible);
        updateQuoteTimer();
    }

    /// 设置页修改每日一言配置后重新读取并刷新。
    private void onQuoteSettingsChanged() {
        if (disposed) {
            return;
        }
        quoteService.reloadConfigAsync(config -> {
            if (disposed) {
                return;
            }
            applyQuoteConfiguration();
            if (!config.enabled() || currentQuote == null || currentQuote.origin() == QuoteOrigin.BUILTIN) {
                applyQuote(quoteService.initialQuote());
            }
        });
    }

    /// 请求刷新每日一言；失败时服务会静默回退到内置静态语料。
    private void refreshQuote(boolean manual) {
        if (disposed) {
            return;
        }
        quoteService.refresh(manual, result -> {
            if (!disposed) {
                applyQuote(result.quote());
            }
        });
    }

    /// 仅在主页可见且窗口聚焦时运行 10 分钟计时器。
    private void updateQuoteTimer() {
        boolean active = !disposed
                && quoteLabel.isVisible()
                && isVisible()
                && getScene() != null
                && getScene().getWindow() != null
                && getScene().getWindow().isFocused();
        if (active) {
            // 主页重新可见/窗口重新聚焦时，如果已过刷新时间就立即补一次，
            // 而不是再等一个 10 分钟周期。
            if (quoteService.canAutoRefresh(Instant.now())) {
                refreshQuote(false);
            }
            if (quoteTimer.getStatus() != Animation.Status.RUNNING) {
                quoteTimer.play();
            }
        } else {
            quoteTimer.stop();
        }
    }

    /// 绑定窗口焦点监听。
    private void attachQuoteWindow(@Nullable Window window) {
        if (quoteWindow == window) {
            updateQuoteTimer();
            return;
        }
        detachQuoteWindow();
        quoteWindow = window;
        if (window != null) {
            window.focusedProperty().addListener(quoteFocusListener);
        }
        updateQuoteTimer();
    }

    /// 解除窗口焦点监听。
    private void detachQuoteWindow() {
        if (quoteWindow != null) {
            quoteWindow.focusedProperty().removeListener(quoteFocusListener);
            quoteWindow = null;
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
        quoteLabel.getStyleClass().addAll("subtitle-label", "community-label", "community-daily-quote");
        quoteLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        quoteLabel.setMinWidth(0);
        quoteLabel.setMaxWidth(Double.MAX_VALUE);
        quoteLabel.setPrefWidth(0);
        quoteLabel.setTooltip(quoteTooltip);
        HBox.setHgrow(quoteLabel, Priority.ALWAYS);
        runStatus.setMinWidth(Region.USE_PREF_SIZE);
        HBox statusRow = new HBox(8, quoteLabel, runStatus);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setMaxWidth(Double.MAX_VALUE);
        VBox titles = new VBox(4, greeting, statusRow);

        JFXButton customizeButton = BuiltinWidgets.borderButton(
                i18n("community.home.customize"), this::startEditing);
        Region headerSpacer = BuiltinWidgets.spacer();
        HBox.setHgrow(headerSpacer, Priority.NEVER);
        HBox header = new HBox(10, titles, headerSpacer, customizeButton);
        HBox.setHgrow(titles, Priority.ALWAYS);
        if (Boolean.getBoolean("lmcl.m3.spike")) {
            header.getChildren().add(2, BuiltinWidgets.borderButton("M3 Spike", M3SpikePane::open));
        }
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 2, 0));
        return header;
    }

    /// 构建左右两条独立滚动的列（只执行一次）。
    ///
    /// 两列滚动区各自包进一个 `StackPane`，上下各叠放一条 [GlassEdgeFade]；
    /// 滚动位置、视口尺寸与内容高度共同驱动两条过渡带显示的距离：顶部带显示
    /// 已滚过的距离，底部带显示剩余可滚距离，滚到底时底部带自动隐藏。
    ///
    /// 页底留白 8 px 由 [HOME_PADDING] 提供；右列滚动视口再向内缩
    /// [INSTANCE_VIEWPORT_BOTTOM_INSET]（56 px），内容（含卡片 footer）不会进入
    /// 右下角 230×57 浮动启动按钮的覆盖/点击区，底部条由渐隐带填充。
    @NotNull
    private HBox buildColumns() {
        infoColumn.setFillWidth(true);
        infoColumn.setMinWidth(300);
        infoColumn.setMaxWidth(380);

        instanceColumn.setFillWidth(true);
        instanceColumn.setMinWidth(0);

        configureColumnScroll(infoScroll, INFO_WIDTH);
        configureColumnScroll(instanceScroll, Region.USE_COMPUTED_SIZE);

        infoScroll.setContent(infoColumn);
        instanceScroll.setContent(instanceColumn);

        StackPane infoFrame = createColumnFrame(infoScroll, infoTopFade, infoBottomFade, 0.0);
        StackPane instanceFrame = createColumnFrame(instanceScroll, instanceTopFade, instanceBottomFade,
                INSTANCE_VIEWPORT_BOTTOM_INSET);
        // 剩余宽度仍由右列吸收；宽度约束已复制到框架，布局结果与旧实现一致。
        HBox.setHgrow(instanceFrame, Priority.ALWAYS);

        infoScroll.vvalueProperty().addListener(columnScrollListener);
        infoScroll.viewportBoundsProperty().addListener(columnScrollListener);
        instanceScroll.vvalueProperty().addListener(columnScrollListener);
        instanceScroll.viewportBoundsProperty().addListener(columnScrollListener);
        infoColumn.layoutBoundsProperty().addListener(columnScrollListener);
        instanceColumn.layoutBoundsProperty().addListener(columnScrollListener);

        HBox columns = new HBox(COLUMN_GAP, infoFrame, instanceFrame);
        columns.setFillHeight(true);
        columns.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(columns, Priority.ALWAYS);
        updateColumnEdgeDistances();
        return columns;
    }

    /// 把一列滚动区包进可裁剪的框架，并在上下各叠放一条边缘渐隐带。
    ///
    /// 两条带都盖在框架的上下边缘：顶部带贴框架上沿，底部带贴内容截断处。
    /// 右列可传入 [bottomInset] 把滚动区视口底边抬到浮动启动按钮上方，
    /// 底部渐隐带同步上移同样的距离，使渐隐在内容截断处结束、下方内缩条
    /// 露出纯背景；这样内容在任意滚动位置都不会进入按钮的覆盖/点击区，
    /// 也不会出现“内容被硬切 + 清晰壁纸条”的观感。滚到底（剩余距离为 0）
    /// 时底部带由 [GlassEdgeFade] 自动隐藏。
    ///
    /// 框架复制滚动区的宽度约束，保证替换后左右列宽度与旧布局一致。
    ///
    /// @param pane 滚动区
    /// @param topFade 顶部渐隐带
    /// @param bottomFade 底部渐隐带
    /// @param bottomInset 滚动区相对框架底部的内缩（像素）；0 表示贴底
    /// @return 承载滚动区与上下渐隐带的框架
    @NotNull
    private static StackPane createColumnFrame(
            @NotNull ScrollPane pane,
            @NotNull GlassEdgeFade topFade,
            @NotNull GlassEdgeFade bottomFade,
            double bottomInset) {
        StackPane frame = new StackPane(pane, topFade, bottomFade);
        if (bottomInset > 0) {
            StackPane.setMargin(pane, new Insets(0, 0, bottomInset, 0));
            // 渐隐带与滚动视口底边对齐：内容在渐隐结束处被截断，下方内缩条是纯背景。
            StackPane.setMargin(bottomFade, new Insets(0, 0, bottomInset, 0));
        }
        frame.setMinWidth(pane.getMinWidth());
        frame.setPrefWidth(pane.getPrefWidth());
        frame.setMaxWidth(pane.getMaxWidth());
        frame.setMinHeight(0.0);
        frame.setMaxHeight(Double.MAX_VALUE);
        configureEdgeFade(topFade, Pos.TOP_CENTER);
        configureEdgeFade(bottomFade, Pos.BOTTOM_CENTER);
        return frame;
    }

    /// 配置一条边缘渐隐带：贴住对应边、固定 72 px 高、铺满整列宽度且不拦截鼠标。
    ///
    /// @param fade 渐隐带
    /// @param alignment 渐隐带在框架中的对齐方式
    private static void configureEdgeFade(@NotNull GlassEdgeFade fade, @NotNull Pos alignment) {
        StackPane.setAlignment(fade, alignment);
        fade.setPrefHeight(GlassEdgeFade.FADE_HEIGHT);
        fade.setMaxHeight(GlassEdgeFade.FADE_HEIGHT);
        fade.setMaxWidth(Double.MAX_VALUE);
        fade.setMouseTransparent(true);
    }

    /// 按两列的滚动位置刷新上下边缘渐隐带。
    private void updateColumnEdgeDistances() {
        updateColumnEdgeDistances(infoScroll, infoTopFade, infoBottomFade);
        updateColumnEdgeDistances(instanceScroll, instanceTopFade, instanceBottomFade);
    }

    /// 计算一列的滚动距离：顶部带显示已滚过的距离，底部带显示剩余可滚距离。
    ///
    /// 剩余距离 = `max(0, 内容高 - 视口高) - 已滚动距离`，滚到底时为 0，
    /// 底部带随之隐藏；内容不足一屏时两条带都保持隐藏。
    ///
    /// @param pane 滚动区
    /// @param top 顶部渐隐带
    /// @param bottom 底部渐隐带
    private static void updateColumnEdgeDistances(
            @NotNull ScrollPane pane,
            @NotNull GlassEdgeFade top,
            @NotNull GlassEdgeFade bottom) {
        @Nullable Node content = pane.getContent();
        double contentHeight = content == null ? 0.0 : content.getLayoutBounds().getHeight();
        double viewportHeight = pane.getViewportBounds().getHeight();
        double maxDistance = Math.max(0.0, contentHeight - viewportHeight);
        double offset = GlassEdgeFade.scrollDistance(pane.getVvalue(), contentHeight, viewportHeight);
        top.setDistance(offset);
        bottom.setDistance(maxDistance - offset);
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
        infoColumn.getChildren().add(BuiltinWidgets.materialCard(accountCard, 16, "account"));
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
        column.getChildren().add(BuiltinWidgets.materialCard(widget, 16, id));
        widget.initialize();
        widget.requestRefresh();
    }

    /// 构建“当前账号”卡片；账户列表与新建入口也在这里。
    @NotNull
    private HomeAccountCard buildAccountCard() {
        return new HomeAccountCard();
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
