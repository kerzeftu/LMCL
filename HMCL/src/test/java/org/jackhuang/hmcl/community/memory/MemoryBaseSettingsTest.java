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

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// `memoryBase` 段的读写、容错与边界测试。
@NotNullByDefault
public final class MemoryBaseSettingsTest {

    private static final long MIB = 1024L * 1024L;

    private CommunitySettings store(Path dir) {
        return new CommunitySettings(dir.resolve("community-settings.json"));
    }

    /// 文件不存在时使用默认值。
    @Test
    public void loadsDefaults(@TempDir Path dir) {
        MemoryBaseSettings settings = MemoryBaseSettings.load(store(dir));
        assertTrue(settings.enabled());
        assertEquals(MemoryBaseSettings.DEFAULT_INTERVAL_MINUTES, settings.intervalMinutes());
        assertEquals(MemoryBaseSettings.DEFAULT_MIN_RECLAIM_MIB, settings.minReclaimMiB());
        assertFalse(settings.autoDisabled());
        assertNull(settings.disabledReason());
        assertNull(settings.lastReclaimAt());
        assertEquals(0, settings.totalReclaimedMiB());
    }

    /// 保存后重新加载字段一致。
    @Test
    public void roundTripsFields(@TempDir Path dir) {
        CommunitySettings store = store(dir);
        MemoryBaseSettings settings = MemoryBaseSettings.load(store);
        settings.setEnabled(false);
        settings.setIntervalMinutes(15);
        settings.setMinReclaimMiB(256);
        settings.setAutoDisabled(true, "pause_too_long");
        settings.recordReclaim(Instant.parse("2026-10-21T05:00:00Z"), 300 * MIB);
        settings.save();

        MemoryBaseSettings reloaded = MemoryBaseSettings.load(store);
        assertFalse(reloaded.enabled());
        assertEquals(15, reloaded.intervalMinutes());
        assertEquals(256, reloaded.minReclaimMiB());
        assertTrue(reloaded.autoDisabled());
        assertEquals("pause_too_long", reloaded.disabledReason());
        assertEquals(Instant.parse("2026-10-21T05:00:00Z"), reloaded.lastReclaimAt());
        assertEquals(300, reloaded.totalReclaimedMiB());
    }

    /// 重新开启会清除自动停用状态。
    @Test
    public void reenablingClearsAutoDisabled(@TempDir Path dir) {
        MemoryBaseSettings settings = MemoryBaseSettings.load(store(dir));
        settings.setAutoDisabled(true, "pause_too_long");
        settings.setEnabled(true);
        settings.setAutoDisabled(false, null);
        settings.save();

        MemoryBaseSettings reloaded = MemoryBaseSettings.load(store(dir));
        assertTrue(reloaded.enabled());
        assertFalse(reloaded.autoDisabled());
        assertNull(reloaded.disabledReason());
    }

    /// 非法数值被夹到合法区间。
    @Test
    public void clampsNumericValues(@TempDir Path dir) {
        CommunitySettings store = store(dir);
        JsonObject section = new JsonObject();
        section.addProperty("intervalMinutes", 0);
        section.addProperty("minReclaimMiB", 99999);
        store.putSection("memoryBase", section);

        MemoryBaseSettings settings = MemoryBaseSettings.load(store);
        assertEquals(1, settings.intervalMinutes());
        assertEquals(4096, settings.minReclaimMiB());

        settings.setIntervalMinutes(999);
        settings.setMinReclaimMiB(0);
        assertEquals(60, settings.intervalMinutes());
        assertEquals(32, settings.minReclaimMiB());
    }

    /// 类型错误时回退默认值，不抛异常。
    @Test
    public void toleratesWrongTypes(@TempDir Path dir) {
        CommunitySettings store = store(dir);
        JsonObject section = new JsonObject();
        section.addProperty("enabled", "not-a-boolean");
        section.addProperty("intervalMinutes", "abc");
        section.addProperty("lastReclaimAt", "not-a-time");
        section.addProperty("totalReclaimedMiB", -5);
        store.putSection("memoryBase", section);

        MemoryBaseSettings settings = MemoryBaseSettings.load(store);
        assertTrue(settings.enabled());
        assertEquals(MemoryBaseSettings.DEFAULT_INTERVAL_MINUTES, settings.intervalMinutes());
        assertNull(settings.lastReclaimAt());
        assertEquals(0, settings.totalReclaimedMiB());
    }

    /// 保留本类不认识的字段，避免覆盖后续步骤追加的键。
    @Test
    public void preservesUnknownFields(@TempDir Path dir) {
        CommunitySettings store = store(dir);
        JsonObject section = new JsonObject();
        section.addProperty("futureField", "keep-me");
        store.putSection("memoryBase", section);

        MemoryBaseSettings settings = MemoryBaseSettings.load(store);
        settings.setIntervalMinutes(12);
        settings.save();

        JsonObject saved = store.section("memoryBase");
        assertEquals("keep-me", saved.get("futureField").getAsString());
        assertEquals(12, saved.get("intervalMinutes").getAsInt());
    }

    /// 累计值按整 MiB 向下累计，小于 1 MiB 的回收不增加计数。
    @Test
    public void accumulatesWholeMib(@TempDir Path dir) {
        MemoryBaseSettings settings = MemoryBaseSettings.load(store(dir));
        settings.recordReclaim(Instant.now(), MIB - 1);
        assertEquals(0, settings.totalReclaimedMiB());
        settings.recordReclaim(Instant.now(), 2 * MIB + 512 * 1024L);
        assertEquals(2, settings.totalReclaimedMiB());
    }

    /// 带偏移的时间字符串也能解析。
    @Test
    public void parsesOffsetDateTime(@TempDir Path dir) {
        CommunitySettings store = store(dir);
        JsonObject section = new JsonObject();
        section.addProperty("lastReclaimAt", "2026-10-21T13:00:00+08:00");
        store.putSection("memoryBase", section);

        MemoryBaseSettings settings = MemoryBaseSettings.load(store);
        assertNotNull(settings.lastReclaimAt());
        assertEquals(Instant.parse("2026-10-21T05:00:00Z"), settings.lastReclaimAt());
    }
}
