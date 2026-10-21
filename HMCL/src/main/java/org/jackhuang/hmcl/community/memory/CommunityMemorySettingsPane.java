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
package org.jackhuang.hmcl.community.memory;

import org.jackhuang.hmcl.community.ui.CommunityStyles;
import com.jfoenix.controls.JFXButton;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.util.DataSizeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 设置面板中的“内存”分组（步骤 12 §5）。
///
/// 只通过 [MemoryReclaimer] 暴露的 API 读取指标与触发回收，不直接读 settings、不调 `System.gc()`。
@NotNullByDefault
public final class CommunityMemorySettingsPane extends VBox {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private final MemoryReclaimer reclaimer = MemoryReclaimer.instance();

    private final Label usageLabel = new Label();
    private final Label reclaimableLabel = new Label();
    private final Label lastReclaimLabel = new Label();
    private final Label intervalLabel = new Label();
    private final Label statusLabel = new Label();
    private final Label messageLabel = new Label();

    private final JFXButton reclaimButton =
            FXUtils.newBorderButton(i18n("community.memory.action.reclaim_now"));
    private final JFXButton toggleButton = FXUtils.newBorderButton("");

    private final PauseTransition refreshDelay = new PauseTransition(Duration.seconds(2.5));

    /// 构造内存设置页。
    public CommunityMemorySettingsPane() {
        CommunityStyles.apply(this);
        setSpacing(10);
        setPadding(new Insets(10));
        setFillWidth(true);

        Label title = new Label(i18n("community.memory.title"));
        title.getStyleClass().add("title-label");

        usageLabel.setWrapText(true);
        reclaimableLabel.setWrapText(true);
        lastReclaimLabel.setWrapText(true);
        intervalLabel.setWrapText(true);
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("subtitle-label");

        Label note = new Label(i18n("community.memory.note"));
        note.setWrapText(true);
        note.getStyleClass().addAll("hint", "info");

        reclaimButton.setOnAction(e -> reclaimNow());
        toggleButton.setOnAction(e -> toggleEnabled());

        HBox actions = new HBox(8, reclaimButton, toggleButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        messageLabel.setWrapText(true);
        messageLabel.setManaged(false);
        messageLabel.setVisible(false);

        VBox body = new VBox(8, title, usageLabel, reclaimableLabel, lastReclaimLabel,
                intervalLabel, statusLabel, note, actions, messageLabel);

        ComponentList card = new ComponentList();
        card.getStyleClass().add("card-non-transparent");
        card.getContent().setAll(body);

        getChildren().setAll(card);

        refreshDelay.setOnFinished(e -> refresh());
        refresh();
    }

    /// 重新读取指标并刷新界面。
    private void refresh() {
        MemoryMetrics metrics = reclaimer.metrics();
        usageLabel.setText(i18n("community.memory.usage",
                formatSize(metrics.heapUsed()),
                formatSize(metrics.heapCommitted()),
                metrics.heapMax() < 0 ? i18n("community.memory.unlimited") : formatSize(metrics.heapMax())));
        reclaimableLabel.setText(i18n("community.memory.reclaimable",
                formatSize(metrics.reclaimableEstimate())));
        lastReclaimLabel.setText(metrics.lastReclaimAt() == null
                ? i18n("community.memory.never")
                : i18n("community.memory.last_reclaim",
                formatTime(metrics.lastReclaimAt()), formatSize(metrics.lastReclaimFreed())));
        intervalLabel.setText(i18n("community.memory.interval", metrics.intervalMinutes()));

        String status;
        if (metrics.disabledReason() != null) {
            status = i18n("community.memory.disabled." + metrics.disabledReason());
        } else if (!metrics.enabled()) {
            status = i18n("community.memory.disabled.by_user");
        } else if (metrics.gameRunning()) {
            status = i18n("community.memory.running");
        } else {
            status = i18n("community.memory.waiting_game");
        }
        statusLabel.setText(status);

        boolean explicitGcDisabled = "explicit_gc_disabled".equals(metrics.disabledReason());
        toggleButton.setText(i18n(metrics.enabled()
                ? "community.memory.action.disable" : "community.memory.action.enable"));
        toggleButton.setDisable(explicitGcDisabled);
        reclaimButton.setDisable(explicitGcDisabled);
    }

    private void reclaimNow() {
        if (reclaimer.reclaimNow()) {
            showMessage(i18n("community.memory.action.accepted"), false);
            refreshDelay.playFromStart();
        } else {
            showMessage(i18n("community.memory.action.cooldown"), true);
        }
    }

    private void toggleEnabled() {
        boolean enable = !reclaimer.isEnabled();
        reclaimer.setEnabled(enable);
        showMessage(i18n(enable ? "community.memory.action.enabled" : "community.memory.action.disabled"), false);
        refresh();
    }

    private void showMessage(String text, boolean error) {
        messageLabel.setText(text);
        messageLabel.setManaged(true);
        messageLabel.setVisible(true);
        messageLabel.getStyleClass().removeAll("error", "success");
        messageLabel.getStyleClass().add(error ? "error" : "success");
    }

    private static String formatSize(long bytes) {
        return DataSizeUnit.format(Math.max(0, bytes));
    }

    private static String formatTime(Instant instant) {
        return TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    }
}
