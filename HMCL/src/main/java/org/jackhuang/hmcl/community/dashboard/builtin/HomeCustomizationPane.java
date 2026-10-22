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
 *
 * Modified by LMCL team on 2026-10-23: home widget customization uses
 * left/right/disabled columns and drag-and-drop instead of move buttons.
 */
package org.jackhuang.hmcl.community.dashboard.builtin;

import javafx.css.PseudoClass;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.dashboard.DashboardConfig;
import org.jackhuang.hmcl.community.dashboard.DashboardItem;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.dashboard.WidgetRegistry;
import org.jackhuang.hmcl.community.env.CommunityPaths;
import org.jackhuang.hmcl.community.ui.CommunityStyles;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.LinePane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// “主页组件”自定义区：设置 > 外观内使用，控制主页各组件的显示、左右栏与顺序。
///
/// 布局分为左栏、右栏、未启用三块。可以直接拖动组件排序，也可以把组件拖到
/// 另一块完成换栏或启用/停用；不再使用上移/下移按钮。
///
/// 数据与主页共用 `<本地根>/config/dashboard.json`；修改立即写入，并通知当前
/// [HomePane] 从磁盘重建，无需重启启动器。
///
/// 线程：构造与控件事件均在 FX 线程；只读写本地小文件，不做网络请求。
@NotNullByDefault
public final class HomeCustomizationPane extends VBox {

    /// 拖拽载荷前缀，避免接受来自其他控件的随意字符串。
    private static final String DRAG_PREFIX = "lmcl-home-widget:";

    /// 行在目标位置之前高亮。
    private static final PseudoClass DROP_BEFORE = PseudoClass.getPseudoClass("drop-before");

    /// 行在目标位置之后高亮。
    private static final PseudoClass DROP_AFTER = PseudoClass.getPseudoClass("drop-after");

    /// 整块作为拖放目标时高亮。
    private static final PseudoClass DROP_TARGET = PseudoClass.getPseudoClass("drop-target");

    /// 组件注册表。
    private final WidgetRegistry registry = WidgetRegistry.load();

    /// `dashboard.json` 读写层。
    private final DashboardConfig config = DashboardConfig.load(
            CommunityPaths.localHome().resolve("config").resolve("dashboard.json"));

    /// 原配置中已登记的项，用于保留用户调整过的顺序/跨度/栏位。
    private final Map<String, DashboardItem> existingItems = new LinkedHashMap<>();

    /// 左栏。
    private final WidgetSection leftSection;

    /// 右栏。
    private final WidgetSection rightSection;

    /// 未启用组件。
    private final WidgetSection disabledSection;

    /// 恢复默认按钮（始终在列表末尾）。
    private final LineButton resetButton = new LineButton();

    /// 单行数据。
    private static final class Entry {
        private final DashboardWidgetDescriptor descriptor;
        private final int colSpan;
        private final int rowSpan;
        private String column;

        private Entry(@NotNull DashboardWidgetDescriptor descriptor,
                      @NotNull String column, int colSpan, int rowSpan) {
            this.descriptor = descriptor;
            this.column = column;
            this.colSpan = colSpan;
            this.rowSpan = rowSpan;
        }
    }

