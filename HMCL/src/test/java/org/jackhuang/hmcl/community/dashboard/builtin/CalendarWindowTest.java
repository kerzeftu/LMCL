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
package org.jackhuang.hmcl.community.dashboard.builtin;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 固定 5×7 日历窗口的测试。
public final class CalendarWindowTest {

    @Test
    public void startsFourMondaysBeforeThisWeek() {
        LocalDate today = LocalDate.of(2026, 10, 21); // Wednesday
        CalendarWindow window = CalendarWindow.containing(today);

        assertEquals(LocalDate.of(2026, 9, 21), window.start());
        assertEquals(DayOfWeek.MONDAY, window.start().getDayOfWeek());
    }

    @Test
    public void todayIsInTheLastRowAndCorrectColumn() {
        LocalDate today = LocalDate.of(2026, 10, 21); // Wednesday
        CalendarWindow window = CalendarWindow.containing(today);

        assertEquals(today, window.dateAt(4, 2));
        assertEquals(DayOfWeek.WEDNESDAY, window.dateAt(4, 2).getDayOfWeek());
        assertEquals(today.plusDays(4), window.dateAt(4, 6));
    }

    @Test
    public void windowHasThirtyFiveDaysAndKeepsFutureDates() {
        LocalDate today = LocalDate.of(2026, 10, 21);
        CalendarWindow window = CalendarWindow.containing(today);

        assertEquals(35, CalendarWindow.WEEKS * CalendarWindow.DAYS);
        assertTrue(window.contains(window.start()));
        assertTrue(window.contains(today.plusDays(4)));
        assertFalse(window.contains(today.plusDays(5)));
        assertEquals(today.plusDays(4), window.dateAt(CalendarWindow.WEEKS - 1, CalendarWindow.DAYS - 1));
    }
}
