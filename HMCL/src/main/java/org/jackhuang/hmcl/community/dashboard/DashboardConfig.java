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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// `dashboard.json` 的读写层：schema、未知字段保留、损坏回退、原子写。
///
/// 路径由 [CommunityDashboard] 决定（`<本地根>/config/dashboard.json`）。
/// 本类不触碰 JavaFX，可在任意线程调用；但实例本身不是线程安全的，
/// 约定只在 FX 线程创建与修改。
///
/// 读取规则：
/// - 文件不存在 → 默认空布局（`items = []`）；
/// - `schema > 1` → 只读模式，按能解析的部分展示，**绝不写回**；
/// - JSON 损坏/类型错误 → 备份为 `dashboard.json.corrupt-<epochSecond>` 并使用默认布局；
/// - 未知字段保留在原始 [JsonObject] 中，写回时合并，升级不丢字段。
@NotNullByDefault
public final class DashboardConfig {

    /// 当前 schema 版本。
    public static final int SCHEMA = 1;

    /// `items` 数量上限；超出时丢弃 `order` 最大者。
    public static final int MAX_ITEMS = 30;

    /// 配置文件名。
    public static final String FILE_NAME = "dashboard.json";

    /// 配置文件路径。
    private final Path path;

    /// 读取到的原始 JSON，用于保留未知字段。
    private JsonObject raw = new JsonObject();

    /// schema 版本；`> 1` 时 [readOnly] 为 `true`。
    private int schema = SCHEMA;

    /// 列数配置，目前只支持 `"auto"`。
    private String columns = "auto";

    /// 布局项。
    private final List<DashboardItem> items = new ArrayList<>();

    /// 置顶实例 id（步骤 14 使用）。
    private final List<String> pinnedInstanceIds = new ArrayList<>();

    /// 组件偏好段（`widgetProps`）。
    private JsonObject widgetProps = new JsonObject();

    /// 是否只读（`schema > 1`）。
    private boolean readOnly;

    /// 最近一次写入/读取的时间戳。
    private Instant updatedAt = Instant.EPOCH;

    /// 是否是首次运行（无配置文件或配置文件损坏）。
    ///
    /// 只有首次运行才允许写入默认布局；用户主动清空布局后保持空，不再回填。
    private boolean firstRun = true;

    /// 使用配置路径构造空布局（不读盘）。
    ///
    /// @param path `dashboard.json` 的路径
    public DashboardConfig(@NotNull Path path) {
        this.path = path;
    }

    /// 从磁盘加载配置；任何异常都不会抛出，而是回退到默认布局。
    ///
    /// @param path `dashboard.json` 的路径
    /// @return 配置实例
    @NotNull
    public static DashboardConfig load(@NotNull Path path) {
        DashboardConfig config = new DashboardConfig(path);
        config.reload();
        return config;
    }

    /// 重新从磁盘加载；读取失败时丢弃旧状态并使用默认布局（保留路径）。
    public void reload() {
        items.clear();
        pinnedInstanceIds.clear();
        widgetProps = new JsonObject();
        raw = new JsonObject();
        schema = SCHEMA;
        columns = "auto";
        readOnly = false;
        updatedAt = Instant.EPOCH;

        if (!Files.isRegularFile(path)) {
            firstRun = true;
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement element = JsonUtils.GSON.fromJson(json, JsonElement.class);
            if (!(element instanceof JsonObject object)) {
                throw new JsonParseException("dashboard.json root is not an object");
            }
            parse(object);
            firstRun = false;
        } catch (Throwable e) {
            firstRun = true;
            LOG.warning("Failed to load dashboard config " + path + ", falling back to defaults", e);
            backupCorrupt();
            items.clear();
            pinnedInstanceIds.clear();
            widgetProps = new JsonObject();
            raw = new JsonObject();
            schema = SCHEMA;
            columns = "auto";
            readOnly = false;
        }
    }

