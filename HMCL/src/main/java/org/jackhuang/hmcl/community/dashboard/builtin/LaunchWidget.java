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
package org.jackhuang.hmcl.community.dashboard.builtin;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.dashboard.DashboardContext;
import org.jackhuang.hmcl.community.dashboard.DashboardWidget;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.launch.CommunityLaunchHooks;
import org.jackhuang.hmcl.community.launch.LaunchEvent;
import org.jackhuang.hmcl.community.launch.LaunchEventKind;
import org.jackhuang.hmcl.community.launch.LaunchEventListener;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.ui.versions.GameListPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.task.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.jackhuang.hmcl.ui.Controllers.navigate;
import static org.jackhuang.hmcl.ui.versions.Versions.addNewGame;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 首页启动卡：显示当前实例、最近启动时间并调用上游启动流程。
///
/// 卡片**不**自行创建 [org.jackhuang.hmcl.game.LauncherHelper]，启动一律走
/// [org.jackhuang.hmcl.ui.versions.Versions#launch]；启动状态只信
/// [CommunityLaunchHooks] 发出的 [LaunchEvent]，不读取进程数。
@NotNullByDefault
public final class LaunchWidget extends DashboardWidget {

    /// 最近启动时间展示格式。
    private static final DateTimeFormatter LAST_LAUNCH_FORMAT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /// 本次进程内各实例最近一次启动请求时间（08 暂不做持久化）。
    private static final Map<String, Instant> LAST_LAUNCH = new ConcurrentHashMap<>();

    /// 当前选中实例 id。
    private final Label instanceLabel = new Label();

    /// 游戏版本 / 最近启动时间。
    private final Label detailLabel = BuiltinWidgets.caption("");

    /// 启动中或运行中的状态行。
    private final Label statusLabel = BuiltinWidgets.caption("");

    /// 空态说明。
    private final Label emptyLabel = BuiltinWidgets.body("");

    /// 启动 / 切换按钮所在行。
    private final HBox actions = new HBox(8);

    /// 空态操作（新��实例 / 管理实例）。
    private final HBox emptyActions = new HBox(8);

    /// 启动按钮。
    private final JFXButton launchButton = BuiltinWidgets.raisedButton(
            i18n("community.launch.start"), this::launch);

    /// 切换实例按钮。
    private final JFXButton switchButton = BuiltinWidgets.borderButton(
            i18n("community.launch.switch"), this::showSwitchPopup);

    /// 新建实例按钮。
    private final JFXButton newInstanceButton = BuiltinWidgets.borderButton(
            i18n("community.launch.new_instance"), LaunchWidget::createInstance);

    /// 管理实例按钮。
    private final JFXButton manageButton = BuiltinWidgets.borderButton(
            i18n("community.launch.manage"), LaunchWidget::openInstanceList);

    /// 选中实例变化监听。
    private final ChangeListener<@Nullable String> selectionListener =
            (observable, oldValue, newValue) -> {
                selectedId = newValue;
                renderState();
            };

    /// 启动事件监听。
    private final LaunchEventListener launchListener = this::onLaunchEvent;

    /// 当前实例列表；未加载时为 `null`。
    private @Nullable List<Version> versions;

    /// 当前选中实例 id。
    private @Nullable String selectedId;

    /// 是否已有启动请求在途。
    private boolean launching;

    /// 是否已有游戏进程在运行。
    private boolean running;

    /// 上次点击启动的时间，用于 300ms 防抖。
    private long lastLaunchClickMillis;

    /// 构造启动卡。
    ///
    /// @param descriptor 组件描述
    /// @param context 组件上下文
    public LaunchWidget(@NotNull DashboardWidgetDescriptor descriptor, @NotNull DashboardContext context) {
        super(descriptor, context);
    }

    /// 创建卡片骨架。
    ///
    /// @return 内容节点
    @Override
    @NotNull
    protected Node createContent() {
        instanceLabel.setMaxWidth(Double.MAX_VALUE);
        instanceLabel.setMinWidth(0);
        instanceLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);

        detailLabel.setMinWidth(0);
        statusLabel.setMinWidth(0);
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        instanceLabel.setVisible(false);
        instanceLabel.setManaged(false);
        detailLabel.setVisible(false);
        detailLabel.setManaged(false);

