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
 * Modified by LMCL team on 2026-10-22: home widget customization embedded in
 * Settings > Appearance; per-widget column and ordering.
 */
package org.jackhuang.hmcl.community.dashboard.builtin;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXToggleButton;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// “主页组件”自定义区：设置 > 外观内使用，控制主页各组件的显示、左右栏与顺序。
///
/// 数据与主页共用 `<本地根>/config/dashboard.json`；修改立即写入，并通知当前
/// [HomePane] 从磁盘重建，无需重启启动器。
///
/// 线程：构造与控件事件均在 FX 线程；只读写本地小文件，不做网络请求。
@NotNullByDefault
public final class HomeCustomizationPane extends VBox {

    /// 组件注册表。
    private final WidgetRegistry registry = WidgetRegistry.load();

    /// `dashboard.json` 读写层。
    private final DashboardConfig config = DashboardConfig.load(
            CommunityPaths.localHome().resolve("config").resolve("dashboard.json"));

    /// 原配置中已登记的项，用于保留用户调整过的顺序/跨度/栏位。
    private final Map<String, DashboardItem> existingItems = new LinkedHashMap<>();

    /// 行组件，顺序即主页顺序。
    private final List<Entry> entries = new ArrayList<>();

    /// 可滚动/分组的行列表。
    private final ComponentList list = new ComponentList();

    /// 恢复默认按钮（始终在列表末尾）。
    private final LineButton resetButton = new LineButton();

    /// 批量重建时避免触发保存。
    private boolean updating;

    /// 单行状态。
    private static final class Entry {
        private final DashboardWidgetDescriptor descriptor;
        private final int colSpan;
        private final int rowSpan;
        private boolean enabled;
        private String column;

        private Entry(@NotNull DashboardWidgetDescriptor descriptor, boolean enabled,
                      @NotNull String column, int colSpan, int rowSpan) {
            this.descriptor = descriptor;
            this.enabled = enabled;
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

        Label hint = new Label(i18n("community.home.widgets.hint"));
        hint.getStyleClass().add("subtitle-label");
        hint.setWrapText(true);

        entries.addAll(createEntries());
        entries.sort(Comparator.comparingInt(this::effectiveOrder).thenComparing(entry -> entry.descriptor.id()));

        resetButton.setTitle(i18n("community.dashboard.reset"));
        resetButton.setSubtitle(i18n("community.dashboard.reset_confirm"));
        resetButton.setTrailingIcon(SVG.RESTORE);
        resetButton.setDisable(config.readOnly());
        resetButton.setOnAction(event -> confirmReset());

        getChildren().addAll(hint, list);

        rebuildRows();

        if (config.readOnly()) {
            Label readonly = new Label(i18n("community.home.readonly"));
            readonly.getStyleClass().add("community-warning");
            readonly.setWrapText(true);
            getChildren().add(readonly);
        }
    }

    /// 生成默认/当前配置对应的行（尚未排序）。
    @NotNull
    private List<Entry> createEntries() {
        List<DashboardWidgetDescriptor> descriptors = new ArrayList<>(registry.all());
        descriptors.removeIf(descriptor -> !descriptor.isValid());

        List<Entry> result = new ArrayList<>();
        for (DashboardWidgetDescriptor descriptor : descriptors) {
            DashboardItem old = existingItems.get(descriptor.id());
            boolean enabled = old == null || old.enabled();
            String column = old != null && old.column() != null
                    ? old.column()
                    : HomePane.defaultColumn(descriptor.id());
            int colSpan = old != null ? old.colSpan() : descriptor.defaultColumns();
            int rowSpan = old != null ? old.rowSpan() : descriptor.defaultRows();
            result.add(new Entry(descriptor, enabled, column, colSpan, rowSpan));
        }
        return result;
    }

    /// 当前配置的排序权重；配置中缺失时使用描述符默认值。
    private int effectiveOrder(@NotNull Entry entry) {
        DashboardItem item = existingItems.get(entry.descriptor.id());
        return item != null ? item.order() : entry.descriptor.order();
    }

    /// 重建全部行；移动后调用。
    private void rebuildRows() {
        updating = true;
        try {
            List<Node> rows = new ArrayList<>(entries.size() + 1);
            for (Entry entry : entries) {
                rows.add(buildRow(entry));
            }
            rows.add(resetButton);
            list.getContent().setAll(rows);
        } finally {
            updating = false;
        }
    }

