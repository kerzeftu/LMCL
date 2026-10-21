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
package org.jackhuang.hmcl.community.crash;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 崩溃历史索引：`<本地根>/community/crash-history.json`。
///
/// 历史是数据、设置是配置，**不合并进 `community-settings.json`**。
/// 所有方法都不抛异常：损坏文件备份后重建，IO 失败只影响 UI。
@NotNullByDefault
public final class CrashHistory {

    /// 索引 schema。
    public static final int SCHEMA = 1;

    /// 最多保留的索引条目数。
    public static final int MAX_ENTRIES = 20;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private static final AtomicInteger UNSEEN = new AtomicInteger();

    /// 测试专用根目录覆盖（`null` = 使用 [Metadata#HMCL_LOCAL_HOME]）。
    @Nullable
    private static volatile Path rootOverride;

    private CrashHistory() {
    }

    /// 测试专用：把索引/归档根重定向到临时目录。
    ///
    /// @param root 临时根目录，`null` 恢复默认
    static void setRootForTesting(@Nullable Path root) {
        rootOverride = root;
        UNSEEN.set(0);
    }

    @NotNull
    private static Path communityRoot() {
        Path override = rootOverride;
        return override != null ? override : Metadata.HMCL_LOCAL_HOME.resolve("community");
    }

    /// 崩溃归档根目录：`<本地根>/community/crash-reports/`。
    ///
    /// @return 归档根
    @NotNull
    public static Path crashRoot() {
        return communityRoot().resolve("crash-reports");
    }

    /// 索引文件路径。
    ///
    /// @return 索引文件
    @NotNull
    public static Path indexPath() {
        return communityRoot().resolve("crash-history.json");
    }

