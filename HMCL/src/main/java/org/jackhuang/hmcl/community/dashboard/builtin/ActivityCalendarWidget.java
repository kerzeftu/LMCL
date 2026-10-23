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
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import org.jackhuang.hmcl.community.launch.LaunchEventKind;
import org.jackhuang.hmcl.community.launch.LaunchEventListener;
import org.jackhuang.hmcl.community.play.PlayRecordStore;
import org.jackhuang.hmcl.community.play.PlayRecordStore.DayRecord;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.main.LauncherSettingsPage;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 游玩日历卡：固定展示最近 5 个自然周的每日游玩时长热力图。
///
/// 数据来自 [PlayRecordStore]，卡片只读；打点由 [org.jackhuang.hmcl.community.play.PlayRecordTracker] 完成。
/// 格子只加 CSS 类，颜色由步骤 19 的 `community.css` 定义，不在 Java 里写颜色值以外的主题假设。
@NotNullByDefault
public final class ActivityCalendarWidget extends DashboardWidget {

    private static final double CELL_SIZE = 22;
    private static final double CELL_GAP = 4;
    private static final String CALENDAR_DARK_CLASS = "community-calendar-on-dark";
    private static final String CALENDAR_LIGHT_CLASS = "community-calendar-on-light";

    private final Label summary = BuiltinWidgets.body("");
    private final Label disabledNote = BuiltinWidgets.caption("");
    private final Label todayValue = metricValue();
    private final Label todayUnit = metricUnit(i18n("community.calendar.unit.minutes"));
    private final Label todaySessions = BuiltinWidgets.caption("");
    private final Label weekValue = metricValue();
    private final Label weekUnit = metricUnit(i18n("community.calendar.unit.minutes"));
    private final GridPane grid = new GridPane();
    private HBox footer;
    private final PauseTransition refreshDelay = new PauseTransition(Duration.seconds(5));

    private Map<LocalDate, DayRecord> data = Map.of();
    private boolean enabled = true;

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
        disabledNote.getStyleClass().add("community-hint");
        disabledNote.setManaged(false);
        disabledNote.setVisible(false);

        footer = new HBox(8, disabledNote, BuiltinWidgets.spacer(),
                BuiltinWidgets.borderButton(i18n("community.calendar.settings"), this::openCalendarSettings));
        footer.setAlignment(Pos.CENTER_RIGHT);

        HBox todayLine = new HBox(3, todayValue, todayUnit);
        todayLine.setAlignment(Pos.BASELINE_LEFT);
        HBox weekLine = new HBox(3, weekValue, weekUnit);
        weekLine.setAlignment(Pos.BASELINE_LEFT);

        VBox todayStats = new VBox(2,
                BuiltinWidgets.caption(i18n("community.calendar.today_play")),
                todayLine,
                todaySessions,
                weekSpacer(),
                BuiltinWidgets.caption(i18n("community.calendar.week_total")),
                weekLine);
        todayStats.getStyleClass().add("community-calendar-stats");
        todayStats.setMinWidth(100);
        todayStats.setPrefWidth(112);
        todayStats.setMaxWidth(124);
        todayStats.setMaxHeight(Double.MAX_VALUE);
        todayStats.setFillWidth(true);
        HBox.setHgrow(todayStats, Priority.NEVER);

        HBox calendarRow = new HBox(8, grid, BuiltinWidgets.spacer(), todayStats);
        calendarRow.setAlignment(Pos.CENTER_LEFT);
        VBox body = new VBox(4, summary, calendarRow);
        body.setFillWidth(true);
        VBox.setVgrow(calendarRow, Priority.ALWAYS);

        refreshDelay.setOnFinished(event -> requestRefresh());
        onDispose(installLaunchListener());

        updateCalendarTone(Themes.darkModeProperty().get());
        ChangeListener<Boolean> toneListener = (observable, oldValue, dark) ->
                updateCalendarTone(Boolean.TRUE.equals(dark));
        Themes.darkModeProperty().addListener(toneListener);
        onDispose(() -> Themes.darkModeProperty().removeListener(toneListener));

