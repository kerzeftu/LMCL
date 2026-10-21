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
package org.jackhuang.hmcl.community.launch;

import org.jackhuang.hmcl.launch.ProcessListener.ExitType;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// [LaunchTracker] 的成功判定与事件分类纯函数测试。
@NotNullByDefault
public final class LaunchTrackerTest {

    /// 存活阈值边界：119999ms 视为未知、120000ms 起视为成功。
    @Test
    public void classifyThresholdBoundaries() {
        assertEquals(LaunchTracker.Outcome.UNKNOWN,
                LaunchTracker.classify(ExitType.NORMAL, 119_999, 120));
        assertEquals(LaunchTracker.Outcome.SUCCESS,
                LaunchTracker.classify(ExitType.NORMAL, 120_000, 120));
        assertEquals(LaunchTracker.Outcome.SUCCESS,
                LaunchTracker.classify(ExitType.NORMAL, 120_001, 120));
    }

    /// 阈值前异常退出视为崩溃。
    @Test
    public void classifyCrashBeforeThreshold() {
        assertEquals(LaunchTracker.Outcome.CRASH,
                LaunchTracker.classify(ExitType.JVM_ERROR, 10_000, 120));
        assertEquals(LaunchTracker.Outcome.CRASH,
                LaunchTracker.classify(ExitType.SIGKILL, 119_999, 120));
    }

    /// 用户主动停止不参与统计。
    @Test
    public void classifyUserInterrupted() {
        assertEquals(LaunchTracker.Outcome.IGNORED,
                LaunchTracker.classify(ExitType.INTERRUPTED, 1, 120));
        assertEquals(LaunchTracker.Outcome.IGNORED,
                LaunchTracker.classify(ExitType.INTERRUPTED, 999_999, 120));
    }

    /// 退出码到事件类型的映射。
    @Test
    public void exitEventKindMapping() {
        assertEquals(LaunchEventKind.EXITED_NORMAL, LaunchTracker.exitEventKind(ExitType.NORMAL));
        assertEquals(LaunchEventKind.ABORTED_BY_USER, LaunchTracker.exitEventKind(ExitType.INTERRUPTED));
        assertEquals(LaunchEventKind.EXITED_ABNORMAL, LaunchTracker.exitEventKind(ExitType.JVM_ERROR));
        assertEquals(LaunchEventKind.EXITED_ABNORMAL, LaunchTracker.exitEventKind(ExitType.APPLICATION_ERROR));
        assertEquals(LaunchEventKind.EXITED_ABNORMAL, LaunchTracker.exitEventKind(ExitType.SIGKILL));
    }
}
