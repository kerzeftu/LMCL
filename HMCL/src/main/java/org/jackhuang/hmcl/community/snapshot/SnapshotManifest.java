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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// 快照清单。手工读写 JSON，避免上游枚举适配器改写 `reason` / `mode`。
///
/// 未知顶层字段保存在 [#extra()] 中并在写回时保留，便于以后向前兼容。
///
/// @param version schema 版本，当前为 `1`
/// @param snapshotId 快照 id
/// @param scopeKey 作用域 key
/// @param scope `version-root` 或 `shared`
/// @param runDirectory 采集时的运行目录（明文，绝对路径）
/// @param instanceIds 共享该运行目录的实例 id
/// @param createdAt 采集时间（epoch millis）
/// @param successfulLaunchAt 被采集的成功启动的判定时间（epoch millis）
/// @param reason 产生原因
/// @param mode 物化方式
/// @param thresholdSeconds 成功启动的存活阈值（秒）
/// @param files 文件清单
/// @param excluded 未采集的目录/文件说明
/// @param extra 未识别的顶层字段
@NotNullByDefault
public record SnapshotManifest(
        int version,
        @NotNull String snapshotId,
        @NotNull String scopeKey,
        @NotNull String scope,
        @NotNull String runDirectory,
        @NotNull @Unmodifiable List<String> instanceIds,
        long createdAt,
        long successfulLaunchAt,
        @NotNull SnapshotReason reason,
        @NotNull SnapshotMode mode,
        int thresholdSeconds,
        @NotNull @Unmodifiable List<SnapshotFileEntry> files,
        @NotNull @Unmodifiable List<String> excluded,
        @NotNull JsonObject extra) {

    /// 当前 schema 版本。
    public static final int SCHEMA = 1;

    /// 规范化并校验路径安全性。
    public SnapshotManifest {
        if (version <= 0) {
            throw new IllegalArgumentException("Invalid manifest version: " + version);
        }
        if (!isSafeId(snapshotId)) {
            throw new IllegalArgumentException("Invalid snapshot id: " + snapshotId);
        }
        if (!isSafeId(scopeKey)) {
            throw new IllegalArgumentException("Invalid scope key: " + scopeKey);
        }
        if (runDirectory.isBlank()) {
            throw new IllegalArgumentException("Run directory is empty");
        }
        instanceIds = List.copyOf(instanceIds);
        excluded = List.copyOf(excluded);
        for (SnapshotFileEntry file : files) {
            if (!isSafeRelativePath(file.path())) {
                throw new IllegalArgumentException("Unsafe snapshot path: " + file.path());
            }
            if (file.size() < 0) {
                throw new IllegalArgumentException("Invalid file size: " + file.path());
            }
            if (!file.sha256().matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Invalid sha256: " + file.path());
            }
        }
        files = List.copyOf(files);
        extra = extra.deepCopy();
    }

    /// 判断 id 是否只由小写字母、数字和连字符组成。
    ///
    /// @param id id
    /// @return 是否合法
    public static boolean isSafeId(@Nullable String id) {
        return id != null && id.matches("[a-z0-9][a-z0-9-]{0,95}");
    }

    /// 判断 manifest 中的相对路径是否安全：非绝对、无 `..`、使用 `/`。
    ///
    /// @param path 相对路径
    /// @return 是否安全
    public static boolean isSafeRelativePath(@Nullable String path) {
        if (path == null || path.isBlank() || path.indexOf('\\') >= 0) {
            return false;
        }
        try {
            Path normalized = Path.of(path);
            if (normalized.isAbsolute()) {
                return false;
            }
            for (Path part : normalized) {
                if ("..".equals(part.toString())) {
                    return false;
                }
            }
            return true;
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /// 序列化为 JSON。
    ///
    /// @return JSON 对象
    @NotNull
    public JsonObject toJson() {
        JsonObject object = extra.deepCopy();
        object.addProperty("version", version);
        object.addProperty("snapshotId", snapshotId);
        object.addProperty("scopeKey", scopeKey);
        object.addProperty("scope", scope);
        object.addProperty("runDirectory", runDirectory);
        JsonArray ids = new JsonArray();
        for (String id : instanceIds) {
            ids.add(id);
        }
        object.add("instanceIds", ids);
        object.addProperty("createdAt", createdAt);
        object.addProperty("successfulLaunchAt", successfulLaunchAt);
        object.addProperty("reason", reason.jsonName());
        object.addProperty("mode", mode.jsonName());
        object.addProperty("thresholdSeconds", thresholdSeconds);
        JsonArray array = new JsonArray();
        for (SnapshotFileEntry file : files) {
            JsonObject item = new JsonObject();
            item.addProperty("path", file.path());
            item.addProperty("size", file.size());
            item.addProperty("sha256", file.sha256());
            if (file.object() != null) {
                item.addProperty("object", file.object());
            }
            if (file.link() != null) {
                item.addProperty("link", file.link());
            }
            item.addProperty("mtime", file.mtime());
            array.add(item);
        }
        object.add("files", array);
        JsonArray excludedArray = new JsonArray();
        for (String value : excluded) {
            excludedArray.add(value);
        }
        object.add("excluded", excludedArray);
        return object;
    }

    /// 从 JSON 解析；缺少必需字段或路径不安全时抛 [IllegalArgumentException]。
    ///
    /// @param object JSON 对象
    /// @return 清单
    @NotNull
    public static SnapshotManifest fromJson(@NotNull JsonObject object) {
        int version = JsonUtils.getInt(object, "version", 0);
        String snapshotId = JsonUtils.getString(object, "snapshotId");
        String scopeKey = JsonUtils.getString(object, "scopeKey");
        String scope = JsonUtils.getString(object, "scope", "version-root");
        String runDirectory = JsonUtils.getString(object, "runDirectory");
        if (snapshotId == null || scopeKey == null || runDirectory == null) {
            throw new IllegalArgumentException("Missing required manifest fields");
        }

        List<String> instanceIds = readStrings(object.get("instanceIds"));
        List<SnapshotFileEntry> files = new ArrayList<>();
        if (object.get("files") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (!(element instanceof JsonObject item)) {
                    continue;
                }
                String path = JsonUtils.getString(item, "path");
                String sha = JsonUtils.getString(item, "sha256");
                if (path == null || sha == null) {
                    continue;
                }
                files.add(new SnapshotFileEntry(
                        path,
                        item.has("size") ? item.get("size").getAsLong() : 0L,
                        sha,
                        JsonUtils.getString(item, "object"),
                        JsonUtils.getString(item, "link"),
                        item.has("mtime") ? item.get("mtime").getAsLong() : 0L));
            }
        }

        Set<String> known = new LinkedHashSet<>(List.of(
                "version", "snapshotId", "scopeKey", "scope", "runDirectory", "instanceIds",
                "createdAt", "successfulLaunchAt", "reason", "mode", "thresholdSeconds",
                "files", "excluded"));
        JsonObject extra = object.deepCopy();
        for (String key : known) {
            extra.remove(key);
        }

        return new SnapshotManifest(
                version,
                snapshotId,
                scopeKey,
                scope,
                runDirectory,
                instanceIds,
                object.has("createdAt") ? object.get("createdAt").getAsLong() : 0L,
                object.has("successfulLaunchAt") ? object.get("successfulLaunchAt").getAsLong() : 0L,
                SnapshotReason.fromJson(JsonUtils.getString(object, "reason", "manual")),
                SnapshotMode.fromJson(JsonUtils.getString(object, "mode", "hardlink")),
                JsonUtils.getInt(object, "thresholdSeconds", 0),
                files,
                readStrings(object.get("excluded")),
                extra);
    }

    /// 读取字符串数组，忽略非字符串元素。
    ///
    /// @param element JSON 元素，可为 `null`
    /// @return 不可变列表
    @NotNull
    private static List<String> readStrings(@Nullable JsonElement element) {
        if (!(element instanceof JsonArray array)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonElement item : array) {
            if (item instanceof JsonPrimitive primitive && primitive.isString()) {
                result.add(primitive.getAsString());
            }
        }
        return List.copyOf(result);
    }
}
