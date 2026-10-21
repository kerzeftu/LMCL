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

import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [CommunityLaunchHooks] 的任务链语义测试。
///
/// 注意：hooks 的静态任务表在 JVM 内共享，测试用唯一 id 并固定方法顺序。
@NotNullByDefault
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public final class CommunityLaunchHooksTest {

    /// 构造最小上下文（只用到 versionId/gameVersion）。
    private static @NotNull LaunchContext context(@NotNull String versionId) {
        return new LaunchContext(null, null, versionId, null, null, "1.20.1", false);
    }

    /// 按 order 升序执行。
    @Test
    @Order(1)
    public void executesInOrder() {
        List<String> events = new CopyOnWriteArrayList<>();
        CommunityLaunchHooks.registerStartupTask(new TestTask("test-order-b", 20, events::add));
        CommunityLaunchHooks.registerStartupTask(new TestTask("test-order-a", 10, events::add));
        CommunityLaunchHooks.runStartupTasksNow(context("test-order"));
        int first = events.indexOf("test-order-a");
        int second = events.indexOf("test-order-b");
        assertTrue(first >= 0, "task a should run");
        assertTrue(second > first, "task a should run before task b");
    }

    /// 单任务 RuntimeException 被吞掉，后续任务继续执行。
    @Test
    @Order(2)
    public void failureIsSwallowed() {
        List<String> events = new ArrayList<>();
        CommunityLaunchHooks.registerStartupTask(new TestTask("test-fail-boom", 30, id -> {
            throw new IllegalStateException("boom");
        }));
        CommunityLaunchHooks.registerStartupTask(new TestTask("test-fail-next", 40, events::add));
        CommunityLaunchHooks.runStartupTasksNow(context("test-fail"));
        assertTrue(events.contains("test-fail-next"), "later task should still run");
    }

    /// 重复 id 只保留第一个注册。
    @Test
    @Order(3)
    public void duplicateIdIgnored() {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        CommunityLaunchHooks.registerStartupTask(new TestTask("test-duplicate", 50, id -> first.incrementAndGet()));
        CommunityLaunchHooks.registerStartupTask(new TestTask("test-duplicate", 51, id -> second.incrementAndGet()));
        CommunityLaunchHooks.runStartupTasksNow(context("test-duplicate"));
        assertEquals(1, first.get());
        assertEquals(0, second.get());
    }

    /// 任务内 CancellationException 传播为启动取消。
    @Test
    @Order(4)
    public void cancellationPropagates() {
        CommunityLaunchHooks.registerStartupTask(new TestTask("test-cancel", 5, id -> {
            throw new CancellationException("stop");
        }));
        assertThrows(CancellationException.class,
                () -> CommunityLaunchHooks.runStartupTasksNow(context("test-cancel")));
    }

    /// 测试用启动任务。
    private static final class TestTask implements LaunchStartupTask {

        private final String id;
        private final int order;
        private final java.util.function.Consumer<String> action;

        TestTask(String id, int order, java.util.function.Consumer<String> action) {
            this.id = id;
            this.order = order;
            this.action = action;
        }

        @Override
        public @NotNull String id() {
            return id;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public @NotNull Task<?> run(@NotNull LaunchContext context) {
            return Task.runAsync("test: " + id, Schedulers.io(), () -> action.accept(id));
        }
    }
}