    /// 构建一行：组件名 + 左右栏切换 + 上移/下移 + 显隐开关。
    @NotNull
    private Node buildRow(@NotNull Entry entry) {
        LinePane row = new LinePane();
        row.setTitle(i18n(entry.descriptor.nameKey()));
        if (entry.descriptor.descriptionKey() != null) {
            row.setSubtitle(i18n(entry.descriptor.descriptionKey()));
        }

        JFXButton sideButton = FXUtils.newBorderButton(sideText(entry.column));
        sideButton.setDisable(config.readOnly());
        sideButton.setOnAction(event -> {
            if (updating || config.readOnly()) {
                return;
            }
            entry.column = DashboardItem.COLUMN_LEFT.equals(entry.column)
                    ? DashboardItem.COLUMN_RIGHT
                    : DashboardItem.COLUMN_LEFT;
            rebuildRows();
            save();
        });
        FXUtils.installFastTooltip(sideButton, i18n("community.home.customize.column"));

        JFXButton up = FXUtils.newToggleButton4(SVG.KEYBOARD_ARROW_UP, 18);
        up.setDisable(config.readOnly() || sameColumnNeighbor(entry, -1) < 0);
        up.setOnAction(event -> move(entry, -1));
        FXUtils.installFastTooltip(up, i18n("community.home.customize.move_up"));

        JFXButton down = FXUtils.newToggleButton4(SVG.KEYBOARD_ARROW_DOWN, 18);
        down.setDisable(config.readOnly() || sameColumnNeighbor(entry, 1) < 0);
        down.setOnAction(event -> move(entry, 1));
        FXUtils.installFastTooltip(down, i18n("community.home.customize.move_down"));

        JFXToggleButton toggle = new JFXToggleButton();
        toggle.setSelected(entry.enabled);
        toggle.setSize(8);
        FXUtils.setLimitHeight(toggle, 30);
        toggle.setDisable(config.readOnly());
        toggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (updating || config.readOnly()) {
                return;
            }
            entry.enabled = newValue;
            save();
        });

        HBox controls = new HBox(4, sideButton, up, down, toggle);
        controls.setAlignment(Pos.CENTER_RIGHT);
        controls.setMinWidth(Region.USE_PREF_SIZE);
        row.setRight(controls);
        return row;
    }

    /// 左右栏按钮文案。
    @NotNull
    private static String sideText(@NotNull String column) {
        return i18n(DashboardItem.COLUMN_LEFT.equals(column)
                ? "community.home.customize.left"
                : "community.home.customize.right");
    }

    /// 查找同一栏中相邻的上一/下一项；跨栏项不参与排序交换。
    private int sameColumnNeighbor(@NotNull Entry entry, int delta) {
        int index = entries.indexOf(entry);
        if (index < 0) {
            return -1;
        }
        for (int i = index + delta; i >= 0 && i < entries.size(); i += delta) {
            if (entries.get(i).column.equals(entry.column)) {
                return i;
            }
        }
        return -1;
    }

    /// 在当前栏内上移/下移一行；其他栏的相对顺序保持不变。
    private void move(@NotNull Entry entry, int delta) {
        if (config.readOnly()) {
            return;
        }
        int index = entries.indexOf(entry);
        int target = sameColumnNeighbor(entry, delta);
        if (index < 0 || target < 0) {
            return;
        }
        Collections.swap(entries, index, target);
        rebuildRows();
        save();
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
        entries.clear();
        entries.addAll(createEntries());
        entries.sort(Comparator.comparingInt((Entry entry) -> entry.descriptor.order())
                .thenComparing(entry -> entry.descriptor.id()));
        rebuildRows();
        save();
    }

    /// 保存当前显示/栏位/顺序；顺序按当前行索引写为 10、20、…
    private void save() {
        if (updating || config.readOnly()) {
            return;
        }

        List<DashboardItem> items = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            items.add(new DashboardItem(
                    entry.descriptor.id(),
                    entry.enabled,
                    i * 10,
                    entry.colSpan,
                    entry.rowSpan,
                    entry.column));
        }

        config.setItems(items);
        try {
            config.save();
            HomePane.notifyConfigurationChanged();
        } catch (IOException e) {
            LOG.warning("Failed to save home configuration from settings", e);
        }
    }
}
