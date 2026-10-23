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
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.community.dashboard.DashboardContext;
import org.jackhuang.hmcl.community.dashboard.DashboardWidget;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.launch.CommunityLaunchHooks;
import org.jackhuang.hmcl.community.launch.LaunchEvent;
import org.jackhuang.hmcl.community.launch.LaunchEventListener;
import org.jackhuang.hmcl.community.launch.LaunchEventKind;
import org.jackhuang.hmcl.community.ui.ManagementPage;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.ui.versions.WorldListPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.jackhuang.hmcl.ui.FXUtils.openFolder;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 最近存档卡：跨实例扫描存档，按最后游玩时间排序，点击直接用 quick-play 进入世界。
///
/// 扫描只在 io 线程进行；卡片不读取 `session.lock` 内容，是否运行由 [World#isLocked()] 判断。
/// 启动复用 [Versions#launchAndEnterWorld(HMCLGameRepository, String, String)]，因此仍然经过上游的
/// 账号选择、版本校验与 08 的启动钩子。
@NotNullByDefault
public final class RecentWorldsWidget extends DashboardWidget {

    /// 单实例最多扫描的世界数。
    private static final int MAX_PER_INSTANCE = 100;
    /// 全局最多扫描的世界数。
    private static final int MAX_TOTAL = 200;
    /// 绝对时间格式。
    private static final DateTimeFormatter ABSOLUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /// 卡片最多展示的存档数；更多存档进入“全部存档”。
    private static final int MAX_VISIBLE = 3;

    private final VBox rows = new VBox(4);
    private final Label empty = BuiltinWidgets.caption(i18n("community.worlds.empty"));
    private final Label partial = BuiltinWidgets.caption(i18n("community.worlds.partial"));
    private final JFXButton allButton = BuiltinWidgets.borderButton(i18n("community.worlds.all"), this::openAll);
    private final JFXButton refreshButton = BuiltinWidgets.borderButton(i18n("community.worlds.refresh"), this::requestRefresh);

    private List<WorldEntry> entries = List.of();
    private boolean partialFailure;
    private @Nullable Throwable loadError;
    private @Nullable String actionError;

    /// 构造最近存档卡。
    ///
    /// @param descriptor 组件描述
    /// @param context 组件上下文
    public RecentWorldsWidget(@NotNull DashboardWidgetDescriptor descriptor,
                              @NotNull DashboardContext context) {
        super(descriptor, context);
    }

    @Override
    @NotNull
    protected Node createContent() {
        empty.setVisible(false);
        partial.setVisible(false);
        partial.getStyleClass().add("community-warning");

        HBox footer = BuiltinWidgets.cardFooter(allButton, refreshButton);
        VBox body = new VBox(4, rows, empty, partial);
        body.setFillWidth(true);
        VBox.setVgrow(rows, Priority.ALWAYS);

        onDispose(installLaunchListener());
        return BuiltinWidgets.cardContent(body, footer);
    }

    @Override
    protected void refreshAsync() {
        loadAsync(() -> {
            try {
                return loadWorlds();
            } catch (Throwable e) {
                return new LoadResult(List.of(), true, e);
            }
        }, this::apply);
    }

    @NotNull
    private LoadResult loadWorlds() {
        HMCLGameRepository repository = context.repository().get();
        if (repository == null) {
            return new LoadResult(List.of(), false, null);
        }
        List<WorldEntry> result = new ArrayList<>();
        boolean failed = false;
        for (Version version : repository.getVersions()) {
            String instanceId = version.getId();
            Path savesDir = repository.getSavesDirectory(instanceId);
            if (!Files.isDirectory(savesDir)) {
                continue;
            }
            List<World> worlds;
            try {
                worlds = World.getWorlds(savesDir);
            } catch (Throwable e) {
                failed = true;
                LOG.warning("Failed to read saves of " + instanceId, e);
                continue;
            }
            int count = 0;
            for (World world : worlds) {
                if (count++ >= MAX_PER_INSTANCE) {
                    LOG.info("Truncated worlds of " + instanceId + " at " + MAX_PER_INSTANCE);
                    break;
                }
                String gameVersion = world.getGameVersion() == null ? "" : world.getGameVersion().toString();
                result.add(new WorldEntry(instanceId, world.getWorldName(), world.getFileName(),
                        world.getLastPlayed(), gameVersion, world.isLocked(), savesDir));
                if (result.size() >= MAX_TOTAL) {
                    LOG.info("Truncated recent worlds at " + MAX_TOTAL);
                    break;
                }
            }
            if (result.size() >= MAX_TOTAL) {
                break;
            }
        }
        result.sort(Comparator.comparingLong(WorldEntry::lastPlayed).reversed());
        return new LoadResult(result, failed, null);
    }

    private void apply(@NotNull LoadResult result) {
        this.entries = result.entries();
        this.partialFailure = result.partialFailure();
        this.loadError = result.error();
        this.actionError = null;
        render();
    }

    /// 渲染最近存档，最多 [MAX_VISIBLE] 条。
    private void render() {
        int visible = Math.min(MAX_VISIBLE, entries.size());
        rows.getChildren().clear();
        boolean hasError = loadError != null;
        boolean showPartial = (partialFailure || actionError != null) && !entries.isEmpty() && !hasError;
        empty.setVisible(entries.isEmpty() && !hasError);
        empty.setManaged(entries.isEmpty() && !hasError);
        partial.setVisible(showPartial);
        partial.setManaged(showPartial);
        partial.setText(actionError != null ? actionError : i18n("community.worlds.partial"));

        if (hasError) {
            rows.getChildren().add(BuiltinWidgets.body(i18n("community.worlds.error")));
            return;
        }
        for (int i = 0; i < visible; i++) {
            rows.getChildren().add(createRow(entries.get(i)));
        }
    }

