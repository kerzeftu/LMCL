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
package org.jackhuang.hmcl.community.play;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunityPaths;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 游玩记录与未闭合会话的本地存储。
///
/// 数据文件 `<本地根>/community/play-records.json`，schema v1；只保存按本地日期聚合的
/// 秒数与次数，不保存世界名、服务器地址等隐私信息。写入采用临时文件 + 原子替换，
/// 损坏文件会改名保留后重建。所有读写都在本类实例上同步，调用方负责放到 io 线程。
@NotNullByDefault
public final class PlayRecordStore {

    /// 数据文件名。
    private static final String DATA_FILE = "play-records.json";
    /// 未闭合启动会话文件名。
    private static final String SESSION_FILE = "play-session.json";
    /// 当前 schema。
    static final int SCHEMA = 1;
    /// 保留天数。
    public static final int KEEP_DAYS = 400;
    /// 单次会话最多计入的秒数（12 小时）。
    public static final long MAX_SESSION_SECONDS = 12L * 60L * 60L;

    /// 单日聚合记录。
    ///
    /// @param playSeconds 计入的游玩秒数
    /// @param sessions 会话次数
    /// @param estimatedSeconds 其中由崩溃恢复估算的秒数
    public record DayRecord(long playSeconds, int sessions, long estimatedSeconds) {

        /// 规范化：负数归零，次数不为负。
        ///
        /// @return 规范化后的记录
        @NotNull
        public DayRecord normalized() {
            return new DayRecord(
                    Math.max(0, playSeconds),
                    Math.max(0, sessions),
                    Math.max(0, estimatedSeconds));
        }

        /// 累加另一次会话。
        ///
        /// @param seconds 本次秒数
        /// @param estimated 是否为估算
        /// @return 累加后的记录
        @NotNull
        public DayRecord plus(long seconds, boolean estimated) {
            long value = Math.max(0, Math.min(seconds, MAX_SESSION_SECONDS));
            return new DayRecord(
                    playSeconds + value,
                    sessions + 1,
                    estimatedSeconds + (estimated ? value : 0));
        }
    }

    /// 未闭合会话（用于启动器被强杀后的恢复）。
    ///
    /// @param startedAtMillis 进程创建时间（epoch millis）
    /// @param trusted 是否已存活过阈值
    public record Session(long startedAtMillis, boolean trusted) {
    }

    /// 全局实例。
    private static volatile @Nullable PlayRecordStore instance;

    private final Path directory;
    private final @Nullable CommunitySettings settings;

    /// 创建使用指定社区设置实例的存储。
    ///
    /// @param directory 数据目录（`community`）
    /// @param settings 设置实例，测试可注入
    public PlayRecordStore(@NotNull Path directory, @Nullable CommunitySettings settings) {
        this.directory = directory.toAbsolutePath().normalize();
        this.settings = settings;
    }

    /// 创建不绑定社区设置的存储（仅测试与只读工具使用，游玩记录开关视为启用）。
    ///
    /// @param directory 数据目录（`community`）
    PlayRecordStore(@NotNull Path directory) {
        this(directory, null);
    }

    /// 返回全局存储实例。
    ///
    /// @return 存储实例
    @NotNull
    public static PlayRecordStore get() {
        PlayRecordStore store = instance;
        if (store == null) {
            synchronized (PlayRecordStore.class) {
                store = instance;
                if (store == null) {
                    store = new PlayRecordStore(
                            CommunityPaths.localHome().resolve("community"),
                            CommunitySettings.get());
                    instance = store;
                }
            }
        }
        return store;
    }

    /// 数据文件。
    ///
    /// @return 路径
    @NotNull
    public Path dataFile() {
        return directory.resolve(DATA_FILE);
    }

    /// 会话文件。
    ///
    /// @return 路径
    @NotNull
    public Path sessionFile() {
        return directory.resolve(SESSION_FILE);
    }

    /// 游玩记录是否启用（默认启用）。
    ///
    /// @return 启用返回 `true`
    public boolean enabled() {
        if (settings == null) {
            return true;
        }
        try {
            return JsonUtils.getBoolean(settings.section("playRecords"), "enabled", true);
        } catch (Throwable e) {
            LOG.warning("Failed to read playRecords.enabled", e);
            return true;
        }
    }

    /// 修改游玩记录开关；保留该段内其它字段。
    ///
    /// @param enabled 是否启用
    public void setEnabled(boolean enabled) {
        if (settings == null) {
            return;
        }
        JsonObject section = settings.section("playRecords");
        section.addProperty("enabled", enabled);
        settings.putSection("playRecords", section);
    }

