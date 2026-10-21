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

import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.dashboard.DashboardContext;
import org.jackhuang.hmcl.community.dashboard.DashboardWidget;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.launch.CommunityLaunchHooks;
import org.jackhuang.hmcl.community.launch.LaunchEvent;
import org.jackhuang.hmcl.community.launch.LaunchEventKind;
import org.jackhuang.hmcl.community.launch.LaunchEventListener;
import org.jackhuang.hmcl.community.play.PlayRecordStore;
import org.jackhuang.hmcl.community.play.PlayRecordStore.DayRecord;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.ui.FXUtils.openFolder;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 游玩日历卡：最近 4/12/26 周的每日游玩时长热力图。
///
/// 数据来自 [PlayRecordStore]，卡片只读；打点由 [org.jackhuang.hmcl.community.play.PlayRecordTracker] 完成。
/// 格子只加 CSS 类，颜色由步骤 19 的 `community.css` 定义，不在 Java 里写颜色值以外的主题假设。
@NotNullByDefault
public final class ActivityCalendarWidget extends DashboardWidget {

    private static final double CELL_SIZE = 10;
    private static final double CELL_GAP = 2;

    private final Label summary = BuiltinWidgets.body("");
    private final Label hint = BuiltinWidgets.caption(i18n("community.calendar.hint"));
    private final GridPane grid = new GridPane();
    private final VBox box = new VBox(4);
    private HBox footer;
    private final PauseTransition refreshDelay = new PauseTransition(Duration.seconds(5));

    private Map<LocalDate, DayRecord> data = Map.of();
    private boolean enabled = true;
    private int renderedWeeks = -1;
    private boolean renderedCompact;

    /// 构造游玩日历卡。
    ///
    /// @param descriptor 组件描述
    /// @param context 组件上下文
    public ActivityCalendarWidget(@NotNull DashboardWidgetDescriptor descriptor,
                                  @NotNull DashboardContext context) {
        super(descriptor, context);
    }

    @Override
    @NotNull
    protected Node createContent() {
        grid.setHgap(CELL_GAP);
        grid.setVgap(CELL_GAP);
        grid.setAlignment(Pos.CENTER_LEFT);
        hint.getStyleClass().add("community-hint");

        footer = new HBox(8, hint, BuiltinWidgets.spacer(),
                BuiltinWidgets.borderButton(i18n("community.calendar.settings"), this::openSettings));
        footer.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(summary, grid, footer);
        box.setFillWidth(true);
        VBox.setVgrow(grid, Priority.ALWAYS);

        heightProperty().addListener((observable, oldValue, newValue) -> rebuild());
        widthProperty().addListener((observable, oldValue, newValue) -> rebuild());
        refreshDelay.setOnFinished(event -> requestRefresh());
        onDispose(installLaunchListener());
        return box;
    }

    /// 监听启动退出，延迟 5 秒刷新，避开打点线程落盘。
    @NotNull
    private Runnable installLaunchListener() {
        LaunchEventListener listener = event -> {
            if (event.kind() == LaunchEventKind.EXITED_NORMAL
                    || event.kind() == LaunchEventKind.EXITED_ABNORMAL) {
                Schedulers.javafx().execute(refreshDelay::playFromStart);
            }
        };
        try {
            CommunityLaunchHooks.addListener(listener);
        } catch (Throwable e) {
            LOG.warning("Failed to listen launch events for activity calendar", e);
        }
        return () -> {
            try {
                CommunityLaunchHooks.removeListener(listener);
            } catch (Throwable e) {
                LOG.warning("Failed to remove launch listener for activity calendar", e);
            }
        };
    }

    @Override
    protected void refreshAsync() {
        loadAsync(() -> {
            PlayRecordStore store = PlayRecordStore.get();
            return new LoadResult(store.load(), store.enabled());
        }, this::apply);
    }

    private void apply(@NotNull LoadResult result) {
        this.data = result.data();
        this.enabled = result.enabled();
        this.renderedWeeks = -1;
        rebuild();
    }

