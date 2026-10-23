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

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.play.PlayRecordStore;
import org.jackhuang.hmcl.community.play.PlayStats;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.LinePane;
import org.jackhuang.hmcl.ui.construct.LineToggleButton;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 设置 → 统计：游玩记录概览与日历设置。
///
/// 页面只读 [PlayRecordStore] 的聚合结果；记录开关、打开数据目录与清空
/// 操作都在 io 线程执行，完成后回到 JavaFX 线程更新界面。
@NotNullByDefault
public final class StatisticsPage extends StackPane implements PageAware {

    /// 页面滚动容器。
    private final ScrollPane scrollPane;

    /// 页面内容容器。
    private final VBox content;

    /// 日历设置区，供设置页选中“日历设置”后滚动定位。
    private final VBox calendarSection;

    /// 游玩记录存储。
    private final PlayRecordStore store = PlayRecordStore.get();

    /// 累计游玩时长。
    private final Label totalValue = valueLabel();

    /// 最近七天游玩时长。
    private final Label last7Value = valueLabel();

    /// 有记录天数。
    private final Label activeDaysValue = valueLabel();

    /// 统计读取失败提示。
    private final Label statsError = new Label(i18n("settings.statistics.unavailable"));

    /// 防止读取失败恢复开关时再次触发写入。
    private boolean updatingToggle;