    /// 构造自定义区。
    public HomeCustomizationPane() {
        CommunityStyles.apply(this);
        setSpacing(10);
        setFillWidth(true);

        for (DashboardItem item : config.items()) {
            existingItems.putIfAbsent(item.id(), item);
        }

        leftSection = new WidgetSection(
                DashboardItem.COLUMN_LEFT,
                "community.home.customize.section.left",
                "community.home.customize.section.left_hint");
        rightSection = new WidgetSection(
                DashboardItem.COLUMN_RIGHT,
                "community.home.customize.section.right",
                "community.home.customize.section.right_hint");
        disabledSection = new WidgetSection(
                null,
                "community.home.customize.section.disabled",
                "community.home.customize.section.disabled_hint");

        Label hint = new Label(i18n("community.home.widgets.hint"));
        hint.getStyleClass().add("subtitle-label");
        hint.setWrapText(true);

        HBox columns = new HBox(12, leftSection.root, rightSection.root);
        columns.setFillHeight(true);
        HBox.setHgrow(leftSection.root, Priority.ALWAYS);
        HBox.setHgrow(rightSection.root, Priority.ALWAYS);
        leftSection.root.setPrefWidth(0);
        rightSection.root.setPrefWidth(0);
        leftSection.root.setMaxWidth(Double.MAX_VALUE);
        rightSection.root.setMaxWidth(Double.MAX_VALUE);
        disabledSection.root.setMaxWidth(Double.MAX_VALUE);

        resetButton.setTitle(i18n("community.dashboard.reset"));
        resetButton.setSubtitle(i18n("community.dashboard.reset_confirm"));
        resetButton.setTrailingIcon(SVG.RESTORE);
        resetButton.setDisable(config.readOnly());
        resetButton.setOnAction(event -> confirmReset());

        ComponentList actions = new ComponentList();
        actions.getContent().add(resetButton);

        loadEntries();
        rebuildSections();

        getChildren().addAll(hint, columns, disabledSection.root, actions);

        if (config.readOnly()) {
            Label readonly = new Label(i18n("community.home.readonly"));
            readonly.getStyleClass().add("community-warning");
            readonly.setWrapText(true);
            getChildren().add(readonly);
        }
    }

    /// 从注册表与旧配置生成本地条目，并按栏位分组。
    private void loadEntries() {
        leftSection.entries.clear();
        rightSection.entries.clear();
        disabledSection.entries.clear();

        List<DashboardWidgetDescriptor> descriptors = new ArrayList<>(registry.all());
        descriptors.removeIf(descriptor -> !descriptor.isValid());

        for (DashboardWidgetDescriptor descriptor : descriptors) {
            DashboardItem old = existingItems.get(descriptor.id());
            boolean enabled = old == null || old.enabled();
            String column = old != null && old.column() != null
                    ? old.column()
                    : HomePane.defaultColumn(descriptor.id());
            int colSpan = old != null ? old.colSpan() : descriptor.defaultColumns();
            int rowSpan = old != null ? old.rowSpan() : descriptor.defaultRows();

            Entry entry = new Entry(descriptor, column, colSpan, rowSpan);
            if (!enabled) {
                disabledSection.entries.add(entry);
            } else if (DashboardItem.COLUMN_LEFT.equals(column)) {
                leftSection.entries.add(entry);
            } else {
                rightSection.entries.add(entry);
            }
        }

        Comparator<Entry> byOrder = Comparator
                .comparingInt(this::effectiveOrder)
                .thenComparing(entry -> entry.descriptor.id());
        leftSection.entries.sort(byOrder);
        rightSection.entries.sort(byOrder);
        disabledSection.entries.sort(byOrder);
    }

    /// 当前配置的排序权重；配置中缺失时使用描述符默认值。
    private int effectiveOrder(@NotNull Entry entry) {
        DashboardItem item = existingItems.get(entry.descriptor.id());
        return item != null ? item.order() : entry.descriptor.order();
    }

    /// 重建三个栏位的行。
    private void rebuildSections() {
        leftSection.rebuild();
        rightSection.rebuild();
        disabledSection.rebuild();
    }

    /// 查找条目所属的栏位。
    @NotNull
    private WidgetSection sectionOf(@NotNull Entry entry) {
        if (leftSection.entries.contains(entry)) {
            return leftSection;
        }
        if (rightSection.entries.contains(entry)) {
            return rightSection;
        }
        return disabledSection;
    }

    /// 按 id 查找条目。
    private @Nullable Entry findEntry(@NotNull String id) {
        for (WidgetSection section : List.of(leftSection, rightSection, disabledSection)) {
            for (Entry entry : section.entries) {
                if (entry.descriptor.id().equals(id)) {
                    return entry;
                }
            }
        }
        return null;
    }

    /// 处理一次拖放：从源栏位移除，插入目标栏位。
    private void handleDrop(@NotNull String id, @NotNull WidgetSection target, int targetIndex) {
        if (config.readOnly()) {
            return;
        }

        Entry entry = findEntry(id);
        if (entry == null) {
            return;
        }

        WidgetSection source = sectionOf(entry);
        int sourceIndex = source.entries.indexOf(entry);
        int index = Math.max(0, Math.min(targetIndex, target.entries.size()));

        if (source == target) {
            // 放在自己原来的位置、或紧邻其后，不产生变化。
            if (index == sourceIndex || index == sourceIndex + 1) {
                clearDropDecorations();
                return;
            }
            source.entries.remove(entry);
            if (index > sourceIndex) {
                index--;
            }
        } else {
            source.entries.remove(entry);
        }

        target.entries.add(Math.max(0, Math.min(index, target.entries.size())), entry);
        if (target.column != null) {
            entry.column = target.column;
        }

        rebuildSections();
        save();
    }

