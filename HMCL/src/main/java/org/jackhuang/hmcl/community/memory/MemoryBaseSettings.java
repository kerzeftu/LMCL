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
package org.jackhuang.hmcl.community.memory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/// `community-settings.json` 中 `memoryBase` 段的读写与默认值。
///
/// 该段由本类独占写入；自动回收的间隔、停用状态、最近一次回收时间等持久化于此，
/// 重启后继续生效。解析是容错的：字段缺失或类型非法时回退默认值，未知字段原样保留。
@NotNullByDefault
public final class MemoryBaseSettings {

    static final String SECTION = "memoryBase";

    /// 默认检查间隔（分钟）。
    public static final int DEFAULT_INTERVAL_MINUTES = 10;
    /// 默认单次回收的最小可归还量（MiB）。
    public static final int DEFAULT_MIN_RECLAIM_MIB = 128;

    static final int MIN_INTERVAL_MINUTES = 1;
    static final int MAX_INTERVAL_MINUTES = 60;
    static final int MIN_RECLAIM_MIB = 32;
    static final int MAX_RECLAIM_MIB = 4096;

    private final CommunitySettings store;

    private boolean enabled = true;
    private int intervalMinutes = DEFAULT_INTERVAL_MINUTES;
    private int minReclaimMiB = DEFAULT_MIN_RECLAIM_MIB;
    private boolean autoDisabled = false;
    private @Nullable String disabledReason = null;
    private @Nullable Instant lastReclaimAt = null;
    private long totalReclaimedMiB = 0;

    MemoryBaseSettings(CommunitySettings store) {
        this.store = store;
    }

    /// 从全局社区设置加载；文件不存在或损坏时使用默认值。
    public static MemoryBaseSettings load() {
        return load(CommunitySettings.get());
    }

    static MemoryBaseSettings load(CommunitySettings store) {
        MemoryBaseSettings settings = new MemoryBaseSettings(store);
        JsonObject section = store.section(SECTION);
        settings.enabled = getBoolean(section, "enabled", true);
        settings.intervalMinutes = clamp(
                getInt(section, "intervalMinutes", DEFAULT_INTERVAL_MINUTES),
                MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES);
        settings.minReclaimMiB = clamp(
                getInt(section, "minReclaimMiB", DEFAULT_MIN_RECLAIM_MIB),
                MIN_RECLAIM_MIB, MAX_RECLAIM_MIB);
        settings.autoDisabled = getBoolean(section, "autoDisabled", false);
        settings.disabledReason = getString(section, "disabledReason");
        String last = getString(section, "lastReclaimAt");
        settings.lastReclaimAt = last == null ? null : parseInstant(last);
        settings.totalReclaimedMiB = Math.max(0, getLong(section, "totalReclaimedMiB", 0));
        return settings;
    }

    /// 写回设置；保留 `memoryBase` 段内本类不认识的字段（后续步骤可能追加）。
    public synchronized void save() {
        JsonObject section = store.section(SECTION);
        section.addProperty("enabled", enabled);
        section.addProperty("intervalMinutes", intervalMinutes);
        section.addProperty("minReclaimMiB", minReclaimMiB);
        section.addProperty("autoDisabled", autoDisabled);
        if (disabledReason == null) {
            section.remove("disabledReason");
        } else {
            section.addProperty("disabledReason", disabledReason);
        }
        if (lastReclaimAt == null) {
            section.remove("lastReclaimAt");
        } else {
            section.addProperty("lastReclaimAt", lastReclaimAt.toString());
        }
        section.addProperty("totalReclaimedMiB", totalReclaimedMiB);
        store.putSection(SECTION, section);
    }

    /// 记录一次回收：更新最近时间与累计减少的已提交堆。
    public void recordReclaim(Instant at, long freedBytes) {
        this.lastReclaimAt = at;
        this.totalReclaimedMiB += Math.max(0, freedBytes) / 1024L / 1024L;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int intervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(int intervalMinutes) {
        this.intervalMinutes = clamp(intervalMinutes, MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES);
    }

    public int minReclaimMiB() {
        return minReclaimMiB;
    }

    public void setMinReclaimMiB(int minReclaimMiB) {
        this.minReclaimMiB = clamp(minReclaimMiB, MIN_RECLAIM_MIB, MAX_RECLAIM_MIB);
    }

    public boolean autoDisabled() {
        return autoDisabled;
    }

    public void setAutoDisabled(boolean autoDisabled, @Nullable String reason) {
        this.autoDisabled = autoDisabled;
        this.disabledReason = autoDisabled ? reason : null;
    }

    public @Nullable String disabledReason() {
        return disabledReason;
    }

    public @Nullable Instant lastReclaimAt() {
        return lastReclaimAt;
    }

    public long totalReclaimedMiB() {
        return totalReclaimedMiB;
    }

    // region tolerant JSON helpers

    private static boolean getBoolean(JsonObject object, String key, boolean defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()) {
            return defaultValue;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    private static int getInt(JsonObject object, String key, int defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return defaultValue;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    private static long getLong(JsonObject object, String key, long defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return defaultValue;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    private static @Nullable String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }

    private static @Nullable Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // endregion
}
