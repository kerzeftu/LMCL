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
package org.jackhuang.hmcl.community.snapshot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 快照采集的 SHA-256 缓存：路径 + 大小 + mtime 命中时跳过重新计算。
///
/// 缓存文件位于快照根目录的 `hash-cache.json`，只在采集成功结束时落盘。
/// 记录上限按 LRU 截断，损坏/不可写时退化为“每次都算”，不影响采集。
@NotNullByDefault
final class SnapshotHashCache {

    private static final String FILE_NAME = "hash-cache.json";
    private static final int SCHEMA = 1;

    private final Path file;
    private final int maxEntries;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    private SnapshotHashCache(Path file, int maxEntries) {
        this.file = file;
        this.maxEntries = Math.max(0, maxEntries);
    }

    /// 从快照根目录加载缓存。
    ///
    /// @param root 快照根目录
    /// @param maxEntries 最大条目数
    /// @return 缓存
    @NotNull
    static SnapshotHashCache load(@NotNull Path root, int maxEntries) {
        SnapshotHashCache cache = new SnapshotHashCache(root.resolve(FILE_NAME), maxEntries);
        if (!Files.isRegularFile(cache.file)) {
            return cache;
        }
        try {
            JsonObject object = JsonParser.parseString(
                    Files.readString(cache.file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject stored = object.getAsJsonObject("entries");
            if (stored == null) {
                return cache;
            }
            for (Map.Entry<String, JsonElement> item : stored.entrySet()) {
                if (!item.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject value = item.getValue().getAsJsonObject();
                if (!value.has("size") || !value.has("mtime") || !value.has("sha256")) {
                    continue;
                }
                cache.entries.put(item.getKey(), new Entry(
                        value.get("size").getAsLong(),
                        value.get("mtime").getAsLong(),
                        value.get("sha256").getAsString()));
            }
            cache.trim();
        } catch (IOException | RuntimeException e) {
            LOG.warning("Failed to read snapshot hash cache " + cache.file, e);
            cache.entries.clear();
        }
        return cache;
    }

    /// 查询缓存；大小或修改时间变化时视为未命中。
    ///
    /// @param path 文件
    /// @param size 大小
    /// @param mtime 修改时间
    /// @return SHA-256，未命中为 `null`
    @Nullable
    String get(@NotNull Path path, long size, long mtime) {
        if (maxEntries == 0) {
            return null;
        }
        Entry entry = entries.get(key(path));
        if (entry == null || entry.size() != size || entry.mtime() != mtime) {
            return null;
        }
        return entry.sha256();
    }

    /// 写入缓存并截断到上限。
    ///
    /// @param path 文件
    /// @param size 大小
    /// @param mtime 修改时间
    /// @param sha256 摘要
    void put(@NotNull Path path, long size, long mtime, @NotNull String sha256) {
        if (maxEntries == 0) {
            return;
        }
        entries.put(key(path), new Entry(size, mtime, sha256));
        trim();
    }

    /// 原子落盘；失败只记日志。
    void save() {
        if (maxEntries == 0) {
            return;
        }
        JsonObject stored = new JsonObject();
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            JsonObject value = new JsonObject();
            value.addProperty("size", item.getValue().size());
            value.addProperty("mtime", item.getValue().mtime());
            value.addProperty("sha256", item.getValue().sha256());
            stored.add(item.getKey(), value);
        }
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.add("entries", stored);
        try {
            Files.createDirectories(file.getParent());
            SnapshotStore.writeAtomic(file, root.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            LOG.warning("Failed to save snapshot hash cache " + file, e);
        }
    }

    /// 超出上限时删除最久未使用的条目。
    private void trim() {
        while (entries.size() > maxEntries) {
            String eldest = entries.keySet().iterator().next();
            entries.remove(eldest);
        }
    }

    /// 缓存键：规范化绝对路径。
    private static String key(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    /// 一条缓存记录。
    ///
    /// @param size 大小
    /// @param mtime 修改时间
    /// @param sha256 摘要
    private record Entry(long size, long mtime, @NotNull String sha256) {
    }
}
