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
package org.jackhuang.hmcl.community.preflight;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// [PreflightChecks] 纯阈值函数测试。
@NotNullByDefault
public final class PreflightChecksTest {

    private static final long MIB = 1024L * 1024;
    private static final long GIB = 1024L * MIB;

    /// 内存阈值：超过 total*ratio 为错误。
    @Test
    public void classifyMemoryBoundaries() {
        long total = 16 * GIB;
        assertEquals(Severity.SKIPPED, PreflightChecks.classifyMemory(1024, 0, 0.9));
        // 14.4 GiB = 14745.6 MiB
        assertEquals(Severity.ERROR, PreflightChecks.classifyMemory(14746, total, 0.9));
        assertEquals(Severity.WARN, PreflightChecks.classifyMemory(14745, total, 0.9));
        // 13.5 GiB = 13824 MiB
        assertEquals(Severity.WARN, PreflightChecks.classifyMemory(13825, total, 0.9));
        assertEquals(Severity.OK, PreflightChecks.classifyMemory(13824, total, 0.9));
    }

    /// 磁盘阈值：低于 min 为错误、低于 warn 为警告。
    @Test
    public void classifyDiskBoundaries() {
        assertEquals(Severity.ERROR, PreflightChecks.classifyDisk(499 * MIB, 500, 2048));
        assertEquals(Severity.WARN, PreflightChecks.classifyDisk(500 * MIB, 500, 2048));
        assertEquals(Severity.WARN, PreflightChecks.classifyDisk(2047 * MIB, 500, 2048));
        assertEquals(Severity.OK, PreflightChecks.classifyDisk(2048 * MIB, 500, 2048));
    }

    /// 严重程度排序：ERROR > WARN > OK > SKIPPED。
    @Test
    public void severityWorstAndParse() {
        assertEquals(Severity.ERROR, Severity.worst(Severity.WARN, Severity.ERROR));
        assertEquals(Severity.WARN, Severity.worst(Severity.SKIPPED, Severity.WARN));
        assertNull(Severity.parse("nonsense"));
        assertEquals(Severity.OK, Severity.parse("ok"));
    }
}
