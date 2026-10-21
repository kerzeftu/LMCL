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
package org.jackhuang.hmcl.community.memory;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 内存回收触发条件的纯函数测试（不启动调度线程、不执行 GC）。
@NotNullByDefault
public final class MemoryReclaimerPolicyTest {

    private static final long MIB = 1024L * 1024L;

    /// 构造一个默认满足全部条件的上下文。
    private static MemoryReclaimer.TriggerContext eligible() {
        return new MemoryReclaimer.TriggerContext(
                true,
                false,
                true,
                20 * 60_000L,
                5 * 60_000L,
                1024 * MIB,
                256 * MIB,
                10,
                128);
    }

    /// 用户关闭自动回收时不触发。
    @Test
    public void rejectsWhenDisabled() {
        assertEquals("disabled", MemoryReclaimer.rejectReason(new MemoryReclaimer.TriggerContext(
                false, false, true, 20 * 60_000L, 5 * 60_000L, 1024 * MIB, 256 * MIB, 10, 128)));
    }

    /// JVM 禁用显式 GC 时不触发。
    @Test
    public void rejectsExplicitGcDisabled() {
        assertEquals("explicit_gc_disabled", MemoryReclaimer.rejectReason(new MemoryReclaimer.TriggerContext(
                true, true, true, 20 * 60_000L, 5 * 60_000L, 1024 * MIB, 256 * MIB, 10, 128)));
    }

    /// 没有稳定运行的游戏时不触发。
    @Test
    public void rejectsWithoutRunningGame() {
        assertEquals("no_game", MemoryReclaimer.rejectReason(new MemoryReclaimer.TriggerContext(
                true, false, false, 20 * 60_000L, 5 * 60_000L, 1024 * MIB, 256 * MIB, 10, 128)));
    }

    /// 距上次回收不足间隔时不触发。
    @Test
    public void rejectsBeforeInterval() {
        assertEquals("interval", MemoryReclaimer.rejectReason(new MemoryReclaimer.TriggerContext(
                true, false, true, 9 * 60_000L, 5 * 60_000L, 1024 * MIB, 256 * MIB, 10, 128)));
    }

    /// 最近一次启动请求后 60 秒内不触发。
    @Test
    public void rejectsNearLaunch() {
        assertEquals("launch_guard", MemoryReclaimer.rejectReason(new MemoryReclaimer.TriggerContext(
                true, false, true, 20 * 60_000L, 59_999L, 1024 * MIB, 256 * MIB, 10, 128)));
    }

    /// 已提交堆低于 256 MiB 时不触发。
    @Test
    public void rejectsSmallCommittedHeap() {
        assertEquals("committed_too_low", MemoryReclaimer.rejectReason(new MemoryReclaimer.TriggerContext(
                true, false, true, 20 * 60_000L, 5 * 60_000L, 255 * MIB, 64 * MIB, 10, 128)));
    }

    /// 可归还量低于阈值时不触发。
    @Test
    public void rejectsSmallReclaimableAmount() {
        assertEquals("reclaimable_too_low", MemoryReclaimer.rejectReason(new MemoryReclaimer.TriggerContext(
                true, false, true, 20 * 60_000L, 5 * 60_000L, 1024 * MIB, 1024 * MIB - 127 * MIB, 10, 128)));
    }

    /// 全部条件满足时返回 `null`（可以回收）。
    @Test
    public void acceptsWhenAllConditionsMet() {
        assertNull(MemoryReclaimer.rejectReason(eligible()));
    }

    /// 间隔与启动保护期的边界值恰好满足时允许。
    @Test
    public void acceptsExactBoundaries() {
        assertNull(MemoryReclaimer.rejectReason(new MemoryReclaimer.TriggerContext(
                true, false, true, 10 * 60_000L, 60_000L, 256 * MIB, 0, 10, 128)));
    }

    /// 慢回收把间隔乘三，上限 60 分钟。
    @Test
    public void escalatesInterval() {
        assertEquals(10, MemoryReclaimer.escalatedInterval(10, 400));
        assertEquals(30, MemoryReclaimer.escalatedInterval(10, 501));
        assertEquals(60, MemoryReclaimer.escalatedInterval(30, 501));
        assertEquals(60, MemoryReclaimer.escalatedInterval(40, 2000));
        assertEquals(3, MemoryReclaimer.escalatedInterval(1, 600));
    }

    /// 手动回收冷却为 60 秒。
    @Test
    public void checksManualCooldown() {
        assertTrue(MemoryReclaimer.canManualReclaim(100_000, 0));
        assertTrue(MemoryReclaimer.canManualReclaim(60_000, 0));
        assertFalse(MemoryReclaimer.canManualReclaim(59_999, 0));
        assertFalse(MemoryReclaimer.canManualReclaim(120_000, 61_000));
    }

    /// 识别 `-XX:+DisableExplicitGC`。
    @Test
    public void detectsExplicitGcDisabled() {
        assertTrue(MemoryReclaimer.detectExplicitGcDisabled(
                List.of("-Xmx2G", "-XX:+DisableExplicitGC")));
        assertFalse(MemoryReclaimer.detectExplicitGcDisabled(
                List.of("-Xmx2G", "-XX:-DisableExplicitGC")));
        assertFalse(MemoryReclaimer.detectExplicitGcDisabled(List.of()));
    }
}
