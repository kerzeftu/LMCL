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
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.community.dashboard.DashboardContext;
import org.jackhuang.hmcl.community.dashboard.DashboardWidget;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.memory.MemoryMetrics;
import org.jackhuang.hmcl.community.memory.MemoryReclaimer;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.DataSizeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 首页内存卡：展示可归还估算，并提供手动归还与自动归还开关。
///
/// 只调用 [MemoryReclaimer] 的公开 API；不直接读设置、不自行调用 `System.gc()`。
@NotNullByDefault
public final class MemoryWidget extends DashboardWidget {

    /// 指标刷新周期。
    private static final Duration REFRESH_INTERVAL = Duration.seconds(30);

    /// 指标行。
    private final Label usageLabel = BuiltinWidgets.caption("");

    /// 状态行。
    private final Label statusLabel = BuiltinWidgets.caption("");

    /// 立即归还按钮。
    private final JFXButton reclaimButton = BuiltinWidgets.borderButton(
            i18n("community.memory.action.reclaim_now"), this::reclaimNow);

    /// 自动归还开关按钮。
    private final JFXButton toggleButton = BuiltinWidgets.borderButton("", this::toggleEnabled);

    /// 最近一次指标。
    private MemoryMetrics metrics;

    /// 周期刷新。
    private Timeline timeline;

    /// 归还结果提示的延迟刷新。
    private final PauseTransition refreshDelay = new PauseTransition(Duration.seconds(2.5));

    /// 构造内存卡。
    ///
    /// @param descriptor 组件描述
    /// @param context 组件上下文
    public MemoryWidget(@NotNull DashboardWidgetDescriptor descriptor,
                        @NotNull DashboardContext context) {
        super(descriptor, context);
    }

    /// 创建卡片骨架。
    ///
    /// @return 内容节点
    @Override
    @NotNull
    protected Node createContent() {
        HBox actions = new HBox(8, reclaimButton, toggleButton);

        VBox box = new VBox(3, usageLabel, statusLabel, actions);
        box.setFillWidth(true);

        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(usageLabel.textProperty());
        Tooltip.install(box, tooltip);

        timeline = new Timeline(new KeyFrame(REFRESH_INTERVAL, event -> {
            if (shouldPoll()) {
                requestRefresh();
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();

        refreshDelay.setOnFinished(event -> requestRefresh());
        onDispose(() -> {
            timeline.stop();
            refreshDelay.stop();
        });
        return box;
    }

    /// 场景可见且窗口聚焦时才轮询指标。
    ///
    /// @return 是否应刷新
    private boolean shouldPoll() {
        if (!isVisible() || getScene() == null || getScene().getWindow() == null) {
            return false;
        }
        return getScene().getWindow().isFocused();
    }

    /// 读取内存指标。
    @Override
    protected void refreshAsync() {
        loadAsync(() -> MemoryReclaimer.instance().metrics(), this::applyMetrics);
    }

    /// 应用指标到界面。
    ///
    /// @param metrics 指标
    private void applyMetrics(@NotNull MemoryMetrics metrics) {
        this.metrics = metrics;
        usageLabel.setText(i18n("community.memory.card.summary",
                formatSize(metrics.heapUsed()),
                formatSize(metrics.reclaimableEstimate())));
        statusLabel.setText(statusText(metrics));

        boolean explicitGcDisabled = "explicit_gc_disabled".equals(metrics.disabledReason());
        reclaimButton.setDisable(explicitGcDisabled);
        toggleButton.setDisable(explicitGcDisabled);
        toggleButton.setText(i18n(metrics.enabled()
                ? "community.memory.action.disable" : "community.memory.action.enable"));
    }

    /// 计算状态文案。
    ///
    /// @param metrics 指标
    /// @return 状态文案
    @NotNull
    private static String statusText(@NotNull MemoryMetrics metrics) {
        if (metrics.disabledReason() != null) {
            return i18n("community.memory.disabled." + metrics.disabledReason());
        }
        if (!metrics.enabled()) {
            return i18n("community.memory.disabled.by_user");
        }
        if (metrics.gameRunning()) {
            return i18n("community.memory.running");
        }
        return i18n("community.memory.waiting_game");
    }

    /// 手动触发一次归还。
    private void reclaimNow() {
        if (MemoryReclaimer.instance().reclaimNow()) {
            statusLabel.setText(i18n("community.memory.action.accepted"));
            refreshDelay.playFromStart();
        } else {
            statusLabel.setText(i18n("community.memory.action.cooldown"));
        }
    }

    /// 切换自动归还。
    private void toggleEnabled() {
        if (metrics == null) {
            return;
        }
        boolean enable = !metrics.enabled();
        reclaimButton.setDisable(true);
        toggleButton.setDisable(true);
        CompletableFuture.runAsync(() -> MemoryReclaimer.instance().setEnabled(enable), Schedulers.io())
                .whenCompleteAsync((ignored, error) -> {
                    if (error != null) {
                        LOG.warning("Failed to toggle memory reclaimer", error);
                    }
                    requestRefresh();
                }, Schedulers.javafx());
    }

    /// 格式化字节数。
    ///
    /// @param bytes 字节数
    /// @return 可读文本
    @NotNull
    private static String formatSize(long bytes) {
        return DataSizeUnit.format(Math.max(0, bytes));
    }
}