    /// 保存当前显示/栏位/顺序；左右栏顺序按块写入 `order`。
    private void save() {
        if (config.readOnly()) {
            return;
        }

        List<DashboardItem> items = new ArrayList<>();
        int order = 0;
        for (Entry entry : leftSection.entries) {
            order += 10;
            items.add(toItem(entry, true, DashboardItem.COLUMN_LEFT, order));
        }
        for (Entry entry : rightSection.entries) {
            order += 10;
            items.add(toItem(entry, true, DashboardItem.COLUMN_RIGHT, order));
        }
        for (Entry entry : disabledSection.entries) {
            order += 10;
            items.add(toItem(entry, false, entry.column, order));
        }

        config.setItems(items);
        try {
            config.save();
            HomePane.notifyConfigurationChanged();
        } catch (IOException e) {
            LOG.warning("Failed to save home configuration from settings", e);
        }
    }

    /// 把本地条目转为写盘项。
    @NotNull
    private DashboardItem toItem(@NotNull Entry entry, boolean enabled,
                                 @NotNull String column, int order) {
        return new DashboardItem(
                entry.descriptor.id(),
                enabled,
                order,
                entry.colSpan,
                entry.rowSpan,
                column);
    }

    /// 清除全部拖放高亮。
    private void clearDropDecorations() {
        leftSection.clearDrop();
        rightSection.clearDrop();
        disabledSection.clearDrop();
    }

    /// 恢复默认：全部组件显示，顺序/栏位回到描述默认值。
    private void confirmReset() {
        Controllers.confirm(
                i18n("community.dashboard.reset_confirm"),
                i18n("community.dashboard.reset"),
                this::resetToDefault,
                null);
    }

    /// 执行恢复默认。
    private void resetToDefault() {
        existingItems.clear();
        loadEntries();
        rebuildSections();
        save();
    }

    /// 一个栏位：左栏、右栏或未启用。
    private final class WidgetSection {
        private final @Nullable String column;
        private final String titleKey;
        private final List<Entry> entries = new ArrayList<>();
        private final VBox root = new VBox(4);
        private final Label header = new Label();
        private final Label hint = new Label();
        private final ComponentList list = new ComponentList();
        private final Label placeholder = new Label();

        private WidgetSection(@Nullable String column,
                              @NotNull String titleKey, @NotNull String hintKey) {
            this.column = column;
            this.titleKey = titleKey;

            root.getStyleClass().add("community-widget-column");
            root.setMinWidth(0);

            header.getStyleClass().add("title-label");
            hint.getStyleClass().add("subtitle-label");
            hint.setWrapText(true);
            hint.setText(i18n(hintKey));

            placeholder.getStyleClass().add("community-widget-placeholder");
            placeholder.setWrapText(true);
            placeholder.setText(i18n("community.home.customize.empty"));

            root.getChildren().addAll(header, hint, list, placeholder);
            root.setOnDragOver(this::onDragOver);
            root.setOnDragDropped(this::onDragDropped);
            root.setOnDragExited(event -> clearDrop());
        }

        /// 重建本栏行。
        private void rebuild() {
            List<Node> rows = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                rows.add(buildRow(entry));
            }
            list.getContent().setAll(rows);
            header.setText(i18n(titleKey) + " (" + entries.size() + ")");
            list.setVisible(!entries.isEmpty());
            list.setManaged(!entries.isEmpty());
            placeholder.setVisible(entries.isEmpty());
            placeholder.setManaged(entries.isEmpty());
        }

