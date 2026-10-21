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
package org.jackhuang.hmcl.community.dashboard;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 自绘网格：按 [DashboardLayout] 的结果摆放卡片，并在编辑模式下提供拖拽排序。
///
/// 非编辑模式下**不注册任何拖拽处理器**，文件拖入仍由上游 `RootPage` 处理；
/// 编辑模式下也只接受本网格自己放上 `Dragboard` 的 id，避免与外部的文件/实例拖拽混淆。
@NotNullByDefault
public final class DashboardGrid extends Region {

    /// 拖拽载荷前缀，用来区分本网格的拖拽与外部拖拽。
    private static final String DRAG_PREFIX = "community-dashboard:";

    /// 组件上下文。
    private final DashboardContext context;

    /// 组件注册表。
    private final WidgetRegistry registry;

    /// 编辑操作回调。
    private final Listener listener;

    /// 已创建组件，按 id 索引；`dispose` 时移除。
    private final Map<String, DashboardWidget> widgets = new LinkedHashMap<>();

    /// 当前布局项（只读副本，由 [DashboardPane] 传入）。
    private List<DashboardItem> items = List.of();

    /// 是否处于编辑模式。
    private boolean editMode;

    /// 最近一次布局结果，用于定位与调试。
    private DashboardLayout.LayoutResult layout =
            new DashboardLayout.LayoutResult(List.of(), List.of(), 0);

    /// 正在拖拽的组件 id。
    private @Nullable String draggingId;

    /// 编辑操作回调。
    public interface Listener {

        /// 把组件按位移移动（`-1` 上移/前移，`1` 下移/后移）。
        ///
        /// @param id 组件 id
        /// @param delta 位移
        void onMove(@NotNull String id, int delta);

        /// 修改组件跨度。
        ///
        /// @param id 组件 id
        /// @param colSpan 列跨度
        /// @param rowSpan 行跨度
        void onResize(@NotNull String id, int colSpan, int rowSpan);

        /// 本次隐藏组件。
        ///
        /// @param id 组件 id
        void onHide(@NotNull String id);

        /// 把组件拖到目标索引。
        ///
        /// @param id 组件 id
        /// @param targetIndex 目标索引
        void onReorder(@NotNull String id, int targetIndex);
    }

    /// 创建网格。
    ///
    /// @param context 组件上下文
    /// @param registry 组件注册表
    /// @param listener 编辑操作回调
    public DashboardGrid(
            @NotNull DashboardContext context,
            @NotNull WidgetRegistry registry,
            @NotNull Listener listener) {
        this.context = context;
        this.registry = registry;
        this.listener = listener;
        getStyleClass().add("dashboard-grid");
    }

    /// 更新布局项；会创建新增组件、释放被移除组件。
    ///
    /// @param newItems 新布局项（元素会被复制）
    public void setItems(@NotNull List<DashboardItem> newItems) {
        List<DashboardItem> copy = new ArrayList<>(newItems.size());
        for (DashboardItem item : newItems) {
            copy.add(item.copy());
        }
        this.items = List.copyOf(copy);

        // 释放不再需要（或变为隐藏/未登记）的组件。
        Map<String, DashboardWidget> retained = new LinkedHashMap<>();
        for (DashboardItem item : copy) {
            if (!item.enabled() || !registry.isRegistered(item.id())) {
                continue;
            }
            DashboardWidget widget = widgets.get(item.id());
            if (widget == null) {
                widget = registry.create(item.id(), context);
                if (widget != null) {
                    installCard(widget);
                    widget.initialize();
                    widget.requestRefresh();
                }
            }
            if (widget != null) {
                retained.put(item.id(), widget);
            }
        }
        for (Map.Entry<String, DashboardWidget> entry : widgets.entrySet()) {
            if (!retained.containsKey(entry.getKey())) {
                entry.getValue().dispose();
                getChildren().remove(entry.getValue());
            }
        }
        widgets.clear();
        widgets.putAll(retained);

        getChildren().setAll(widgets.values());
        requestLayout();
    }

    /// 返回当前已创建的组件数量（调试/泄漏审计用）。
    ///
    /// @return 组件数量
    public int widgetCount() {
        return widgets.size();
    }

    /// 刷新全部组件（首页重新可见时调用）。
    public void refreshAll() {
        for (DashboardWidget widget : widgets.values()) {
            widget.requestRefresh();
        }
    }

