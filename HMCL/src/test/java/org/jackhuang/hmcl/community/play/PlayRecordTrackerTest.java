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

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 游玩打点的边界与恢复测试。
public final class PlayRecordTrackerTest {

    @Test
    public void thresholdBoundaries() {
        assertFalse(PlayRecordTracker.shouldRecord(119, 120));
        assertTrue(PlayRecordTracker.shouldRecord(120, 120));
        assertTrue(PlayRecordTracker.shouldRecord(121, 120));
    }

    @Test
    public void durationNeverNegative() {
        assertEquals(0, PlayRecordTracker.durationSeconds(1000, 999));
        assertEquals(5, PlayRecordTracker.durationSeconds(1000, 6000));
    }

    @Test
    public void recoversInterruptedSessionAsEstimated(@TempDir Path temp) {
        PlayRecordStore store = new PlayRecordStore(temp.resolve("community"));
        long now = 1_700_000_000_000L;
        store.saveSessions(Map.of("a", new PlayRecordStore.Session(now - 121_000L, false)));

        PlayRecordTracker.recover(store, 120, now);

        PlayRecordStore.DayRecord record = store.load().get(LocalDate.now());
        assertEquals(121, record.playSeconds());
        assertEquals(1, record.sessions());
        assertEquals(121, record.estimatedSeconds());
        assertTrue(store.loadSessions().isEmpty());
    }

    @Test
    public void skipsShortInterruptedSession(@TempDir Path temp) {
        PlayRecordStore store = new PlayRecordStore(temp.resolve("community"));
        long now = 1_700_000_000_000L;
        store.saveSessions(Map.of("a", new PlayRecordStore.Session(now - 30_000L, true)));

        PlayRecordTracker.recover(store, 120, now);

        assertTrue(store.load().isEmpty());
        assertTrue(store.loadSessions().isEmpty());
    }

    @Test
    public void recoveryRespectsDisabledFlag(@TempDir Path temp) {
        PlayRecordStore store = new PlayRecordStore(temp.resolve("community"),
                new CommunitySettings(temp.resolve("config/community-settings.json")));
        store.setEnabled(false);
        long now = 1_700_000_000_000L;
        store.saveSessions(Map.of("a", new PlayRecordStore.Session(now - 600_000L, true)));

        PlayRecordTracker.recover(store, 120, now);

        assertTrue(store.load().isEmpty());
        assertTrue(store.loadSessions().isEmpty());
    }
}
