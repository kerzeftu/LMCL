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
 * Settings > Appearance.
 */
package org.jackhuang.hmcl.community.dashboard.builtin;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.dashboard.DashboardConfig;
import org.jackhuang.hmcl.community.dashboard.DashboardItem;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.dashboard.WidgetRegistry;
import org.jackhuang.hmcl.community.env.CommunityPaths;
import org.jackhuang.hmcl.community.ui.CommunityStyles;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.LineToggleButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// “主页组件”自定义区：设置 > 外观内使用，控制主页各组件的显示/隐藏。
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

    /// 每个组件的开关。
    private final Map<String, LineToggleButton> toggles = new LinkedHashMap<>();

    /// 原配置中已登记的项，用于保留用户调整过的顺序/跨度。
    private final Map<String, DashboardItem> existingItems = new LinkedHashMap<>();

    /// 批量更新开关时不触发落盘。
    private boolean updating;

    /// 构造自定义区。
    public HomeCustomizationPane() {
        CommunityStyles.apply(this);
        setSpacing(10);
        setFillWidth(true);

        for (DashboardItem item : config.items()) {
            existingItems.put(item.id(), item);
        }

        Label hint = new Label(i18n("community.home.widgets.hint"));
        hint.getStyleClass().add("subtitle-label");
        hint.setWrapText(true);

        ComponentList list = new ComponentList();
        List<DashboardWidgetDescriptor> descriptors = new ArrayList<>(registry.all());
        descriptors.removeIf(descriptor -> !descriptor.isValid());
        descriptors.sort(Comparator.comparingInt(DashboardWidgetDescriptor::order));

        for (DashboardWidgetDescriptor descriptor : descriptors) {
            LineToggleButton toggle = new LineToggleButton();
            toggle.setTitle(i18n(descriptor.nameKey()));
            if (descriptor.descriptionKey() != null) {
                toggle.setSubtitle(i18n(descriptor.descriptionKey()));
            }
            toggle.setSelected(isEnabled(descriptor.id()));
            toggle.setDisable(config.readOnly());
            toggle.selectedProperty().addListener((observable, oldValue, newValue) -> save());
            toggles.put(descriptor.id(), toggle);
            list.getContent().add(toggle);
        }

        LineButton reset = new LineButton();
        reset.setTitle(i18n("community.dashboard.reset"));
        reset.setSubtitle(i18n("community.dashboard.reset_confirm"));
        reset.setTrailingIcon(SVG.RESTORE);
        reset.setDisable(config.readOnly());
        reset.setOnAction(event -> confirmReset());
        list.getContent().add(reset);

        getChildren().addAll(hint, list);

        if (config.readOnly()) {
            Label readonly = new Label(i18n("community.home.readonly"));
            readonly.getStyleClass().add("community-warning");
            readonly.setWrapText(true);
            getChildren().add(readonly);
        }
    }

    /// 当前是否显示某个组件；配置中缺失时按默认显示处理。
    private boolean isEnabled(@NotNull String id) {
        DashboardItem item = existingItems.get(id);
        return item == null || item.enabled();
    }

    /// 恢复默认：全部组件显示，顺序/跨度回到描述默认值。
    private void confirmReset() {
        Controllers.confirm(
                i18n("community.dashboard.reset_confirm"),
                i18n("community.dashboard.reset"),
                this::resetToDefault,
                null);
    }

    /// 执行恢复默认。
    private void resetToDefault() {
        updating = true;
        try {
            for (Map.Entry<String, LineToggleButton> entry : toggles.entrySet()) {
                entry.getValue().setSelected(true);
            }
        } finally {
            updating = false;
        }
        existingItems.clear();
        save();
    }

    /// 保存当前开关状态；保留已有项的排序与跨度。
    private void save() {
        if (updating || config.readOnly()) {
            return;
        }

        List<DashboardItem> items = new ArrayList<>();
        for (Map.Entry<String, LineToggleButton> entry : toggles.entrySet()) {
            String id = entry.getKey();
            DashboardWidgetDescriptor descriptor = registry.byId(id);
            if (descriptor == null) {
                continue;
            }
            DashboardItem old = existingItems.get(id);
            items.add(new DashboardItem(
                    id,
                    entry.getValue().isSelected(),
                    old != null ? old.order() : descriptor.order(),
                    old != null ? old.colSpan() : descriptor.defaultColumns(),
                    old != null ? old.rowSpan() : descriptor.defaultRows()));
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