        /// 构建一行：组件名 + 拖动手柄。
        @NotNull
        private Node buildRow(@NotNull Entry entry) {
            LinePane row = new LinePane();
            row.getStyleClass().add("community-widget-row");
            row.setMaxWidth(Double.MAX_VALUE);
            row.setTitle(i18n(entry.descriptor.nameKey()));
            if (entry.descriptor.descriptionKey() != null) {
                row.setSubtitle(i18n(entry.descriptor.descriptionKey()));
            }

            StackPane handle = new StackPane(SVG.MENU.createIcon(16));
            handle.getStyleClass().add("community-widget-drag-handle");
            handle.setMouseTransparent(true);
            row.setRight(handle);

            if (!config.readOnly()) {
                row.setCursor(Cursor.OPEN_HAND);
                FXUtils.installFastTooltip(row, i18n("community.home.customize.drag_handle"));
                row.setOnDragDetected(event -> {
                    Dragboard dragboard = row.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(DRAG_PREFIX + entry.descriptor.id());
                    dragboard.setContent(content);
                    event.consume();
                });
                row.setOnDragOver(event -> {
                    if (!isWidgetDrag(event) || config.readOnly()) {
                        return;
                    }
                    event.acceptTransferModes(TransferMode.MOVE);
                    showDrop(dropIndexForRow(entry, event.getY()));
                    event.consume();
                });
                row.setOnDragDropped(event -> {
                    if (!isWidgetDrag(event) || config.readOnly()) {
                        return;
                    }
                    String id = draggedId(event);
                    if (id == null) {
                        return;
                    }
                    handleDrop(id, this, dropIndexForRow(entry, event.getY()));
                    event.setDropCompleted(true);
                    event.consume();
                });
                row.setOnDragExited(event -> clearDrop());
                row.setOnDragDone(event -> clearDrop());
            }

            return row;
        }

        /// 行内拖放位置：上半部分插到该行之前，下半部分插到之后。
        private int dropIndexForRow(@NotNull Entry entry, double y) {
            int index = entries.indexOf(entry);
            if (index < 0) {
                return entries.size();
            }
            double height = list.getContent().isEmpty()
                    ? 0
                    : list.getContent().get(index).getBoundsInParent().getHeight();
            return y < height / 2 ? index : index + 1;
        }

        /// 栏位空白处拖放：追加到末尾。
        private void onDragOver(@NotNull DragEvent event) {
            if (!isWidgetDrag(event) || config.readOnly()) {
                return;
            }
            event.acceptTransferModes(TransferMode.MOVE);
            showDrop(entries.size());
            event.consume();
        }

        /// 栏位空白处放下。
        private void onDragDropped(@NotNull DragEvent event) {
            if (!isWidgetDrag(event) || config.readOnly()) {
                return;
            }
            String id = draggedId(event);
            if (id == null) {
                return;
            }
            handleDrop(id, this, entries.size());
            event.setDropCompleted(true);
            event.consume();
        }

        /// 显示插入位置高亮。
        private void showDrop(int index) {
            clearDrop();
            int clamped = Math.max(0, Math.min(index, entries.size()));
            root.pseudoClassStateChanged(DROP_TARGET, true);
            if (clamped < entries.size()) {
                list.getContent().get(clamped).pseudoClassStateChanged(DROP_BEFORE, true);
            } else if (!entries.isEmpty()) {
                list.getContent().get(entries.size() - 1).pseudoClassStateChanged(DROP_AFTER, true);
            }
        }

        /// 清除本栏拖放高亮。
        private void clearDrop() {
            root.pseudoClassStateChanged(DROP_TARGET, false);
            for (Node node : list.getContent()) {
                node.pseudoClassStateChanged(DROP_BEFORE, false);
                node.pseudoClassStateChanged(DROP_AFTER, false);
            }
        }
    }

    /// 是否为本文档组件拖拽。
    private static boolean isWidgetDrag(@NotNull DragEvent event) {
        Dragboard dragboard = event.getDragboard();
        return dragboard.hasString() && dragboard.getString().startsWith(DRAG_PREFIX);
    }

    /// 读取拖拽的组件 id；载荷非法时返回 `null`。
    private static @Nullable String draggedId(@NotNull DragEvent event) {
        Dragboard dragboard = event.getDragboard();
        if (!dragboard.hasString()) {
            return null;
        }
        String value = dragboard.getString();
        if (!value.startsWith(DRAG_PREFIX)) {
            return null;
        }
        String id = value.substring(DRAG_PREFIX.length());
        return DashboardItem.isValidId(id) ? id : null;
    }
}