        return BuiltinWidgets.cardContent(body, footer);
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
        rebuild();
    }

    private void rebuild() {
        summary.setVisible(true);
        summary.setManaged(true);
        grid.getChildren().clear();

        LocalDate today = LocalDate.now();
        CalendarWindow window = CalendarWindow.containing(today);

        for (int day = 0; day < CalendarWindow.DAYS; day++) {
            Label header = new Label(DayOfWeek.of(day + 1)
                    .getDisplayName(TextStyle.NARROW, Locale.getDefault()));
            header.getStyleClass().add("community-calendar-weekday");
            header.setMinSize(CELL_SIZE, 12);
            header.setPrefSize(CELL_SIZE, 12);
            header.setMaxSize(CELL_SIZE, 12);
            header.setAlignment(Pos.CENTER);
            grid.add(header, day, 0);
        }

        for (int week = 0; week < CalendarWindow.WEEKS; week++) {
            for (int day = 0; day < CalendarWindow.DAYS; day++) {
                LocalDate date = window.dateAt(week, day);
                Region cell = new Region();
                cell.setMinSize(CELL_SIZE, CELL_SIZE);
                cell.setPrefSize(CELL_SIZE, CELL_SIZE);
                cell.setMaxSize(CELL_SIZE, CELL_SIZE);
                cell.getStyleClass().add("community-calendar-cell");

                boolean future = date.isAfter(today);
                if (future) {
                    cell.getStyleClass().addAll("community-activity-level-0", "community-calendar-cell-future");
                } else {
                    DayRecord record = data.get(date);
                    int level = levelOf(record);
                    cell.getStyleClass().add("community-activity-level-" + level);
                    if (record != null && record.estimatedSeconds() > 0) {
                        cell.getStyleClass().add("community-activity-estimated");
                    }
                    Tooltip.install(cell, new Tooltip(tooltip(date, record)));
                }

                if (date.equals(today)) {
                    cell.getStyleClass().add("community-calendar-cell-today");
                    cell.setAccessibleText(i18n("community.calendar.today"));
                }

                grid.add(cell, day, week + 1);
            }
        }

        updateSummary();
        updateTodayStats();
        disabledNote.setText(enabled ? "" : i18n("community.calendar.disabled"));
        disabledNote.setVisible(!enabled);
        disabledNote.setManaged(!enabled);
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

    /// 更新“本日游玩时长”和“本周累计”两栏。
    private void updateTodayStats() {
        LocalDate today = LocalDate.now();
        DayRecord todayRecord = data.get(today);
        long todaySeconds = todayRecord == null ? 0 : todayRecord.playSeconds();
        int todaySessionsCount = todayRecord == null ? 0 : todayRecord.sessions();
        setMetric(todayValue, todayUnit, todaySeconds);
        todaySessions.setText(todaySeconds <= 0
                ? i18n("community.calendar.today.none")
                : i18n("community.calendar.today.sessions", Math.max(1, todaySessionsCount)));

        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        long weekSeconds = 0;
        for (Map.Entry<LocalDate, DayRecord> entry : data.entrySet()) {
            LocalDate date = entry.getKey();
            if (!date.isBefore(weekStart) && !date.isAfter(today)) {
                weekSeconds += entry.getValue().playSeconds();
            }
        }
        setMetric(weekValue, weekUnit, weekSeconds);
    }

    /// 根据当前主题表面明暗切换日历色阶：深色背景用浅色格，浅色背景用深色格。
    private void updateCalendarTone(boolean dark) {
        grid.getStyleClass().removeAll(CALENDAR_DARK_CLASS, CALENDAR_LIGHT_CLASS);
        grid.getStyleClass().add(dark ? CALENDAR_DARK_CLASS : CALENDAR_LIGHT_CLASS);
    }

    /// 打开设置 → 统计 → 日历设置。
    private void openCalendarSettings() {
        try {
            LauncherSettingsPage page = Controllers.getSettingsPage();
            page.showStatisticsCalendar();
            Controllers.navigate(page);
        } catch (Throwable e) {
            LOG.warning("Failed to open calendar settings", e);
        }
    }

    /// 把秒数写入“数值 + 单位”两个标签；不足一小时显示分钟。
    private static void setMetric(@NotNull Label value, @NotNull Label unit, long seconds) {
        if (seconds >= 3600) {
            value.setText(String.format(Locale.ROOT, "%.1f", seconds / 3600.0));
            unit.setText(i18n("community.calendar.unit.hours"));
        } else {
            value.setText(Long.toString(seconds / 60));
            unit.setText(i18n("community.calendar.unit.minutes"));
        }
    }

    /// 创建统计大数字标签。
    @NotNull
    private static Label metricValue() {
        Label label = new Label("0");
        label.getStyleClass().add("community-calendar-metric");
        return label;
    }

    /// 创建统计单位标签。
    @NotNull
    private static Label metricUnit(@NotNull String text) {
        Label label = new Label(text);
        label.getStyleClass().add("community-calendar-metric-unit");
        return label;
    }

    /// 创建统计栏内的可伸缩空白。
    @NotNull
    private static Region weekSpacer() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
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