    /// 读取索引（损坏时备份并返回空列表）。
    ///
    /// @return 从新到旧的条目
    @NotNull
    public static synchronized List<CrashEntry> load() {
        Path file = indexPath();
        if (!Files.isRegularFile(file)) {
            return List.of();
        }

        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonUtils.GSON.fromJson(text, JsonObject.class);
            if (root == null) {
                return List.of();
            }
            JsonArray entries = root.getAsJsonArray("entries");
            if (entries == null) {
                return List.of();
            }

            List<CrashEntry> result = new ArrayList<>(entries.size());
            for (JsonElement element : entries) {
                try {
                    if (element.isJsonObject()) {
                        result.add(parseEntry(element.getAsJsonObject()));
                    }
                } catch (RuntimeException e) {
                    LOG.warning("Skip broken crash history entry", e);
                }
            }
            return List.copyOf(result);
        } catch (Throwable e) {
            LOG.warning("Failed to read crash history, backup and rebuild", e);
            backupBroken(file);
            return List.of();
        }
    }

    /// 追加一条历史并原子写回；同时把"未读数"+1。
    ///
    /// @param archiveDir 归档目录
    /// @param meta 归档摘要
    public static synchronized void append(@NotNull Path archiveDir, @NotNull CrashMeta meta) {
        try {
            String dirName = archiveDir.getFileName().toString();
            List<CrashEntry> entries = new ArrayList<>(load());
            entries.removeIf(entry -> entry.archiveId().equals(dirName));
            entries.add(0, new CrashEntry(
                    dirName,
                    meta.at(),
                    meta.versionId(),
                    meta.gameVersion(),
                    meta.phase(),
                    meta.rules().isEmpty() ? null : meta.rules().get(0),
                    meta.keywords(),
                    meta.snapshotId(),
                    meta.exported(),
                    dirName));
            if (entries.size() > MAX_ENTRIES) {
                entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
            }
            write(entries);
            UNSEEN.incrementAndGet();
        } catch (Throwable e) {
            LOG.warning("Failed to append crash history", e);
        }
    }

    /// 删除一个归档目录及其历史条目。
    ///
    /// @param archiveId 归档 id
    /// @return 是否删除成功
    public static synchronized boolean delete(@NotNull String archiveId) {
        boolean deleted = false;
        try {
            Path dir = crashRoot().resolve(archiveId).normalize();
            if (dir.startsWith(crashRoot()) && Files.isDirectory(dir)) {
                deleteRecursively(dir);
                deleted = !Files.exists(dir);
            }
        } catch (Throwable e) {
            LOG.warning("Failed to delete crash archive " + archiveId, e);
        }

        try {
            List<CrashEntry> entries = new ArrayList<>(load());
            if (entries.removeIf(entry -> entry.archiveId().equals(archiveId))) {
                write(entries);
            }
        } catch (Throwable e) {
            LOG.warning("Failed to delete crash history entry " + archiveId, e);
        }
        return deleted;
    }

    /// 本次会话内新增的未读条数。
    ///
    /// @return 未读数
    public static int unseenCount() {
        return UNSEEN.get();
    }

    /// 把未读数清零。
    public static void markAllSeen() {
        UNSEEN.set(0);
    }

    /// 按 id 查条目。
    ///
    /// @param archiveId 归档 id
    /// @return 条目或 `null`
    @Nullable
    public static CrashEntry find(@NotNull String archiveId) {
        for (CrashEntry entry : load()) {
            if (entry.archiveId().equals(archiveId)) {
                return entry;
            }
        }
        return null;
    }

    private static CrashEntry parseEntry(JsonObject json) {
        return new CrashEntry(
                getString(json, "archiveId", ""),
                parseInstant(getString(json, "createdAt", "")),
                getString(json, "versionId", ""),
                getString(json, "gameVersion", ""),
                LaunchPhase.parse(getString(json, "phase", "STARTUP")),
                json.has("reasonRule") && !json.get("reasonRule").isJsonNull()
                        ? json.get("reasonRule").getAsString() : null,
                getStringList(json, "keywords"),
                json.has("snapshotId") && !json.get("snapshotId").isJsonNull()
                        ? json.get("snapshotId").getAsString() : null,
                json.has("exported") && !json.get("exported").isJsonNull() && json.get("exported").getAsBoolean(),
                getString(json, "dirName", getString(json, "archiveId", "")));
    }

    private static void write(List<CrashEntry> entries) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        JsonArray array = new JsonArray();
        for (CrashEntry entry : entries) {
            JsonObject json = new JsonObject();
            json.addProperty("archiveId", entry.archiveId());
            json.addProperty("createdAt", TIME_FORMATTER.format(entry.at().atZone(ZoneId.systemDefault())));
            json.addProperty("versionId", entry.versionId());
            json.addProperty("gameVersion", entry.gameVersion());
            json.addProperty("phase", entry.phase().name());
            if (entry.reasonRule() != null) {
                json.addProperty("reasonRule", entry.reasonRule());
            }
            JsonArray keywords = new JsonArray();
            entry.keywords().forEach(keywords::add);
            json.add("keywords", keywords);
            if (entry.snapshotId() != null) {
                json.addProperty("snapshotId", entry.snapshotId());
            }
            json.addProperty("exported", entry.exported());
            json.addProperty("dirName", entry.dirName());
            array.add(json);
        }
        root.add("entries", array);

        Path file = indexPath();
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, JsonUtils.GSON.toJson(root), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void backupBroken(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            String suffix = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .format(Instant.now().atZone(ZoneId.systemDefault()));
            Files.move(file, file.resolveSibling(file.getFileName() + ".bak-" + suffix),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable e) {
            LOG.warning("Failed to backup broken crash history", e);
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            try {
                return java.time.OffsetDateTime.parse(value).toInstant();
            } catch (RuntimeException ignored) {
                return Instant.EPOCH;
            }
        }
    }

    private static String getString(JsonObject json, String key, String defaultValue) {
        JsonElement element = json.get(key);
        return element != null && !element.isJsonNull() ? element.getAsString() : defaultValue;
    }

    private static List<String> getStringList(JsonObject json, String key) {
        JsonArray array = json.getAsJsonArray(key);
        if (array == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element != null && !element.isJsonNull()) {
                result.add(element.getAsString());
            }
        }
        return List.copyOf(result);
    }

    private static void deleteRecursively(Path dir) throws IOException {
        List<Path> paths = new ArrayList<>();
        try (var stream = Files.walk(dir)) {
            stream.forEach(paths::add);
        }
        paths.sort(java.util.Comparator.reverseOrder());
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }
}
