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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.crash.CrashEntry;
import org.jackhuang.hmcl.community.crash.CrashHistory;
import org.jackhuang.hmcl.community.dashboard.DashboardContext;
import org.jackhuang.hmcl.community.dashboard.DashboardWidget;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.ui.crash.CrashHistoryPane;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static org.jackhuang.hmcl.ui.Controllers.dialog;
import static org.jackhuang.hmcl.ui.FXUtils.openFolder;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 首页崩溃历史卡：展示最近两条崩溃，并提供全部记录入口。
///
/// 不复制 09 的详情/导出/回滚实现：完整视图直接内嵌 [CrashHistoryPane]。
@NotNullByDefault
public final class CrashHistoryWidget extends DashboardWidget {

    /// 1×1 卡片最多显示两条。
    private static final int COMPACT_ENTRIES = 2;

    /// 2×1 卡片最多显示四条。
    private static final int LARGE_ENTRIES = 4;

    /// 时间格式。
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /// 列表容器。
    private final VBox list = new VBox(2);

    /// 空态。
    private final Label emptyLabel = BuiltinWidgets.caption(i18n("community.crash.empty"));

    /// 错误态。
    private final Label errorLabel = BuiltinWidgets.caption(i18n("community.crash.card.unavailable"));

    /// 未读红点。
    private final Label unseenLabel = new Label();

    /// 最近一次加载的条目。
    private List<CrashEntry> entries = List.of();

    /// 当前高度下最多显示几条。
    private int maxEntries = COMPACT_ENTRIES;

    /// 查看全部。
    private final JFXButton moreButton = BuiltinWidgets.borderButton(
            i18n("community.crash.card.more"), this::openFullHistory);

    /// 打开归档目录（错误态）。
    private final JFXButton openFolderButton = BuiltinWidgets.borderButton(
            i18n("community.crash.action.open_folder"), this::openCrashFolder);

    /// 构造崩溃历史卡。
    ///
    /// @param descriptor 组件描述
    /// @param context 组件上下文
    public CrashHistoryWidget(@NotNull DashboardWidgetDescriptor descriptor,
                              @NotNull DashboardContext context) {
        super(descriptor, context);
    }

    /// 创建卡片骨架。
    ///
    /// @return 内容节点
    @Override
    @NotNull
    protected Node createContent() {
        unseenLabel.getStyleClass().add("error");
        unseenLabel.setVisible(false);
        unseenLabel.setManaged(false);

        HBox footer = new HBox(8, emptyLabel, errorLabel, openFolderButton,
                BuiltinWidgets.spacer(), unseenLabel, moreButton);
        footer.setAlignment(Pos.CENTER_LEFT);
        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        openFolderButton.setVisible(false);
        openFolderButton.setManaged(false);

        VBox box = new VBox(4, list, footer);
        box.setFillWidth(true);
        VBox.setVgrow(list, Priority.ALWAYS);

        heightProperty().addListener((observable, oldValue, newValue) -> {
            int limit = newValue.doubleValue() >= 220 ? LARGE_ENTRIES : COMPACT_ENTRIES;
            if (limit != maxEntries) {
                maxEntries = limit;
                renderRows();
            }
        });
        return box;
    }

    /// 读取崩溃历史。
    @Override
    protected void refreshAsync() {
        loadAsync(() -> {
            try {
                return new LoadResult(CrashHistory.load(), null);
            } catch (Throwable e) {
                return new LoadResult(List.of(), e);
            }
        }, this::apply);
    }

    /// 应用加载结果。
    ///
    /// @param result 加载结果
    private void apply(@NotNull LoadResult result) {
        entries = result.entries();
        list.getChildren().clear();

        boolean failed = result.error() != null;
        errorLabel.setVisible(failed);
        errorLabel.setManaged(failed);
        openFolderButton.setVisible(failed);
        openFolderButton.setManaged(failed);
        moreButton.setDisable(failed);

        int unseen = CrashHistory.unseenCount();
        unseenLabel.setText(unseen <= 0 ? "" : "● " + unseen);
        unseenLabel.setVisible(unseen > 0);
        unseenLabel.setManaged(unseen > 0);

        if (failed) {
            return;
        }

        boolean empty = entries.isEmpty();
        emptyLabel.setVisible(empty);
        emptyLabel.setManaged(empty);
        renderRows();
    }

    /// 按当前卡片高度渲染摘要行。
    private void renderRows() {
        list.getChildren().clear();
        int limit = Math.min(maxEntries, entries.size());
        for (int i = 0; i < limit; i++) {
            list.getChildren().add(createRow(entries.get(i)));
        }
    }

    /// 创建一条摘要行。
    ///
    /// @param entry 崩溃条目
    /// @return 行节点
    @NotNull
    private Node createRow(@NotNull CrashEntry entry) {
        Label time = BuiltinWidgets.caption(DISPLAY_TIME.format(
                entry.at().atZone(ZoneId.systemDefault())));
        time.setMinWidth(72);

        Label version = BuiltinWidgets.caption(BuiltinWidgets.shorten(entry.versionId(), 12));
        version.setMinWidth(60);

        Label reason = BuiltinWidgets.caption(BuiltinWidgets.shorten(reasonText(entry), 18));
        HBox.setHgrow(reason, Priority.ALWAYS);

        HBox row = new HBox(6, time, version, reason);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 6, 3, 6));
        row.getStyleClass().add("md-list-cell");
        row.setOnMouseClicked(event -> openFullHistory());
        return row;
    }

    /// 计算原因文案。
    ///
    /// @param entry 崩溃条目
    /// @return 原因文案
    @NotNull
    private static String reasonText(@NotNull CrashEntry entry) {
        if (entry.reasonRule() != null) {
            String key = "game.crash.reason." + entry.reasonRule().toLowerCase(Locale.ROOT);
            if (I18n.hasKey(key)) {
                return i18n(key);
            }
        }
        if (!entry.keywords().isEmpty()) {
            return i18n("game.crash.reason.stacktrace");
        }
        return i18n("community.crash.card.unknown_reason");
    }

    /// 打开完整崩溃历史。
    private void openFullHistory() {
        CrashHistory.markAllSeen();
        int unseen = CrashHistory.unseenCount();
        unseenLabel.setVisible(unseen > 0);
        unseenLabel.setManaged(unseen > 0);

        CrashHistoryPane pane = new CrashHistoryPane();
        pane.setPrefHeight(380);
        ScrollPane scroll = new ScrollPane(pane);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(380);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox body = new VBox(8, scroll);
        VBox root = BuiltinWidgets.dialogRoot(i18n("community.crash.title"), body, 680);
        JFXButton close = BuiltinWidgets.borderButton(i18n("community.crash.action.close"),
                () -> root.fireEvent(new org.jackhuang.hmcl.ui.construct.DialogCloseEvent()));
        body.getChildren().add(BuiltinWidgets.dialogActions(close));
        dialog(root);
    }

    /// 打开崩溃归档目录。
    private void openCrashFolder() {
        try {
            openFolder(CrashHistory.crashRoot());
        } catch (Throwable e) {
            LOG.warning("Failed to open crash archive folder", e);
        }
    }

    /// 崩溃历史加载结果。
    ///
    /// @param entries 条目
    /// @param error 失败原因
    private record LoadResult(@NotNull List<CrashEntry> entries, @org.jetbrains.annotations.Nullable Throwable error) {
    }
}