    /// 构建设置页“统计”内容。
    public StatisticsPage() {
        FXUtils.checkFxUserThread();

        content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setFillWidth(true);

        scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        getChildren().setAll(scrollPane);

        statsError.getStyleClass().addAll("subtitle-label", "community-label");
        statsError.setVisible(false);
        statsError.setManaged(false);

        content.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("settings.statistics.overview")),
                createOverviewList(),
                statsError);

        calendarSection = new VBox(10);
        calendarSection.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("settings.statistics.calendar")),
                createCalendarList());
        content.getChildren().add(calendarSection);

        CommunityStyles.apply(this);
    }

    @Override
    public void onPageShown() {
        reloadStats();
    }

    /// 滚动到“日历设置”区；由主页日历卡的“日历设置”按钮调用。
    public void scrollToCalendarSettings() {
        calendarSection.requestLayout();
        Platform.runLater(() -> {
            double contentHeight = content.getHeight();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            if (contentHeight <= viewportHeight) {
                scrollPane.setVvalue(1);
                return;
            }
            double offset = calendarSection.getBoundsInParent().getMinY();
            scrollPane.setVvalue(Math.min(1, Math.max(0, offset / (contentHeight - viewportHeight))));
        });
    }

    /// 创建概览列表。
    @NotNull
    private ComponentList createOverviewList() {
        ComponentList list = new ComponentList();
        list.getContent().addAll(
                statRow("settings.statistics.total", totalValue),
                statRow("settings.statistics.last7", last7Value),
                statRow("settings.statistics.active_days", activeDaysValue));
        return list;
    }

    /// 创建日历设置列表。
    @NotNull
    private ComponentList createCalendarList() {
        LineToggleButton recordToggle = new LineToggleButton();
        recordToggle.setTitle(i18n("community.calendar.enabled"));
        recordToggle.setSubtitle(i18n("community.calendar.enabled.note"));
        recordToggle.setSelected(store.enabled());
        recordToggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingToggle) {
                return;
            }
            saveRecordEnabled(recordToggle, Boolean.TRUE.equals(newValue));
        });

        LineButton openFolder = LineButton.createNavigationButton();
        openFolder.setTitle(i18n("community.calendar.open_folder"));
        openFolder.setSubtitle(store.dataFile().getParent().toString());
        openFolder.setTrailingIcon(SVG.FOLDER_OPEN);
        openFolder.setOnAction(event -> {
            try {
                FXUtils.openFolder(store.dataFile().getParent());
            } catch (Throwable e) {
                LOG.warning("Failed to open play record folder", e);
                Controllers.dialog(i18n("settings.statistics.open_failed"), null, MessageType.ERROR);
            }
        });

        LineButton clear = new LineButton();
        clear.setTitle(i18n("community.calendar.clear"));
        clear.setSubtitle(i18n("settings.statistics.clear.subtitle"));
        clear.setTrailingIcon(SVG.DELETE);
        clear.setOnAction(event -> Controllers.confirm(
                i18n("community.calendar.clear.confirm"),
                i18n("community.calendar.clear"),
                () -> clearRecords(clear),
                null));

        ComponentList list = new ComponentList();
        list.getContent().addAll(recordToggle, openFolder, clear);
        return list;
    }

    /// 创建一行“标题 + 右侧值”。
    @NotNull
    private static LinePane statRow(@NotNull String titleKey, @NotNull Label value) {
        LinePane row = new LinePane();
        row.setTitle(i18n(titleKey));
        row.setRight(value);
        return row;
    }

    /// 创建统计数值标签。
    @NotNull
    private static Label valueLabel() {
        Label label = new Label("—");
        label.getStyleClass().addAll("community-stat-value");
        return label;
    }

    /// 异步读取并更新统计概览。
    private void reloadStats() {
        CompletableFuture.supplyAsync(store::load, Schedulers.io())
                .whenCompleteAsync((data, error) -> {
                    if (error != null) {
                        LOG.warning("Failed to read play statistics", error);
                        totalValue.setText("—");
                        last7Value.setText("—");
                        activeDaysValue.setText("—");
                        statsError.setVisible(true);
                        statsError.setManaged(true);
                        return;
                    }

                    PlayStats stats = PlayStats.of(data, LocalDate.now());
                    totalValue.setText(formatDuration(stats.totalSeconds()));
                    last7Value.setText(formatDuration(stats.last7Seconds()));
                    activeDaysValue.setText(i18n("settings.statistics.days.value", stats.activeDays()));
                    statsError.setVisible(false);
                    statsError.setManaged(false);
                }, Schedulers.javafx());
    }

    /// 保存游玩记录开关；失败时恢复原状态并提示。
    private void saveRecordEnabled(@NotNull LineToggleButton toggle, boolean enabled) {
        toggle.setDisable(true);
        CompletableFuture.runAsync(() -> store.setEnabled(enabled), Schedulers.io())
                .whenCompleteAsync((unused, error) -> {
                    toggle.setDisable(false);
                    if (error != null) {
                        LOG.warning("Failed to save play record switch", error);
                        updatingToggle = true;
                        try {
                            toggle.setSelected(!enabled);
                        } finally {
                            updatingToggle = false;
                        }
                        Controllers.dialog(i18n("settings.statistics.save_failed"), null, MessageType.ERROR);
                    } else {
                        reloadStats();
                    }
                }, Schedulers.javafx());
    }

    /// 清空游玩记录；完成后刷新概览。
    private void clearRecords(@NotNull LineButton clearButton) {
        clearButton.setDisable(true);
        CompletableFuture.runAsync(store::clear, Schedulers.io())
                .whenCompleteAsync((unused, error) -> {
                    clearButton.setDisable(false);
                    if (error != null) {
                        LOG.warning("Failed to clear play records", error);
                        Controllers.dialog(i18n("settings.statistics.clear_failed"), null, MessageType.ERROR);
                        return;
                    }
                    reloadStats();
                }, Schedulers.javafx());
    }

    /// 把秒数格式化为本地化的时长文本。
    @NotNull
    private static String formatDuration(long seconds) {
        if (seconds < 60) {
            return i18n("community.calendar.duration.seconds", seconds);
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return i18n("community.calendar.duration.minutes", minutes);
        }

        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return remainingMinutes == 0
                ? i18n("community.calendar.duration.hours", hours)
                : i18n("community.calendar.duration.hours_minutes", hours, remainingMinutes);
    }
}
