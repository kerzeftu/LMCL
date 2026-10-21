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

import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 游玩记录存储的单元测试。
public final class PlayRecordStoreTest {

    @Test
    public void accumulatesSessionsAndReloads(@TempDir Path temp) {
        PlayRecordStore store = new PlayRecordStore(temp.resolve("community"));
        LocalDate day = LocalDate.now();
        store.addSession(day, 3600, false);
        store.addSession(day, 60, true);

        Map<LocalDate, PlayRecordStore.DayRecord> days = store.load();
        PlayRecordStore.DayRecord record = days.get(day);
        assertEquals(3660, record.playSeconds());
        assertEquals(2, record.sessions());
        assertEquals(60, record.estimatedSeconds());

        PlayRecordStore reloaded = new PlayRecordStore(temp.resolve("community"));
        assertEquals(3660, reloaded.load().get(day).playSeconds());
    }

    @Test
    public void capsSingleSessionAtTwelveHours(@TempDir Path temp) {
        PlayRecordStore store = new PlayRecordStore(temp.resolve("community"));
        LocalDate day = LocalDate.now();
        store.addSession(day, 24L * 60 * 60, false);
        assertEquals(PlayRecordStore.MAX_SESSION_SECONDS, store.load().get(day).playSeconds());
    }

    @Test
    public void keepsRecordsAcrossYearBoundary(@TempDir Path temp) {
        PlayRecordStore store = new PlayRecordStore(temp.resolve("community"));
        LocalDate now = LocalDate.now();
        LocalDate lastYear = now.minusYears(1);

        store.addSession(lastYear, 120, false);
        store.addSession(now, 60, false);

        Map<LocalDate, PlayRecordStore.DayRecord> days = store.load();
        assertEquals(120, days.get(lastYear).playSeconds());
        assertEquals(60, days.get(now).playSeconds());
    }

    @Test
    public void prunesRecordsOlderThanKeepDays(@TempDir Path temp) {
        PlayRecordStore store = new PlayRecordStore(temp.resolve("community"));
        LocalDate today = LocalDate.now();
        store.addSession(today.minusDays(PlayRecordStore.KEEP_DAYS + 1L), 60, false);
        store.addSession(today.minusDays(PlayRecordStore.KEEP_DAYS), 60, false);
        store.addSession(today, 60, false);

        Map<LocalDate, PlayRecordStore.DayRecord> days = store.load();
        assertTrue(days.containsKey(today));
        assertTrue(days.containsKey(today.minusDays(PlayRecordStore.KEEP_DAYS)));
        assertFalse(days.containsKey(today.minusDays(PlayRecordStore.KEEP_DAYS + 1L)));
    }

    @Test
    public void quarantinesCorruptDataFile(@TempDir Path temp) throws Exception {
        Path dir = temp.resolve("community");
        PlayRecordStore store = new PlayRecordStore(dir);
        Files.createDirectories(dir);
        Files.writeString(store.dataFile(), "{not json");

        assertTrue(store.load().isEmpty());
        try (var stream = Files.list(dir)) {
            assertTrue(stream.anyMatch(path -> path.getFileName().toString().contains(".corrupt-")));
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        LocalDate day = LocalDate.now();
        store.addSession(day, 60, false);
        assertEquals(60, store.load().get(day).playSeconds());
    }

    @Test
    public void storesAndRemovesOpenSessions(@TempDir Path temp) {
        PlayRecordStore store = new PlayRecordStore(temp.resolve("community"));
        store.saveSessions(Map.of("a", new PlayRecordStore.Session(1000, false),
                "b", new PlayRecordStore.Session(2000, true)));
        assertEquals(2, store.loadSessions().size());

        store.removeSession("a");
        Map<String, PlayRecordStore.Session> sessions = store.loadSessions();
        assertEquals(1, sessions.size());
        assertTrue(sessions.get("b").trusted());

        store.saveSessions(Map.of());
        assertFalse(Files.exists(store.sessionFile()));
    }

    @Test
    public void enabledFlagRoundTripsThroughCommunitySettings(@TempDir Path temp) {
        Path settingsFile = temp.resolve("config/community-settings.json");
        PlayRecordStore store = new PlayRecordStore(temp.resolve("community"), new CommunitySettings(settingsFile));
        assertTrue(store.enabled());
        store.setEnabled(false);
        assertFalse(store.enabled());
        assertFalse(new PlayRecordStore(temp.resolve("community"), new CommunitySettings(settingsFile)).enabled());
        store.setEnabled(true);
        assertTrue(store.enabled());
    }
}
