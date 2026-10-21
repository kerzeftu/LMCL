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

import org.jackhuang.hmcl.community.ui.CommunityStyles;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.preflight.CheckResult;
import org.jackhuang.hmcl.community.preflight.PreflightChecks;
import org.jackhuang.hmcl.community.preflight.PreflightConfig;
import org.jackhuang.hmcl.community.preflight.PreflightReport;
import org.jackhuang.hmcl.community.preflight.Severity;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 启动前体检结果对话框。
///
/// - 启动流程：`new PreflightDialog(report).show(decision -> ...)`，`true` 表示继续启动；
/// - 步骤 15 的“查看报告”：`PreflightDialog.showReportOnly(report)`，只读展示。
///
/// 关闭窗口视为“返回修复”（`false`）。
@NotNullByDefault
public final class PreflightDialog {

    /// 一份报告。
    private final PreflightReport report;

    /// 配置（用于过滤静音项）。
    private final PreflightConfig config;

    /// 构造对话框。
    ///
    /// @param report 报告
    public PreflightDialog(@NotNull PreflightReport report) {
        this.report = report;
        this.config = PreflightConfig.load();
    }

    /// 在 JavaFX 线程展示对话框并回调用户决定。
    ///
    /// @param onDecision 决定回调：`true` = 仍要启动，`false` = 返回修复
    public void show(@NotNull Consumer<Boolean> onDecision) {
        if (Platform.isFxApplicationThread()) {
            showNow(onDecision);
        } else {
            Platform.runLater(() -> showNow(onDecision));
        }
    }

    /// 只读展示报告（不做启动决定）。
    ///
    /// @param report 报告
    public static void showReportOnly(@NotNull PreflightReport report) {
        Runnable action = () -> {
            PreflightConfig config = PreflightConfig.load();
            VBox root = buildRoot(report, config, null);
            Controllers.dialog(root);
        };
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    /// 在 FX 线程构造并展示。
    private void showNow(@NotNull Consumer<Boolean> onDecision) {
        AtomicBoolean finished = new AtomicBoolean();
        Consumer<Boolean> finish = proceed -> {
            if (finished.compareAndSet(false, true)) {
                onDecision.accept(proceed);
            }
        };

        VBox root = buildRoot(report, config, finish);
        root.addEventHandler(DialogCloseEvent.CLOSE, event -> finish.accept(false));
        Controllers.dialog(root);
    }

    /// 构造对话框根节点。
    ///
    /// @param report 报告
    /// @param config 配置
    /// @param finish 决定回调（`null` 表示只读模式）
    /// @return 根节点
    private static @NotNull VBox buildRoot(
            @NotNull PreflightReport report,
            @NotNull PreflightConfig config,
            @Nullable Consumer<Boolean> finish) {
        VBox root = new VBox(12);
        CommunityStyles.apply(root);
        root.setPadding(new Insets(16));
        root.setPrefWidth(640);
        root.getStyleClass().add("jfx-dialog-layout");

        Label title = new Label(i18n("community.preflight.title") + " · " + report.versionId());
        title.getStyleClass().add("title-label");
        HBox header = new HBox(8, title, spacer());

        JFXButton copy = new JFXButton(i18n("community.preflight.action.copy"));
        copy.setOnAction(event -> FXUtils.copyText(renderPlainText(report, config)));
        header.getChildren().add(copy);

        JFXButton close = new JFXButton("✕");
        close.setOnAction(event -> {
            if (finish == null) {
                root.fireEvent(new DialogCloseEvent());
            } else {
                finish.accept(false);
                root.fireEvent(new DialogCloseEvent());
            }
        });
        header.getChildren().add(close);
        root.getChildren().add(header);

        List<CheckResult> visible = config.visibleChecks(report);
        VBox rows = new VBox(8);
        if (visible.isEmpty()) {
            rows.getChildren().add(new Label(i18n("community.preflight.advice.none")));
        } else {
            for (CheckResult check : visible) {
                rows.getChildren().add(createRow(check));
            }
        }
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(380, Math.max(120, visible.size() * 64)));
        root.getChildren().add(scroll);

        HBox actions = new HBox(8);
        actions.setPadding(new Insets(8, 0, 0, 0));
        if (finish == null) {
            JFXButton ok = new JFXButton(i18n("community.preflight.action.close"));
            ok.setOnAction(event -> root.fireEvent(new DialogCloseEvent()));
            actions.getChildren().add(ok);
        } else {
            JFXButton launchAnyway = new JFXButton(i18n("community.preflight.action.launch_anyway"));
            launchAnyway.setOnAction(event -> {
                finish.accept(true);
                root.fireEvent(new DialogCloseEvent());
            });

            JFXButton fixLater = new JFXButton(i18n("community.preflight.action.fix_later"));
            fixLater.setOnAction(event -> {
                finish.accept(false);
                root.fireEvent(new DialogCloseEvent());
            });

            JFXButton mute = new JFXButton(i18n("community.preflight.action.mute_instance"));
            mute.setOnAction(event -> Controllers.confirm(
                    i18n("community.preflight.action.mute_instance.confirm"),
                    i18n("community.preflight.title"),
                    () -> {
                        List<String> ids = new ArrayList<>();
                        for (CheckResult check : visible) {
                            if (check.severity() == Severity.WARN || check.severity() == Severity.ERROR) {
                                ids.add(check.id());
                            }
                        }
                        if (!ids.isEmpty()) {
                            PreflightConfig.mute(report.versionId(), ids);
                        }
                        finish.accept(true);
                        root.fireEvent(new DialogCloseEvent());
                    },
                    null));

            actions.getChildren().addAll(launchAnyway, fixLater, mute);
        }
        root.getChildren().add(actions);
        return root;
    }

