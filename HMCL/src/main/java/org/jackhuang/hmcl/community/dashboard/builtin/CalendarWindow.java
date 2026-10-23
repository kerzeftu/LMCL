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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/// 游玩日历的固定 5×7 日期窗口。
///
/// 行是自然周，列是周一至周日；最后一行是“今天”所在的周，所以最新的日期
/// 永远在底部。窗口从四周前的周一开始，共 35 天；未来日期仍保留在窗口中，
/// 由 UI 使用淡色样式展示。
///
/// @param start 窗口第一行周一
@NotNullByDefault
record CalendarWindow(@NotNull LocalDate start) {

    /// 周数。
    static final int WEEKS = 5;
    /// 一周天数。
    static final int DAYS = 7;

    /// 校验起点必须是周一。
    CalendarWindow {
        if (start.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("start must be Monday: " + start);
        }
    }

    /// 创建包含指定日期的 5 周窗口。
    ///
    /// @param today 今天
    /// @return 日期窗口
    @NotNull
    static CalendarWindow containing(@NotNull LocalDate today) {
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new CalendarWindow(thisMonday.minusWeeks(WEEKS - 1L));
    }

    /// 取指定行列的日期。
    ///
    /// @param week 行，0 为最早一周，4 为本周
    /// @param day 列，0 为周一，6 为周日
    /// @return 日期
    @NotNull
    LocalDate dateAt(int week, int day) {
        return start.plusDays((long) week * DAYS + day);
    }

    /// 判断日期是否落在窗口内。
    ///
    /// @param date 日期
    /// @return 是否在窗口内
    boolean contains(@NotNull LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(dateAt(WEEKS - 1, DAYS - 1));
    }
}