    /// 解析根对象；调用前调用方已清空状态。
    ///
    /// @param object 根 JSON 对象
    private void parse(@NotNull JsonObject object) {
        raw = object.deepCopy();
        schema = JsonUtils.getInt(object, "schema", SCHEMA);
        if (schema < 1) {
            schema = SCHEMA;
        }
        readOnly = schema > SCHEMA;

        // schema v1 只定义 auto 列数；未知值也回退到 auto，避免布局在旧版本上错乱。
        columns = "auto";

        JsonElement itemsElement = object.get("items");
        if (itemsElement instanceof JsonArray array) {
            int index = 0;
            for (JsonElement element : array) {
                if (!(element instanceof JsonObject itemObject)) {
                    continue;
                }
                String id = JsonUtils.getString(itemObject, "id");
                if (!DashboardItem.isValidId(id)) {
                    LOG.warning("Ignored dashboard item with invalid id: " + id);
                    continue;
                }
                boolean enabled = JsonUtils.getBoolean(itemObject, "enabled", true);
                int order = JsonUtils.getInt(itemObject, "order", index * 10);
                int colSpan = JsonUtils.getInt(itemObject, "colSpan", 1);
                int rowSpan = JsonUtils.getInt(itemObject, "rowSpan", 1);
                items.add(new DashboardItem(id, enabled, order, colSpan, rowSpan));
                index++;
            }
        }

        JsonElement pinnedElement = object.get("pinnedInstanceIds");
        if (pinnedElement instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element instanceof JsonPrimitive primitive && primitive.isString()) {
                    pinnedInstanceIds.add(primitive.getAsString());
                }
            }
        }

        JsonElement propsElement = object.get("widgetProps");
        if (propsElement instanceof JsonObject propsObject) {
            widgetProps = propsObject.deepCopy();
        }

        String updatedAtText = JsonUtils.getString(object, "updatedAt");
        if (updatedAtText != null) {
            try {
                updatedAt = Instant.parse(updatedAtText);
            } catch (RuntimeException e) {
                updatedAt = Instant.EPOCH;
            }
        }

        trimItems();
    }

    /// 超过 [MAX_ITEMS] 时丢弃 `order` 最大的项（写出前调用）。
    private void trimItems() {
        if (items.size() <= MAX_ITEMS) {
            return;
        }
        items.sort(Comparator.comparingInt(DashboardItem::order));
        while (items.size() > MAX_ITEMS) {
            DashboardItem removed = items.remove(items.size() - 1);
            LOG.warning("Dropped dashboard item (over " + MAX_ITEMS + "): " + removed.id());
        }
    }

    /// 把损坏文件备份为 `dashboard.json.corrupt-<epochSecond>`；失败只记日志。
    private void backupCorrupt() {
        try {
            Path backup = path.resolveSibling(FILE_NAME + ".corrupt-" + Instant.now().getEpochSecond());
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
            LOG.warning("Backed up corrupt dashboard config to " + backup);
        } catch (IOException e) {
            LOG.warning("Failed to back up corrupt dashboard config " + path, e);
        }
    }

    /// 把内存状态写回磁盘（原子写）。只读模式下直接返回。
    ///
    /// @throws IOException 写入失败时抛出，调用方负责提示用户
    public void save() throws IOException {
        if (readOnly) {
            return;
        }
        trimItems();

        JsonObject root = raw == null ? new JsonObject() : raw.deepCopy();
        root.addProperty("schema", SCHEMA);
        root.addProperty("columns", columns);

        JsonArray itemsArray = new JsonArray();
        for (DashboardItem item : items) {
            JsonObject itemObject = new JsonObject();
            itemObject.addProperty("id", item.id());
            itemObject.addProperty("enabled", item.enabled());
            itemObject.addProperty("order", item.order());
            itemObject.addProperty("colSpan", item.colSpan());
            itemObject.addProperty("rowSpan", item.rowSpan());
            itemsArray.add(itemObject);
        }
        root.add("items", itemsArray);

        JsonArray pinnedArray = new JsonArray();
        for (String id : pinnedInstanceIds) {
            pinnedArray.add(id);
        }
        root.add("pinnedInstanceIds", pinnedArray);
        root.add("widgetProps", widgetProps.deepCopy());

        Instant now = Instant.now();
        updatedAt = now;
        root.addProperty("updatedAt", DateTimeFormatter.ISO_INSTANT.format(now));

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = path.resolveSibling(FILE_NAME + ".tmp");
        Files.writeString(temp, JsonUtils.GSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }

        raw = root;
    }

    /// 返回配置路径。
    ///
    /// @return `dashboard.json` 路径
    @NotNull
    public Path path() {
        return path;
    }

    /// 返回 schema 版本。
    ///
    /// @return schema
    public int schema() {
        return schema;
    }

    /// 是否只读（`schema > 1`）。只读时禁止编辑与写盘。
    ///
    /// @return 只读标志
    public boolean readOnly() {
        return readOnly;
    }

    /// 是否是首次运行（无配置文件，或文件存在但解析失败）。
    ///
    /// 首次运行时调用方才允许写入默认布局；用户清空布局后本值保持 `false`。
    ///
    /// @return 首次运行标志
    public boolean firstRun() {
        return firstRun;
    }

    /// 返回列数配置（当前恒为 `"auto"`）。
    ///
    /// @return `"auto"`
    @NotNull
    public String columns() {
        return columns;
    }

    /// 返回布局项的只读视图（顺序与内存一致）。
    ///
    /// @return 不可变列表
    @NotNull
    @Unmodifiable
    public List<DashboardItem> items() {
        return List.copyOf(items);
    }

    /// 用给定布局项替换内存中的列表（不写盘）。
    ///
    /// @param newItems 新布局项；元素会被复制
    public void setItems(@NotNull List<DashboardItem> newItems) {
        items.clear();
        for (DashboardItem item : newItems) {
            items.add(item.copy());
        }
        trimItems();
    }

    /// 返回置顶实例 id 的只读视图。
    ///
    /// @return 不可变列表
    @NotNull
    @Unmodifiable
    public List<String> pinnedInstanceIds() {
        return List.copyOf(pinnedInstanceIds);
    }

    /// 覆盖置顶实例 id（不写盘）。
    ///
    /// @param ids 新列表
    public void setPinnedInstanceIds(@NotNull List<String> ids) {
        pinnedInstanceIds.clear();
        pinnedInstanceIds.addAll(ids);
    }

    /// 返回最近一次写入/读取的时间戳；没有时为 [Instant#EPOCH]。
    ///
    /// @return 时间戳
    @NotNull
    public Instant updatedAt() {
        return updatedAt;
    }

    /// 返回组件偏好视图；每次调用返回新视图，共享同一份内存数据。
    ///
    /// @param dirty 任意写操作后回调（用于防抖落盘）
    /// @return 偏好视图
    @NotNull
    public DashboardPrefs prefs(@NotNull Runnable dirty) {
        return new Prefs(dirty);
    }

    /// [DashboardPrefs] 的内存实现。
    @NotNullByDefault
    private final class Prefs implements DashboardPrefs {

        /// 写操作后的回调。
        private final Runnable dirty;

        /// 创建视图。
        ///
        /// @param dirty 写操作后的回调
        private Prefs(@NotNull Runnable dirty) {
            this.dirty = dirty;
        }

        /// 读取 `widgetProps[key]` 的字符串值。
        ///
        /// @param key 偏好 key
        /// @return 值或 `null`
        @Override
        @Nullable
        public String getString(@NotNull String key) {
            return JsonUtils.getString(widgetProps, key);
        }

        /// 读取布尔值。
        ///
        /// @param key 偏好 key
        /// @param defaultValue 默认值
        /// @return 值
        @Override
        public boolean getBoolean(@NotNull String key, boolean defaultValue) {
            return JsonUtils.getBoolean(widgetProps, key, defaultValue);
        }

        /// 读取字符串列表。
        ///
        /// @param key 偏好 key
        /// @return 不可变列表；缺失时为空列表
        @Override
        @NotNull
        @Unmodifiable
        public List<String> getStringList(@NotNull String key) {
            JsonElement element = widgetProps.get(key);
            if (!(element instanceof JsonArray array)) {
                return List.of();
            }
            List<String> result = new ArrayList<>(array.size());
            for (JsonElement value : array) {
                if (value instanceof JsonPrimitive primitive && primitive.isString()) {
                    result.add(primitive.getAsString());
                }
            }
            return List.copyOf(result);
        }

        /// 写入/删除偏好。
        ///
        /// @param key 偏好 key
        /// @param value 可 JSON 序列化的值；`null` 表示删除
        @Override
        public void put(@NotNull String key, @Nullable Object value) {
            if (value == null) {
                widgetProps.remove(key);
            } else {
                widgetProps.add(key, JsonUtils.GSON.toJsonTree(value));
            }
            dirty.run();
        }

        /// 返回置顶实例 id。
        ///
        /// @return 不可变列表
        @Override
        @NotNull
        @Unmodifiable
        public List<String> pinnedInstanceIds() {
            return DashboardConfig.this.pinnedInstanceIds();
        }

        /// 覆盖置顶实例 id。
        ///
        /// @param ids 新列表
        @Override
        public void setPinnedInstanceIds(@NotNull List<String> ids) {
            DashboardConfig.this.setPinnedInstanceIds(ids);
            dirty.run();
        }
    }
}
