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

import org.jackhuang.hmcl.community.play.PlayRecordStore.DayRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.time.LocalDate;
import java.util.Map;

/// 游玩记录的聚合统计：累计时长、最近七天时长与有效天数。
///
/// 只做内存计算，不读文件；调用方负责在 io 线程读取 [PlayRecordStore]。
///
/// @param totalSeconds 全部记录的游玩秒数
/// @param last7Seconds 含今天在内最近 7 天的游玩秒数
/// @param activeDays 有游玩时长的天数
@NotNullByDefault
public record PlayStats(long totalSeconds, long last7Seconds, int activeDays) {

    /// 汇总游玩记录。
    ///
    /// @param data 日期到记录的映射
    /// @param today 今天（本地日期）
    /// @return 聚合结果
    @NotNull
    public static PlayStats of(@NotNull Map<LocalDate, DayRecord> data, @NotNull LocalDate today) {
        LocalDate firstDay = today.minusDays(6);
        long totalSeconds = 0;
        long last7Seconds = 0;
        int activeDays = 0;

        for (Map.Entry<LocalDate, DayRecord> entry : data.entrySet()) {
            long seconds = entry.getValue().playSeconds();
            totalSeconds += seconds;

            LocalDate date = entry.getKey();
            if (!date.isBefore(firstDay) && !date.isAfter(today)) {
                last7Seconds += seconds;
            }
            if (seconds > 0) {
                activeDays++;
            }
        }

        return new PlayStats(totalSeconds, last7Seconds, activeDays);
    }
}