    @NotNull
    private Node createRow(@NotNull WorldEntry entry) {
        JFXButton button = BuiltinWidgets.borderButton("", () -> openWorld(entry));
        button.getStyleClass().add("community-world-row");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setText(null);

        Label name = BuiltinWidgets.body(entry.worldName());
        Label detail = BuiltinWidgets.caption(entry.instanceId() + " · " + relative(entry.lastPlayed())
                + (entry.gameVersion().isEmpty() ? "" : " · " + entry.gameVersion())
                + (entry.locked() ? " · " + i18n("community.worlds.locked") : ""));
        VBox text = new VBox(1, name, detail);
        button.setGraphic(text);

        Tooltip tooltip = new Tooltip(absolute(entry.lastPlayed()) + "\n" + entry.savesDir().resolve(entry.fileName()));
        Tooltip.install(button, tooltip);

        ContextMenu menu = new ContextMenu();
        MenuItem list = new MenuItem(i18n("community.worlds.open_list"));
        list.setOnAction(event -> openWorldList(entry.instanceId()));
        MenuItem folder = new MenuItem(i18n("community.worlds.open_folder"));
        folder.setOnAction(event -> openFolder(entry.savesDir().resolve(entry.fileName())));
        menu.getItems().addAll(list, folder);
        button.setContextMenu(menu);
        return button;
    }

    private void openWorld(@NotNull WorldEntry entry) {
        HMCLGameRepository repository = context.repository().get();
        if (repository == null) {
            return;
        }
        if (entry.locked()) {
            openWorldList(entry.instanceId());
            return;
        }
        repository.setSelectedInstance(entry.instanceId());
        try {
            Versions.launchAndEnterWorld(repository, entry.instanceId(), entry.fileName());
        } catch (Throwable e) {
            LOG.warning("Failed to launch world " + entry.fileName() + " of " + entry.instanceId(), e);
            actionError = i18n("community.launch.failed",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            render();
        }
    }

    private void openWorldList(@NotNull String instanceId) {
        HMCLGameRepository repository = context.repository().get();
        if (repository == null) {
            return;
        }
        WorldListPage page = new WorldListPage();
        page.loadInstance(repository, instanceId);
        Controllers.navigate(page);
    }

    private void openAll() {
        HMCLGameRepository repository = context.repository().get();
        String selected = context.selectedInstanceId().getValue();
        if (repository != null && selected != null) {
            openWorldList(selected);
        } else {
            ManagementPage page = ManagementPage.getInstance();
            page.showInstances();
            Controllers.navigate(page);
        }
    }

    @NotNull
    private Runnable installLaunchListener() {
        LaunchEventListenerImpl listener = new LaunchEventListenerImpl();
        try {
            CommunityLaunchHooks.addListener(listener);
        } catch (Throwable e) {
            LOG.warning("Failed to listen launch events for recent worlds", e);
        }
        return () -> {
            try {
                CommunityLaunchHooks.removeListener(listener);
            } catch (Throwable e) {
                LOG.warning("Failed to remove launch listener for recent worlds", e);
            }
        };
    }

    /// 退出后延迟 5 秒刷新，避免与游戏进程写盘竞争。
    private final class LaunchEventListenerImpl implements LaunchEventListener {
        private final PauseTransition delay = new PauseTransition(Duration.seconds(5));

        private LaunchEventListenerImpl() {
            delay.setOnFinished(event -> requestRefresh());
        }

        @Override
        public void onLaunchEvent(@NotNull LaunchEvent event) {
            if (event.kind() == LaunchEventKind.EXITED_NORMAL
                    || event.kind() == LaunchEventKind.EXITED_ABNORMAL) {
                Schedulers.javafx().execute(delay::playFromStart);
            }
        }
    }

    @NotNull
    private static String relative(long epochMillis) {
        if (epochMillis <= 0) {
            return i18n("community.worlds.never");
        }
        long seconds = Math.max(0, (System.currentTimeMillis() - epochMillis) / 1000);
        if (seconds < 60) {
            return i18n("community.worlds.just_now");
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return i18n("community.worlds.minutes_ago", minutes);
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return i18n("community.worlds.hours_ago", hours);
        }
        long days = hours / 24;
        if (days < 7) {
            return i18n("community.worlds.days_ago", days);
        }
        if (days < 14) {
            return i18n("community.worlds.last_week");
        }
        return LocalDate.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).toString();
    }

    @NotNull
    private static String absolute(long epochMillis) {
        if (epochMillis <= 0) {
            return "-";
        }
        return ABSOLUTE.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()));
    }

    /// 扫描结果。
    private record LoadResult(@NotNull List<WorldEntry> entries, boolean partialFailure, @Nullable Throwable error) {
    }

    /// 世界条目。
    private record WorldEntry(
            @NotNull String instanceId,
            @NotNull String worldName,
            @NotNull String fileName,
            long lastPlayed,
            @NotNull String gameVersion,
            boolean locked,
            @NotNull Path savesDir) {
    }
}
