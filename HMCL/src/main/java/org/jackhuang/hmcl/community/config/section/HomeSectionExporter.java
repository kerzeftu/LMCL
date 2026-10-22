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
package org.jackhuang.hmcl.community.config.section;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.community.config.SettingsChange;
import org.jackhuang.hmcl.community.config.SettingsSectionExporter;
import org.jackhuang.hmcl.community.dashboard.DashboardConfig;
import org.jackhuang.hmcl.community.dashboard.DashboardItem;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.community.dashboard.WidgetRegistry;
import org.jackhuang.hmcl.community.dashboard.builtin.HomePane;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// “主页布局”设置段：复用 `dashboard.json` 的 schema。
///
/// 不含 `updatedAt`；导入后重建主页左右栏。`schema > 1` 时拒绝导入。
@NotNullByDefault
public final class HomeSectionExporter implements SettingsSectionExporter {

    /// 段 id。
    public static final String ID = "home";

    private static Path configPath() {
        return Metadata.HMCL_LOCAL_HOME.resolve("config").resolve(DashboardConfig.FILE_NAME);
    }

    @Override
    public @NotNull String id() {
        return ID;
    }

    @Override
    public @NotNull String titleKey() {
        return "community.settings.transfer.section.home";
    }

    @Override
    public @NotNull JsonObject exportNow() {
        return DashboardConfig.load(configPath()).toExportJson();
    }

    @Override
    public @NotNull @Unmodifiable List<SettingsChange> preview(@NotNull JsonObject incoming) {
        JsonObject current = exportNow();
        List<ItemView> currentItems = parseItems(current);
        List<ItemView> incomingItems = parseItems(incoming);
        Map<String, ItemView> currentById = byId(currentItems);
        Map<String, ItemView> incomingById = byId(incomingItems);

        List<ItemView> added = new ArrayList<>();
        List<ItemView> removed = new ArrayList<>();
        List<ItemView> toggled = new ArrayList<>();
        List<ItemView> moved = new ArrayList<>();
        List<ItemView> resized = new ArrayList<>();
        for (ItemView item : incomingItems) {
            ItemView old = currentById.get(item.id());
            if (old == null) {
                added.add(item);
            } else {
                if (old.enabled() != item.enabled()) {
                    toggled.add(item);
                }
                if (!equalsColumn(old.column(), item.column())) {
                    moved.add(item);
                }
                if (old.colSpan() != item.colSpan() || old.rowSpan() != item.rowSpan()) {
                    resized.add(item);
                }
            }
        }
        for (ItemView item : currentItems) {
            if (!incomingById.containsKey(item.id())) {
                removed.add(item);
            }
        }

        WidgetRegistry registry = WidgetRegistry.load();
        List<String> lines = new ArrayList<>();
        appendSection(lines, "community.settings.transfer.preview.home.added", added, registry);
        appendSection(lines, "community.settings.transfer.preview.home.removed", removed, registry);
        if (!toggled.isEmpty()) {
            lines.add(i18n("community.settings.transfer.preview.home.toggled", toggled.size()));
            for (ItemView item : toggled) {
                lines.add(i18n("community.settings.transfer.preview.home.toggle_line",
                        widgetName(item.id(), registry),
                        i18n(item.enabled()
                                ? "community.settings.transfer.preview.home.enabled"
                                : "community.settings.transfer.preview.home.disabled")));
            }
        }
        if (!moved.isEmpty()) {
            lines.add(i18n("community.settings.transfer.preview.home.moved", moved.size()));
            for (ItemView item : moved) {
                ItemView old = currentById.get(item.id());
                lines.add(i18n("community.settings.transfer.preview.home.move_line",
                        widgetName(item.id(), registry),
                        columnLabel(old == null ? null : old.column()),
                        columnLabel(item.column())));
            }
        }
        if (!resized.isEmpty()) {
            lines.add(i18n("community.settings.transfer.preview.home.resized", resized.size()));
            for (ItemView item : resized) {
                lines.add(i18n("community.settings.transfer.preview.home.size_line",
                        widgetName(item.id(), registry), item.colSpan(), item.rowSpan()));
            }
        }

        boolean orderChanged = !ids(currentItems).equals(ids(incomingItems));
        if (lines.isEmpty() && orderChanged && currentItems.size() == incomingItems.size()) {
            lines.add(i18n("community.settings.transfer.preview.home.reordered"));
            for (ItemView item : incomingItems) {
                lines.add(widgetName(item.id(), registry));
            }
        }

        List<SettingsChange> changes = new ArrayList<>();
        if (!lines.isEmpty()) {
            changes.add(new SettingsChange(ID, "items", null, String.join("\n", lines),
                    i18n("community.settings.transfer.preview.home.layout")));
        }

        int currentPinned = arraySize(current.get("pinnedInstanceIds"));
        int incomingPinned = arraySize(incoming.get("pinnedInstanceIds"));
        if (currentPinned != incomingPinned) {
            changes.add(new SettingsChange(ID, "pinnedInstanceIds",
                    i18n("community.settings.transfer.value.items", currentPinned),
                    i18n("community.settings.transfer.value.items", incomingPinned),
                    i18n("community.settings.transfer.preview.home.pinned")));
        }

        int currentProps = objectSize(current.get("widgetProps"));
        int incomingProps = objectSize(incoming.get("widgetProps"));
        if (currentProps != incomingProps) {
            changes.add(new SettingsChange(ID, "widgetProps",
                    i18n("community.settings.transfer.value.items", currentProps),
                    i18n("community.settings.transfer.value.items", incomingProps),
                    i18n("community.settings.transfer.preview.home.widget_props")));
        }
        return List.copyOf(changes);
    }