    /// 构造一条检查结果。
    private static @NotNull HBox createRow(@NotNull CheckResult check) {
        Label icon = new Label(severityIcon(check.severity()));
        icon.setMinWidth(24);
        Label text = new Label(describe(check));
        text.setWrapText(true);
        HBox row = new HBox(8, icon, text);
        row.setPadding(new Insets(4, 0, 4, 0));
        return row;
    }

    /// 生成结果描述。
    private static @NotNull String describe(@NotNull CheckResult check) {
        StringBuilder builder = new StringBuilder();
        builder.append(checkTitle(check.id())).append("：").append(severityText(check.severity()));
        if (!check.details().isEmpty()) {
            builder.append(" (").append(String.join(", ", check.details().values())).append(')');
        }
        String adviceKey = adviceKey(check.code());
        if (adviceKey != null) {
            builder.append(" — ").append(i18n(adviceKey));
        }
        return builder.toString();
    }

    /// 检查项标题。
    private static @NotNull String checkTitle(@NotNull String id) {
        return switch (id) {
            case PreflightChecks.ID_JAVA -> i18n("community.preflight.check.java");
            case PreflightChecks.ID_MEMORY -> i18n("community.preflight.check.memory");
            case PreflightChecks.ID_DISK -> i18n("community.preflight.check.disk");
            case PreflightChecks.ID_MODS -> i18n("community.preflight.check.mods");
            case PreflightChecks.ID_DEPS -> i18n("community.preflight.check.deps");
            default -> id;
        };
    }

    /// 根据 code 返回建议文案 key；无建议时返回 `null`。
    private static @Nullable String adviceKey(@NotNull String code) {
        return switch (code) {
            case "java_low", "java_no_java" -> "community.preflight.advice.java_low";
            case "memory_error", "memory_warn" -> "community.preflight.advice.memory_high";
            case "disk_error", "disk_warn" -> "community.preflight.advice.disk_low";
            case "duplicate_mods" -> "community.preflight.advice.duplicate_mods";
            case "missing_deps" -> "community.preflight.advice.missing_deps";
            default -> null;
        };
    }

    /// 严重程度图标。
    private static @NotNull String severityIcon(@NotNull Severity severity) {
        return switch (severity) {
            case OK -> "✅";
            case WARN -> "⚠️";
            case ERROR -> "❌";
            case SKIPPED -> "➖";
        };
    }

    /// 严重程度文案。
    private static @NotNull String severityText(@NotNull Severity severity) {
        return i18n("community.preflight.severity." + severity.name().toLowerCase(Locale.ROOT));
    }

    /// 渲染可复制的纯文本报告。
    private static @NotNull String renderPlainText(@NotNull PreflightReport report, @NotNull PreflightConfig config) {
        StringBuilder builder = new StringBuilder();
        builder.append(i18n("community.preflight.title")).append(" · ").append(report.versionId()).append('\n');
        for (CheckResult check : config.visibleChecks(report)) {
            builder.append(severityIcon(check.severity())).append(' ').append(describe(check)).append('\n');
        }
        return builder.toString();
    }

    /// 生成水平占位。
    private static @NotNull Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}