    /// 设置是否处于编辑模式；切换时重建右键菜单。
    ///
    /// @param editMode 编辑模式标志
    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        for (DashboardWidget widget : widgets.values()) {
            installEditHandlers(widget);
        }
        requestLayout();
    }

    /// 为卡片安装拖拽处理器（仅编辑模式）。
    ///
    /// @param widget 卡片
    private void installEditHandlers(@NotNull DashboardWidget widget) {
        widget.setOnDragDetected(editMode ? event -> {
            draggingId = widget.descriptor().id();
            Dragboard dragboard = widget.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(DRAG_PREFIX + draggingId);
            dragboard.setContent(content);
            widget.setOpacity(0.5);
            event.consume();
        } : null);

        widget.setOnDragDone(editMode ? event -> {
            widget.setOpacity(1);
            draggingId = null;
            event.consume();
        } : null);

        widget.setOnDragOver(editMode ? event -> {
            if (draggingId != null && event.getDragboard().hasString()
                    && event.getDragboard().getString().startsWith(DRAG_PREFIX)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        } : null);

        widget.setOnDragDropped(editMode ? event -> {
            String payload = event.getDragboard().hasString() ? event.getDragboard().getString() : "";
            if (payload.startsWith(DRAG_PREFIX)) {
                String dragged = payload.substring(DRAG_PREFIX.length());
                int targetIndex = indexOf(dragged);
                if (targetIndex >= 0) {
                    listener.onReorder(dragged, targetIndex);
                }
                event.setDropCompleted(true);
            }
            event.consume();
        } : null);

        widget.setOnContextMenuRequested(editMode ? event -> {
            ContextMenu menu = buildContextMenu(widget.descriptor().id());
            menu.show(widget, event.getScreenX(), event.getScreenY());
            event.consume();
        } : null);
    }

    /// 组件 id 在布局项列表中的索引。
    ///
    /// @param id 组件 id
    /// @return 索引；不存在返回 `-1`
    private int indexOf(@NotNull String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /// 构建编辑模式右键菜单。
    ///
    /// @param id 组件 id
    /// @return 菜单
    @NotNull
    private ContextMenu buildContextMenu(@NotNull String id) {
        ContextMenu menu = new ContextMenu();

        MenuItem moveUp = new MenuItem(i18n("community.dashboard.move_up"));
        moveUp.setOnAction(e -> listener.onMove(id, -1));
        MenuItem moveDown = new MenuItem(i18n("community.dashboard.move_down"));
        moveDown.setOnAction(e -> listener.onMove(id, 1));

        MenuItem size1x1 = new MenuItem("1 x 1");
        size1x1.setOnAction(e -> listener.onResize(id, 1, 1));
        MenuItem size2x1 = new MenuItem("2 x 1");
        size2x1.setOnAction(e -> listener.onResize(id, 2, 1));
        MenuItem size2x2 = new MenuItem("2 x 2");
        size2x2.setOnAction(e -> listener.onResize(id, 2, 2));

        MenuItem hide = new MenuItem(i18n("community.dashboard.hide"));
        hide.setOnAction(e -> listener.onHide(id));

        menu.getItems().addAll(
                moveUp, moveDown,
                new SeparatorMenuItem(),
                size1x1, size2x1, size2x2,
                new SeparatorMenuItem(),
                hide);
        return menu;
    }

    /// 创建卡片时安装清空处理器（非编辑模式），避免编辑处理器残留。
    ///
    /// @param widget 卡片
    private void installCard(@NotNull DashboardWidget widget) {
        installEditHandlers(widget);
    }

    /// 布局：列数按宽度自动计算，位置由纯函数给出。
    @Override
    protected void layoutChildren() {
        double width = getWidth();
        int columns = DashboardLayout.autoColumns(width);
        layout = DashboardLayout.computeResult(items, columns);

        double cellWidth = (width - (columns - 1) * (double) DashboardLayout.GAP) / columns;
        if (cellWidth < 0) {
            cellWidth = 0;
        }

        for (DashboardLayout.PlacedItem placed : layout.placed()) {
            DashboardWidget widget = widgets.get(placed.item().id());
            if (widget == null) {
                continue;
            }
            double x = placed.column() * (cellWidth + DashboardLayout.GAP);
            double y = placed.row() * (DashboardLayout.ROW_HEIGHT + DashboardLayout.GAP);
            double w = placed.columnSpan() * cellWidth
                    + (placed.columnSpan() - 1) * (double) DashboardLayout.GAP;
            double h = placed.rowSpan() * DashboardLayout.ROW_HEIGHT
                    + (placed.rowSpan() - 1) * (double) DashboardLayout.GAP;
            widget.resizeRelocate(x, y, Math.max(w, 0), Math.max(h, 0));
        }
    }

    /// 按布局行数计算首选高度，滚动容器据此滚动。
    @Override
    protected double computePrefHeight(double width) {
        int rows = Math.max(1, layout.rows());
        return rows * (double) DashboardLayout.ROW_HEIGHT
                + (rows - 1) * (double) DashboardLayout.GAP;
    }

    /// 按布局行数计算最小高度。
    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }
}
