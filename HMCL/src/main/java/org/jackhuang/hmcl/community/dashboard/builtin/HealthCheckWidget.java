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
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.community.dashboard.DashboardContext;
import org.jackhuang.hmcl.community.dashboard.DashboardWidget;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.launch.CommunityLaunchHooks;
import org.jackhuang.hmcl.community.launch.LaunchEvent;
import org.jackhuang.hmcl.community.launch.LaunchEventKind;
import org.jackhuang.hmcl.community.launch.LaunchEventListener;
import org.jackhuang.hmcl.community.preflight.CheckResult;
import org.jackhuang.hmcl.community.preflight.PreflightReport;
import org.jackhuang.hmcl.community.preflight.PreflightService;
import org.jackhuang.hmcl.community.preflight.Severity;
import org.jackhuang.hmcl.community.ui.PreflightDialog;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.task.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 健康检查卡：展示最近一次启动前体检的汇总，并允许从卡片重新体检。
///
/// 卡片只读 08 落盘的 `last.json`，不自己执行检查；[复查] 调用 08 的
/// [PreflightService#collect(HMCLGameRepository, String)]，结果用 08 的只读弹窗展示。
@NotNullByDefault
public final class HealthCheckWidget extends DashboardWidget {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final long STALE_DAYS = 14;

    private final Label summary = BuiltinWidgets.body("");
    private final Label time = BuiltinWidgets.caption("");
    private final Label stale = BuiltinWidgets.caption(i18n("community.health.stale"));
    private final Label error = BuiltinWidgets.caption(i18n("community.health.unavailable"));
    private final VBox details = new VBox(3);
    private final javafx.scene.control.Button recheck =
            BuiltinWidgets.borderButton(i18n("community.health.recheck"), this::recheck);
    private HBox footer;

    private @Nullable PreflightReport report;
    private boolean busy;

    /// 构造健康检查卡。
    ///
    /// @param descriptor 组件描述
    /// @param context 组件上下文
    public HealthCheckWidget(@NotNull DashboardWidgetDescriptor descriptor,
                             @NotNull DashboardContext context) {
        super(descriptor, context);
    }

    @Override
    @NotNull
    protected Node createContent() {
        stale.setVisible(false);
        stale.setManaged(false);
        error.setVisible(false);
        error.setManaged(false);

        footer = new HBox(8, recheck, BuiltinWidgets.spacer());
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(3, summary, time, stale, details, error, footer);
        box.setFillWidth(true);
        VBox.setVgrow(details, Priority.ALWAYS);
        heightProperty().addListener((observable, oldValue, newValue) -> render());
        onDispose(installLaunchListener());
        return box;
    }

    @Override
    protected void refreshAsync() {
        loadAsync(() -> {
            try {
                return PreflightService.lastReport();
            } catch (Throwable e) {
                LOG.warning("Failed to read last preflight report", e);
                return null;
            }
        }, this::apply);
    }

    private void apply(@Nullable PreflightReport report) {
        this.report = report;
        render();
    }

    private void render() {
        details.getChildren().clear();
        boolean compact = getHeight() < 170;
        time.setVisible(!compact);
        time.setManaged(!compact);
        if (report == null) {
            summary.setText(i18n("community.health.empty"));
            time.setText("");
            stale.setVisible(false);
            stale.setManaged(false);
            error.setVisible(false);
            error.setManaged(false);
            return;
        }

        Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            counts.put(severity, 0);
        }
        for (CheckResult check : report.checks()) {
            counts.merge(check.severity(), 1, Integer::sum);
        }
        summary.setText(i18n("community.health.summary",
                counts.get(Severity.OK), counts.get(Severity.WARN),
                counts.get(Severity.ERROR), counts.get(Severity.SKIPPED)));
        time.setText(i18n("community.health.time", formatTime(report.at())));

        boolean isStale = report.at().isBefore(Instant.now().minus(java.time.Duration.ofDays(STALE_DAYS)));
        stale.setVisible(isStale && !compact);
        stale.setManaged(isStale && !compact);
        error.setVisible(false);
        error.setManaged(false);

        if (compact) {
            return;
        }
        int shown = 0;
        for (CheckResult check : report.checks()) {
            if (check.severity() == Severity.OK || check.severity() == Severity.SKIPPED) {
                continue;
            }
            if (shown++ >= 2) {
                break;
            }
            Label line = BuiltinWidgets.caption(i18n("community.preflight.check." + check.id())
                    + " · " + i18n("community.preflight.severity." + check.severity().name().toLowerCase()));
            line.setStyle(check.severity() == Severity.ERROR
                    ? "-fx-text-fill: -monet-error;"
                    : "-fx-text-fill: -monet-tertiary;");
            details.getChildren().add(line);
        }
    }

    private void recheck() {
        if (busy) {
            return;
        }
        HMCLGameRepository repository = context.repository().get();
        String selected = context.selectedInstanceId().getValue();
        if (repository == null || selected == null) {
            showRecheckHint(i18n("community.health.no_instance"));
            return;
        }
        busy = true;
        recheck.setDisable(true);
        recheck.setText(i18n("community.health.rechecking"));
        java.util.concurrent.CompletableFuture<PreflightReport> future;
        try {
            future = PreflightService.collect(repository, selected);
        } catch (Throwable e) {
            busy = false;
            recheck.setDisable(false);
            recheck.setText(i18n("community.health.recheck"));
            showRecheckHint(i18n("community.health.failed", String.valueOf(e.getMessage())));
            return;
        }
        future.whenCompleteAsync((result, throwable) -> {
                    busy = false;
                    recheck.setDisable(false);
                    recheck.setText(i18n("community.health.recheck"));
                    if (throwable != null) {
                        showRecheckHint(i18n("community.health.failed",
                                throwable.getCause() == null ? throwable.getMessage() : throwable.getCause().getMessage()));
                        return;
                    }
                    if (result == null) {
                        showRecheckHint(i18n("community.health.unavailable"));
                        return;
                    }
                    this.report = result;
                    render();
                    PreflightDialog.showReportOnly(result);
                }, Schedulers.javafx());
    }

    private void showRecheckHint(@NotNull String text) {
        error.setText(text);
        error.setVisible(true);
        error.setManaged(true);
    }

    @NotNull
    private static String formatTime(@NotNull Instant instant) {
        return TIME.format(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
    }


    @NotNull
    private Runnable installLaunchListener() {
        LaunchEventListener listener = new LaunchEventListener() {
            private final PauseTransition delay = new PauseTransition(Duration.seconds(2));

            {
                delay.setOnFinished(event -> refreshAsync());
            }

            @Override
            public void onLaunchEvent(@NotNull LaunchEvent event) {
                if (event.kind() == LaunchEventKind.TASKS_COMPLETED) {
                    Schedulers.javafx().execute(delay::playFromStart);
                }
            }
        };
        try {
            CommunityLaunchHooks.addListener(listener);
        } catch (Throwable e) {
            LOG.warning("Failed to listen launch events for health check", e);
        }
        return () -> {
            try {
                CommunityLaunchHooks.removeListener(listener);
            } catch (Throwable e) {
                LOG.warning("Failed to remove launch listener for health check", e);
            }
        };
    }
}
