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
package org.jackhuang.hmcl.community.jvm;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [JvmMemoryOptions] 的 JEP 346 生成测试。
@NotNullByDefault
public final class JvmMemoryOptionsTest {

    /// 未勾选时恒为空。
    @Test
    public void optOutReturnsEmpty() {
        JvmMemoryOptions.GenerationResult result = JvmMemoryOptions.generate(21, "G1", false);
        assertEquals(List.of(), result.args());
        assertEquals("opt_out", result.skipReason());
        assertFalse(result.generated());
    }

    /// Java 11 及更早不生成。
    @Test
    public void javaTooOld() {
        assertEquals("java_too_old", JvmMemoryOptions.generate(11, "G1", true).skipReason());
        assertEquals("java_too_old", JvmMemoryOptions.generate(0, "G1", true).skipReason());
    }

    /// 非 G1 不生成。
    @Test
    public void gcNotG1() {
        assertEquals("gc_not_g1", JvmMemoryOptions.generate(21, "ZGC", true).skipReason());
        assertEquals("gc_not_g1", JvmMemoryOptions.generate(21, null, true).skipReason());
    }

    /// Java 12 从边界开始生成三项参数。
    @Test
    public void generatesForG1() {
        JvmMemoryOptions.GenerationResult result = JvmMemoryOptions.generate(12, "g1", true);
        assertTrue(result.generated());
        assertNull(result.skipReason());
        assertEquals(List.of(
                "-XX:G1PeriodicGCInterval=600000",
                "-XX:MinHeapFreeRatio=5",
                "-XX:MaxHeapFreeRatio=25"
        ), result.args());
    }
}