    private static void appendSection(@NotNull List<String> lines, @NotNull String titleKey,
                                      @NotNull List<ItemView> items, @NotNull WidgetRegistry registry) {
        if (items.isEmpty()) {
            return;
        }
        lines.add(i18n(titleKey, items.size()));
        for (ItemView item : items) {
            lines.add(widgetName(item.id(), registry));
        }
    }

    private static @NotNull String widgetName(@NotNull String id, @NotNull WidgetRegistry registry) {
        @Nullable DashboardWidgetDescriptor descriptor = registry.byId(id);
        return descriptor != null ? i18n(descriptor.nameKey()) : id;
    }

    private static @NotNull String columnLabel(@Nullable String column) {
        if (DashboardItem.COLUMN_LEFT.equals(column)) {
            return i18n("community.home.customize.section.left");
        }
        if (DashboardItem.COLUMN_RIGHT.equals(column)) {
            return i18n("community.home.customize.section.right");
        }
        return i18n("community.settings.transfer.preview.home.column.default");
    }

    private static boolean equalsColumn(@Nullable String first, @Nullable String second) {
        return java.util.Objects.equals(first, second);
    }

    private static @NotNull List<String> ids(@NotNull List<ItemView> items) {
        return items.stream().map(ItemView::id).toList();
    }

    private static @NotNull Map<String, ItemView> byId(@NotNull List<ItemView> items) {
        Map<String, ItemView> result = new LinkedHashMap<>();
        for (ItemView item : items) {
            result.putIfAbsent(item.id(), item);
        }
        return result;
    }

    private static @NotNull List<ItemView> parseItems(@NotNull JsonObject object) {
        JsonElement element = object.get("items");
        if (!(element instanceof JsonArray array)) {
            return List.of();
        }
        List<ItemView> items = new ArrayList<>();
        int index = 0;
        for (JsonElement value : array) {
            if (!(value instanceof JsonObject item)) {
                continue;
            }
            String id = JsonUtils.getString(item, "id");
            if (!DashboardItem.isValidId(id)) {
                continue;
            }
            items.add(new ItemView(
                    id,
                    JsonUtils.getBoolean(item, "enabled", true),
                    JsonUtils.getInt(item, "order", index * 10),
                    DashboardItem.clampSpan(JsonUtils.getInt(item, "colSpan", 1)),
                    DashboardItem.clampSpan(JsonUtils.getInt(item, "rowSpan", 1)),
                    DashboardItem.normalizeColumn(JsonUtils.getString(item, "column"))));
            index++;
        }
        return List.copyOf(items);
    }

    private static int arraySize(@Nullable JsonElement element) {
        return element instanceof JsonArray array ? array.size() : 0;
    }

    private static int objectSize(@Nullable JsonElement element) {
        return element instanceof JsonObject object ? object.size() : 0;
    }

    /// 主页布局项的最小只读视图。
    ///
    /// @param id 组件 id
    /// @param enabled 是否启用
    /// @param order 排序权重
    /// @param colSpan 列跨度
    /// @param rowSpan 行跨度
    /// @param column 栏位
    private record ItemView(@NotNull String id, boolean enabled, int order,
                            int colSpan, int rowSpan, @Nullable String column) {
    }

    @Override
    public void apply(@NotNull JsonObject incoming) {
        DashboardConfig config = DashboardConfig.load(configPath());
        if (config.readOnly()) {
            throw new IllegalStateException("dashboard configuration is read-only");
        }
        config.replaceFromJson(incoming);
        try {
            config.save();
        } catch (IOException e) {
            throw new IllegalStateException("failed to save dashboard: " + e.getMessage(), e);
        }
        HomePane.notifyConfigurationChanged();
    }
}
