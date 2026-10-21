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

import org.jackhuang.hmcl.util.platform.Bits;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [JvmArgValidator] 的规则测试。
@NotNullByDefault
public final class JvmArgValidatorTest {

    /// 构造输入。
    ///
    /// @param args 参数串
    /// @param preset 档位
    /// @param javaMajor Java 大版本
    /// @param bits 位宽
    /// @return 输入
    @NotNull
    private static JvmArgValidator.Input input(@NotNull String args, @NotNull JvmPreset preset,
                                               int javaMajor, @NotNull Bits bits) {
        return new JvmArgValidator.Input(args, preset, javaMajor, bits, 32L * 1024 * 1024 * 1024);
    }

    /// 查找规则 id。
    ///
    /// @param conflicts 冲突列表
    /// @param ruleId 规则 id
    /// @return 是否命中
    private static boolean hasRule(@NotNull List<JvmArgValidator.Conflict> conflicts, @NotNull String ruleId) {
        return conflicts.stream().anyMatch(conflict -> ruleId.equals(conflict.ruleId()));
    }

    /// 堆参数解析：单位与非法值。
    @Test
    public void parsesHeapOptions() {
        assertEquals(4096, JvmArgValidator.parseHeapMb("-Xmx4096m"));
        assertEquals(4096, JvmArgValidator.parseHeapMb("-Xms4G"));
        assertEquals(1536, JvmArgValidator.parseHeapMb("-Xmx1536M"));
        assertEquals(1024, JvmArgValidator.parseHeapMb("-Xmx1048576k"));
        assertEquals(1024, JvmArgValidator.parseHeapMb("-Xmx1073741824"));
        assertNull(JvmArgValidator.parseHeapMb("-Xmxabc"));
        assertNull(JvmArgValidator.parseHeapMb("-Xmx"));
        assertNull(JvmArgValidator.parseHeapMb(null));
    }

    /// 已移除的 GC：新 JDK 为 ERROR，旧 JDK 为 WARN。
    @Test
    public void removedGcSeverityDependsOnJava() {
        List<JvmArgValidator.Conflict> modern = JvmArgValidator.validate(
                input("-XX:+UseConcMarkSweepGC", JvmPreset.CUSTOM, 21, Bits.BIT_64));
        assertEquals(JvmArgValidator.Severity.ERROR, find(modern, "removed_gc").severity());
        assertEquals("remove", find(modern, "removed_gc").action());

        List<JvmArgValidator.Conflict> legacy = JvmArgValidator.validate(
                input("-XX:+UseConcMarkSweepGC", JvmPreset.CUSTOM, 8, Bits.BIT_64));
        assertEquals(JvmArgValidator.Severity.WARN, find(legacy, "removed_gc").severity());
    }

    /// 多个不同 GC 是 ERROR。
    @Test
    public void multipleGc() {
        List<JvmArgValidator.Conflict> conflicts = JvmArgValidator.validate(
                input("-XX:+UseG1GC -XX:+UseZGC", JvmPreset.CUSTOM, 21, Bits.BIT_64));
        assertTrue(hasRule(conflicts, "multiple_gc"));
    }

    /// ZGC 档残留 G1 参数。
    @Test
    public void zgcWithG1() {
        List<JvmArgValidator.Conflict> conflicts = JvmArgValidator.validate(
                input("-Xmx12288m -XX:+UseZGC -XX:G1HeapRegionSize=32m", JvmPreset.ZGC, 21, Bits.BIT_64));
        assertTrue(hasRule(conflicts, "zgc_g1_mix"));
        assertEquals("-XX:G1HeapRegionSize=32m", find(conflicts, "zgc_g1_mix").matchedArg());
    }

    /// 重复 -Xmx 是 WARN。
    @Test
    public void duplicateXmx() {
        List<JvmArgValidator.Conflict> conflicts = JvmArgValidator.validate(
                input("-Xmx4096m -Xmx2048m", JvmPreset.CUSTOM, 17, Bits.BIT_64));
        assertEquals(JvmArgValidator.Severity.WARN, find(conflicts, "duplicate_xmx").severity());
    }

    /// -Xms > -Xmx 是 ERROR。
    @Test
    public void xmsGreaterThanXmx() {
        List<JvmArgValidator.Conflict> conflicts = JvmArgValidator.validate(
                input("-Xms8192m -Xmx4096m", JvmPreset.CUSTOM, 17, Bits.BIT_64));
        assertTrue(hasRule(conflicts, "xms_gt_xmx"));
    }

    /// 32 位 JVM 大堆是 WARN。
    @Test
    public void x32LargeHeap() {
        List<JvmArgValidator.Conflict> conflicts = JvmArgValidator.validate(
                input("-Xmx2048m", JvmPreset.CUSTOM, 8, Bits.BIT_32));
        assertTrue(hasRule(conflicts, "x32_large_heap"));
    }

    /// 旧 GC 日志参数是 WARN。
    @Test
    public void legacyGcLog() {
        List<JvmArgValidator.Conflict> conflicts = JvmArgValidator.validate(
                input("-XX:+PrintGCDetails -XX:+PrintGCTimeStamps", JvmPreset.CUSTOM, 8, Bits.BIT_64));
        assertTrue(hasRule(conflicts, "legacy_gc_log"));
    }

    /// 跟随上游 + 自定义 G1 是 INFO。
    @Test
    public void customGcUnderUpstream() {
        List<JvmArgValidator.Conflict> conflicts = JvmArgValidator.validate(
                input("-XX:+UseG1GC", JvmPreset.UPSTREAM, 17, Bits.BIT_64));
        JvmArgValidator.Conflict conflict = find(conflicts, "custom_gc_upstream");
        assertEquals(JvmArgValidator.Severity.INFO, conflict.severity());
        assertNull(conflict.action());
    }

    /// ZGC 档的三条前置校验。
    @Test
    public void zgcPreconditions() {
        List<JvmArgValidator.Conflict> conflicts = JvmArgValidator.validate(
                input("-Xmx8192m -XX:+UseZGC", JvmPreset.ZGC, 17, Bits.BIT_32));
        assertTrue(hasRule(conflicts, "zgc_small_heap"));
        assertTrue(hasRule(conflicts, "zgc_java_too_old"));
        assertTrue(hasRule(conflicts, "zgc_32bit"));
    }

    /// 空参数无冲突。
    @Test
    public void emptyArgs() {
        assertTrue(JvmArgValidator.validate(input("", JvmPreset.UPSTREAM, 17, Bits.BIT_64)).isEmpty());
    }

    /// 查找指定规则。
    @NotNull
    private static JvmArgValidator.Conflict find(@NotNull List<JvmArgValidator.Conflict> conflicts,
                                                 @NotNull String ruleId) {
        return conflicts.stream()
                .filter(conflict -> ruleId.equals(conflict.ruleId()))
                .findFirst()
                .orElseThrow();
    }
}