    /// 读取全部按日聚合记录；损坏文件会被改名后返回空表。
    ///
    /// @return 日期到记录的映射
    @NotNull
    public synchronized Map<LocalDate, DayRecord> load() {
        Path path = dataFile();
        if (!Files.isRegularFile(path)) {
            return new LinkedHashMap<>();
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonUtils.GSON.fromJson(text, JsonObject.class);
            if (root == null || !root.has("days")) {
                return new LinkedHashMap<>();
            }
            JsonObject days = root.getAsJsonObject("days");
            Map<LocalDate, DayRecord> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : days.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                LocalDate date;
                try {
                    date = LocalDate.parse(entry.getKey());
                } catch (RuntimeException e) {
                    continue;
                }
                JsonObject value = entry.getValue().getAsJsonObject();
                long seconds = getLong(value, "playSeconds", 0);
                int sessions = (int) Math.max(0, Math.min(Integer.MAX_VALUE, getLong(value, "sessions", 0)));
                long estimated = getLong(value, "estimatedSeconds", 0);
                result.put(date, new DayRecord(seconds, sessions, estimated).normalized());
            }
            return result;
        } catch (Throwable e) {
            LOG.warning("Failed to read play records, rebuilding: " + path, e);
            quarantine(path);
            return new LinkedHashMap<>();
        }
    }

    /// 累加一次会话并落盘；超过 [PlayRecordStore#KEEP_DAYS] 的日期会被裁剪。
    ///
    /// @param day 退出日期（本地时区）
    /// @param seconds 会话秒数
    /// @param estimated 是否为估算
    public synchronized void addSession(@NotNull LocalDate day, long seconds, boolean estimated) {
        Map<LocalDate, DayRecord> days = load();
        DayRecord record = days.getOrDefault(day, new DayRecord(0, 0, 0));
        days.put(day, record.plus(seconds, estimated));
        prune(days, LocalDate.now());
        saveDays(days);
    }

    /// 清空游玩记录与未闭合会话。
    public synchronized void clear() {
        saveDays(new LinkedHashMap<>());
        try {
            Files.deleteIfExists(sessionFile());
        } catch (IOException e) {
            LOG.warning("Failed to delete play session", e);
        }
    }

    /// 读取未闭合会话。
    ///
    /// @return 实例 id 到会话的映射
    @NotNull
    public synchronized Map<String, Session> loadSessions() {
        Path path = sessionFile();
        if (!Files.isRegularFile(path)) {
            return new LinkedHashMap<>();
        }
        try {
            JsonObject root = JsonUtils.GSON.fromJson(
                    Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            if (root == null || !root.has("sessions")) {
                return new LinkedHashMap<>();
            }
            JsonObject sessions = root.getAsJsonObject("sessions");
            Map<String, Session> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : sessions.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject value = entry.getValue().getAsJsonObject();
                long startedAt = getLong(value, "startedAt", 0);
                boolean trusted = JsonUtils.getBoolean(value, "trusted", false);
                if (startedAt > 0) {
                    result.put(entry.getKey(), new Session(startedAt, trusted));
                }
            }
            return result;
        } catch (Throwable e) {
            LOG.warning("Failed to read play session, rebuilding: " + path, e);
            quarantine(path);
            return new LinkedHashMap<>();
        }
    }

    /// 写入未闭合会话；空表时删除文件。
    ///
    /// @param sessions 实例 id 到会话的映射
    public synchronized void saveSessions(@NotNull Map<String, Session> sessions) {
        if (sessions.isEmpty()) {
            try {
                Files.deleteIfExists(sessionFile());
            } catch (IOException e) {
                LOG.warning("Failed to delete play session", e);
            }
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", SCHEMA);
        JsonObject values = new JsonObject();
        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            JsonObject value = new JsonObject();
            value.addProperty("startedAt", entry.getValue().startedAtMillis());
            value.addProperty("trusted", entry.getValue().trusted());
            values.add(entry.getKey(), value);
        }
        root.add("sessions", values);
        writeJson(sessionFile(), root);
    }

    /// 删除指定实例的未闭合会话。
    ///
    /// @param versionId 实例 id
    public synchronized void removeSession(@NotNull String versionId) {
        Map<String, Session> sessions = loadSessions();
        if (sessions.remove(versionId) != null) {
            saveSessions(sessions);
        }
    }

    private void saveDays(@NotNull Map<LocalDate, DayRecord> days) {
        JsonObject root = new JsonObject();
        root.addProperty("version", SCHEMA);
        JsonObject values = new JsonObject();
        for (Map.Entry<LocalDate, DayRecord> entry : days.entrySet()) {
            DayRecord record = entry.getValue().normalized();
            JsonObject value = new JsonObject();
            value.addProperty("playSeconds", record.playSeconds());
            value.addProperty("sessions", record.sessions());
            if (record.estimatedSeconds() > 0) {
                value.addProperty("estimatedSeconds", record.estimatedSeconds());
            }
            values.add(entry.getKey().toString(), value);
        }
        root.add("days", values);
        writeJson(dataFile(), root);
    }

    private static void prune(@NotNull Map<LocalDate, DayRecord> days, @NotNull LocalDate today) {
        LocalDate oldest = today.minusDays(KEEP_DAYS);
        days.entrySet().removeIf(entry -> entry.getKey().isBefore(oldest));
    }

    private void writeJson(@NotNull Path path, @NotNull JsonObject root) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = Files.createTempFile(path.getParent(), "play-", ".tmp");
            Files.writeString(temp, JsonUtils.GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.warning("Failed to write play records: " + path, e);
        }
    }

    private static void quarantine(@NotNull Path path) {
        try {
            Path target = path.resolveSibling(path.getFileName() + ".corrupt-" + System.currentTimeMillis());
            Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.warning("Failed to quarantine corrupt play record: " + path, e);
        }
    }

    private static long getLong(@NotNull JsonObject object, @NotNull String key, long defaultValue) {
        if (!object.has(key)) {
            return defaultValue;
        }
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }
}
