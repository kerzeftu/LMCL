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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Locale;

/// JEP 346（G1 空闲时归还内存）参数的纯函数生成器。
///
/// 只在用户显式勾选（`userOptIn=true`）、JVM 为 Java 12+ 且 GC 为 G1 时返回三项参数；
/// 其余情况返回空列表并给出 [GenerationResult#skipReason()]。
///
/// 为什么要判断 Java 版本：这三项参数依赖 JEP 346，Java 11 及更早没有
/// `G1PeriodicGCInterval`，写了会直接启动失败。
@NotNullByDefault
public final class JvmMemoryOptions {

    /// `-XX:G1PeriodicGCInterval` 的检查间隔（毫秒）。
    public static final int G1_PERIODIC_GC_INTERVAL_MILLIS = 600_000;

    /// `-XX:MinHeapFreeRatio` 的取值。
    public static final int MIN_HEAP_FREE_RATIO = 5;

    /// `-XX:MaxHeapFreeRatio` 的取值。
    public static final int MAX_HEAP_FREE_RATIO = 25;

    /// JEP 346 要求的最低 Java 大版本。
    public static final int MIN_JAVA_MAJOR = 12;

    private JvmMemoryOptions() {
    }

    /// 生成 JEP 346 参数片段。
    ///
    /// @param javaMajor 实例使用的 Java 大版本；`<= 0` 表示未知（按不满足处理）
    /// @param gcName GC 名称（大小写不敏感，`G1` 才生成）
    /// @param userOptIn 用户是否显式勾选
    /// @return 生成结果，永不为 `null`
    @NotNull
    public static GenerationResult generate(int javaMajor, @Nullable String gcName, boolean userOptIn) {
        if (!userOptIn) {
            return new GenerationResult(List.of(), "opt_out");
        }
        if (javaMajor <= 0 || javaMajor < MIN_JAVA_MAJOR) {
            return new GenerationResult(List.of(), "java_too_old");
        }
        if (gcName == null || !"G1".equals(gcName.trim().toUpperCase(Locale.ROOT))) {
            return new GenerationResult(List.of(), "gc_not_g1");
        }
        return new GenerationResult(List.of(
                "-XX:G1PeriodicGCInterval=" + G1_PERIODIC_GC_INTERVAL_MILLIS,
                "-XX:MinHeapFreeRatio=" + MIN_HEAP_FREE_RATIO,
                "-XX:MaxHeapFreeRatio=" + MAX_HEAP_FREE_RATIO
        ), null);
    }

    /// 生成结果。
    ///
    /// @param args 生成的参数（`skipReason != null` 时为空）
    /// @param skipReason 跳过原因：`opt_out` / `java_too_old` / `gc_not_g1`；成功为 `null`
    public record GenerationResult(@NotNull @Unmodifiable List<String> args, @Nullable String skipReason) {

        /// 是否生成了参数。
        ///
        /// @return 是否成功
        public boolean generated() {
            return skipReason == null;
        }
    }
}
