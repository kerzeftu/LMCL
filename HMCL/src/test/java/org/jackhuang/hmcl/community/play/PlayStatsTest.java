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

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// 游玩记录聚合统计的测试。
public final class PlayStatsTest {

    @Test
    public void aggregatesTotalLastSevenDaysAndActiveDays() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        Map<LocalDate, PlayRecordStore.DayRecord> data = Map.of(
                today, new PlayRecordStore.DayRecord(1800, 1, 0),
                today.minusDays(1), new PlayRecordStore.DayRecord(3600, 2, 0),
                today.minusDays(6), new PlayRecordStore.DayRecord(60, 1, 0),
                today.minusDays(7), new PlayRecordStore.DayRecord(999, 1, 0),
                LocalDate.of(2026, 9, 1), new PlayRecordStore.DayRecord(120, 1, 0),
                LocalDate.of(2026, 9, 2), new PlayRecordStore.DayRecord(0, 3, 0));

        PlayStats stats = PlayStats.of(data, today);

        assertEquals(6579, stats.totalSeconds());
        assertEquals(5460, stats.last7Seconds());
        assertEquals(5, stats.activeDays());
    }

    @Test
    public void emptyDataProducesZeros() {
        PlayStats stats = PlayStats.of(Map.of(), LocalDate.of(2026, 10, 21));

        assertEquals(0, stats.totalSeconds());
        assertEquals(0, stats.last7Seconds());
        assertEquals(0, stats.activeDays());
    }

    @Test
    public void lastSevenDaysIncludesTodayAndExcludesTheEighthDay() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        Map<LocalDate, PlayRecordStore.DayRecord> data = Map.of(
                today, new PlayRecordStore.DayRecord(10, 1, 0),
                today.minusDays(6), new PlayRecordStore.DayRecord(20, 1, 0),
                today.minusDays(7), new PlayRecordStore.DayRecord(40, 1, 0));

        PlayStats stats = PlayStats.of(data, today);

        assertEquals(30, stats.last7Seconds());
    }
}