        emptyLabel.setMinWidth(0);
        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);

        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getChildren().addAll(launchButton, switchButton);
        actions.setVisible(false);
        actions.setManaged(false);

        emptyActions.setAlignment(Pos.CENTER_LEFT);
        emptyActions.getChildren().addAll(newInstanceButton, manageButton);
        emptyActions.setVisible(false);
        emptyActions.setManaged(false);

        VBox box = new VBox(4, instanceLabel, detailLabel, statusLabel, actions, emptyLabel, emptyActions);
        box.setFillWidth(true);

        context.selectedInstanceId().addListener(selectionListener);
        onDispose(() -> context.selectedInstanceId().removeListener(selectionListener));

        CommunityLaunchHooks.addListener(launchListener);
        onDispose(() -> CommunityLaunchHooks.removeListener(launchListener));

        return box;
    }

    /// 重新读取实例列表与启动状态。
    @Override
    protected void refreshAsync() {
        HMCLGameRepository repository = context.repository().get();
        selectedId = context.selectedInstanceId().getValue();
        if (repository == null) {
            versions = List.of();
            renderState();
            return;
        }

        loadAsync(() -> {
            List<Version> list = new ArrayList<>(repository.getVersions());
            list.sort(Comparator
                    .comparing(Version::getReleaseTime, Comparator.nullsLast(Comparator.naturalOrder()))
                    .reversed());
            return list;
        }, list -> {
            versions = List.copyOf(list);
            String id = selectedId;
            CommunityLaunchHooks.LaunchSession session = id == null ? null : CommunityLaunchHooks.sessionOf(id);
            launching = session != null && session.process() == null;
            running = session != null && session.process() != null;
            renderState();
        });
    }

    /// 启动或切换状态变化时刷新卡片。
    private void renderState() {
        if (disposed) {
            return;
        }

        List<Version> list = versions;
        String id = selectedId;
        HMCLGameRepository repository = context.repository().get();
        Version version = findVersion(list, id);

        boolean hasVersions = list != null && !list.isEmpty();
        boolean valid = repository != null && version != null && id != null;

        setShown(instanceLabel, valid);
        setShown(detailLabel, valid);
        setShown(actions, valid);
        setShown(emptyLabel, !valid);
        setShown(emptyActions, !valid);

        if (valid) {
            instanceLabel.setText(id);
            String gameVersion = BuiltinWidgets.gameVersion(repository, version);
            Instant last = LAST_LAUNCH.get(id);
            String lastText = last == null
                    ? i18n("community.launch.never")
                    : LAST_LAUNCH_FORMAT.format(last);
            detailLabel.setText(i18n("community.launch.detail", gameVersion, lastText));
        } else if (!hasVersions) {
            emptyLabel.setText(i18n("community.launch.no_versions"));
        } else {
            emptyLabel.setText(i18n("community.launch.no_instance"));
        }

        switchButton.setVisible(hasVersions);
        switchButton.setManaged(hasVersions);

        boolean busy = launching || running;
        launchButton.setDisable(busy);
        if (launching) {
            launchButton.setText(i18n("community.launch.launching"));
        } else if (running) {
            launchButton.setText(i18n("community.launch.running"));
        } else {
            launchButton.setText(i18n("community.launch.start"));
        }
        switchButton.setDisable(busy);

        if (launching) {
            showStatus(i18n("community.launch.launching"));
        } else if (running) {
            showStatus(i18n("community.launch.running"));
        } else {
            showStatus(null);
        }
    }

    /// 显示或隐藏状态行。
    ///
    /// @param text 文案；`null` 表示隐藏
    private void showStatus(@Nullable String text) {
        boolean show = text != null;
        statusLabel.setVisible(show);
        statusLabel.setManaged(show);
        if (show) {
            statusLabel.setText(text);
        }
    }

    /// 查找实例；不存在或 id 为空时返回 `null`。
    ///
    /// @param list 实例列表
    /// @param id 实例 id
    /// @return 实例或 `null`
    private static @Nullable Version findVersion(@Nullable List<Version> list, @Nullable String id) {
        if (list == null || id == null) {
            return null;
        }
        for (Version version : list) {
            if (id.equals(version.getId())) {
                return version;
            }
        }
        return null;
    }

    /// 设置节点可见与托管状态。
    ///
    /// @param node 节点
    /// @param shown 是否显示
    private static void setShown(@NotNull Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    /// 调用上游启动流程；不创建 LauncherHelper。
    private void launch() {
        HMCLGameRepository repository = context.repository().get();
        List<Version> list = versions;
        String id = selectedId;
        if (repository == null || id == null || findVersion(list, id) == null || launching || running) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastLaunchClickMillis < 300) {
            return;
        }
        lastLaunchClickMillis = now;

        try {
            Versions.launch(repository, id);
        } catch (Throwable e) {
            LOG.warning("Failed to launch instance " + id, e);
            showStatus(i18n("community.launch.failed",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /// 新建实例。
    private static void createInstance() {
        try {
            addNewGame();
        } catch (Throwable e) {
            LOG.warning("Failed to open new instance dialog", e);
        }
    }

    /// 打开实例列表页。
    private static void openInstanceList() {
        try {
            navigate(new GameListPage());
        } catch (Throwable e) {
            LOG.warning("Failed to open instance list", e);
        }
    }

    /// 显示实例切换弹层。
    private void showSwitchPopup() {
        List<Version> list = versions;
        HMCLGameRepository repository = context.repository().get();
        if (list == null || repository == null) {
            return;
        }

        VBox menu = new VBox(2);
        JFXPopup[] holder = new JFXPopup[1];
        int shown = Math.min(20, list.size());
        for (int i = 0; i < shown; i++) {
            Version version = list.get(i);
            JFXButton item = new JFXButton(BuiltinWidgets.shorten(
                    version.getId() + "  " + BuiltinWidgets.gameVersion(repository, version), 42));
            item.setMaxWidth(Double.MAX_VALUE);
            item.setOnAction(event -> {
                try {
                    repository.setSelectedInstance(version.getId());
                } catch (Throwable e) {
                    LOG.warning("Failed to select instance " + version.getId(), e);
                }
                JFXPopup current = holder[0];
                if (current != null) {
                    current.hide();
                }
            });
            menu.getChildren().add(item);
        }
        if (list.size() > shown) {
            JFXButton more = new JFXButton(i18n("community.launch.switch.more"));
            more.setMaxWidth(Double.MAX_VALUE);
            more.setOnAction(event -> openInstanceList());
            menu.getChildren().add(more);
        }

        ScrollPane scroll = new ScrollPane(menu);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(Math.min(320, 32 + shown * 32));
        scroll.setPrefWidth(260);

        JFXPopup popup = new JFXPopup(scroll);
        holder[0] = popup;
        popup.setAutoHide(true);
        popup.show(switchButton, JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.RIGHT, 0, 4);
    }

    /// 处理启动事件；事件可能来自任意线程。
    ///
    /// @param event 事件
    private void onLaunchEvent(@NotNull LaunchEvent event) {
        if (event.kind() == LaunchEventKind.REQUESTED) {
            LAST_LAUNCH.put(event.versionId(), Instant.ofEpochMilli(event.timestamp()));
        }
        Schedulers.javafx().execute(() -> {
            if (disposed) {
                return;
            }
            String id = selectedId;
            if (id == null || !id.equals(event.versionId())) {
                return;
            }
            switch (event.kind()) {
                case REQUESTED -> {
                    launching = true;
                    running = false;
                }
                case PROCESS_CREATED, SURVIVED_THRESHOLD -> {
                    launching = false;
                    running = true;
                }
                case EXITED_NORMAL, EXITED_ABNORMAL, ABORTED_BY_USER, LAUNCH_FAILED -> {
                    launching = false;
                    running = false;
                }
                default -> {
                    return;
                }
            }
            renderState();
        });
    }

    /// 供单元测试重置会话内最近启动时间。
    static void clearLastLaunchForTesting() {
        LAST_LAUNCH.clear();
    }

    /// 供单元测试读取最近启动时间。
    ///
    /// @param versionId 实例 id
    /// @return 时间或 `null`
    static @Nullable Instant lastLaunchForTesting(@NotNull String versionId) {
        return LAST_LAUNCH.get(versionId);
    }

}