    private void rebuild() {
        int weeks = weeks();
        boolean compact = getHeight() < 180;
        if (weeks == renderedWeeks && compact == renderedCompact) {
            updateSummary();
            return;
        }
        renderedWeeks = weeks;
        renderedCompact = compact;
        double cellSize = compact ? 6 : CELL_SIZE;
        double cellGap = compact ? 0 : CELL_GAP;
        grid.setHgap(cellGap);
        grid.setVgap(cellGap);
        summary.setVisible(!compact);
        summary.setManaged(!compact);
        hint.setVisible(!compact);
        hint.setManaged(!compact);
        grid.getChildren().clear();

        LocalDate today = LocalDate.now();
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate start = thisMonday.minusWeeks(weeks - 1L);

        for (int week = 0; week < weeks; week++) {
            for (int day = 0; day < 7; day++) {
                LocalDate date = start.plusDays((long) week * 7 + day);
                Region cell = new Region();
                cell.setMinSize(cellSize, cellSize);
                cell.setPrefSize(cellSize, cellSize);
                cell.setMaxSize(cellSize, cellSize);
                cell.getStyleClass().add("community-calendar-cell");
                if (date.isAfter(today)) {
                    cell.setVisible(false);
                } else {
                    DayRecord record = data.get(date);
                    int level = levelOf(record);
                    cell.getStyleClass().add("community-activity-level-" + level);
                    if (record != null && record.estimatedSeconds() > 0) {
                        cell.getStyleClass().add("community-activity-estimated");
                    }
                    Tooltip.install(cell, new Tooltip(tooltip(date, record)));
                }
                grid.add(cell, week, day);
            }
        }
        updateSummary();
        hint.setText(enabled ? i18n("community.calendar.hint") : i18n("community.calendar.disabled"));
    }

    private void updateSummary() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        int days = 0;
        long seconds = 0;
        for (Map.Entry<LocalDate, DayRecord> entry : data.entrySet()) {
            if (!entry.getKey().isBefore(monthStart) && !entry.getKey().isAfter(today)) {
                if (entry.getValue().playSeconds() > 0) {
                    days++;
                }
                seconds += entry.getValue().playSeconds();
            }
        }
        summary.setText(i18n("community.calendar.summary", days, formatHours(seconds)));
    }

    private void openSettings() {
        PlayRecordStore store = PlayRecordStore.get();
        CheckBox enabledBox = new CheckBox(i18n("community.calendar.enabled"));
        enabledBox.setSelected(store.enabled());

        Label note = BuiltinWidgets.caption(i18n("community.calendar.enabled.note"));
        HBox buttons = new HBox(8,
                BuiltinWidgets.borderButton(i18n("community.calendar.open_folder"),
                        () -> openFolder(store.dataFile().getParent())),
                BuiltinWidgets.spacer(),
                BuiltinWidgets.borderButton(i18n("community.calendar.clear"), () -> {
                    Controllers.confirm(i18n("community.calendar.clear.confirm"), i18n("community.calendar.clear"), () -> {
                        CompletableFuture.runAsync(() -> {
                            try {
                                store.clear();
                            } catch (Throwable e) {
                                LOG.warning("Failed to clear play records", e);
                            }
                        }, Schedulers.io()).thenRunAsync(this::requestRefresh, Schedulers.javafx());
                    }, null);
                }));
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, enabledBox, note, buttons);
        enabledBox.setOnAction(event -> {
            boolean selected = enabledBox.isSelected();
            enabledBox.setDisable(true);
            CompletableFuture.runAsync(() -> {
                try {
                    store.setEnabled(selected);
                } catch (Throwable e) {
                    LOG.warning("Failed to save play record switch", e);
                }
            }, Schedulers.io()).thenRunAsync(() -> {
                enabledBox.setDisable(false);
                requestRefresh();
            }, Schedulers.javafx());
        });
        Controllers.dialog(BuiltinWidgets.dialogRoot(i18n("community.calendar.settings"), content, 420));
    }

    private int weeks() {
        if (getHeight() >= 300) {
            return 26;
        }
        return getWidth() >= 480 ? 12 : 4;
    }

    private static int levelOf(@Nullable DayRecord record) {
        if (record == null || record.playSeconds() <= 0) {
            return 0;
        }
        double hours = record.playSeconds() / 3600.0;
        if (hours < 0.5) {
            return 1;
        }
        if (hours < 2) {
            return 2;
        }
        if (hours < 5) {
            return 3;
        }
        return 4;
    }

    @NotNull
    private static String tooltip(@NotNull LocalDate date, @Nullable DayRecord record) {
        if (record == null || record.playSeconds() <= 0) {
            return date + "\n" + i18n("community.calendar.tooltip.empty");
        }
        return date + "\n" + i18n("community.calendar.tooltip", formatDuration(record.playSeconds()),
                record.sessions(), record.estimatedSeconds() > 0
                        ? i18n("community.calendar.tooltip.estimated", formatDuration(record.estimatedSeconds()))
                        : "");
    }

    @NotNull
    private static String formatHours(long seconds) {
        if (seconds < 60) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.1f", seconds / 3600.0);
    }

    @NotNull
    private static String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        return minutes / 60 + "h " + minutes % 60 + "m";
    }

    /// 一次读取的结果。
    private record LoadResult(@NotNull Map<LocalDate, DayRecord> data, boolean enabled) {
    }
}
