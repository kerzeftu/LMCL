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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// JVM 参数冲突检测（纯函数，不依赖 UI / 文件系统）。
///
/// 输入是实例当前的 `jvmOptions` 字符串、向导档位、实例 Java 大版本、位宽与物理内存，
/// 输出是 [#Conflict] 列表。`ERROR` 只表示“很可能起不来”，**不阻止应用**：
/// 用户永远有最终决定权。
///
/// 规则编号见 `docs/steps/11-性能策略-JVM参数预设向导.md` §3.4；
/// 额外补充 ZGC 的三条前置校验（Java 版本、堆大小、物理内存），
/// 由步骤 11 的 DoD“ZGC+小堆”要求引入。
@NotNullByDefault
public final class JvmArgValidator {

    /// ZGC 档要求的最低 Java 大版本。
    public static final int ZGC_MIN_JAVA_MAJOR = 21;

    /// ZGC 档要求的最小堆（MiB）。
    public static final int ZGC_MIN_HEAP_MB = 12 * 1024;

    /// ZGC 档要求堆之外预留的物理内存（MiB）。
    public static final int ZGC_RESERVED_PHYSICAL_MB = 4 * 1024;

    /// 32 位 JVM 的“大堆”阈值（MiB）。
    public static final int X32_LARGE_HEAP_MB = 1536;

    /// 单个堆参数的正则。
    private static final Pattern HEAP_OPTION = Pattern.compile("-Xm([sx])(\\d+)([kKmMgGtT]?)");

    /// GC 开关的正则（只匹配 `+Use*GC` 形式）。
    private static final Pattern GC_FLAG = Pattern.compile("-XX:\\+Use([A-Za-z0-9]+)GC");

    /// 已在新 JDK 移除的 GC。
    private static final Set<String> REMOVED_GC = Set.of("UseConcMarkSweepGC", "UseParNewGC");

    /// 旧版 GC 日志参数（前缀匹配）。
    private static final List<String> LEGACY_GC_LOG_PREFIXES = List.of(
            "-XX:+PrintGCDetails",
            "-XX:+PrintGCDateStamps",
            "-XX:+PrintGCTimeStamps",
            "-XX:+PrintHeapAtGC",
            "-XX:+PrintGCApplicationStoppedTime",
            "-Xloggc:"
    );

    private JvmArgValidator() {
    }

    /// 冲突级别。
    public enum Severity {
        /// 很可能无法启动。
        ERROR,
        /// 可能不是用户想要的结果。
        WARN,
        /// 说明性提示。
        INFO
    }

    /// 一条冲突。
    ///
    /// @param severity 级别
    /// @param ruleId 规则 id（同时是 i18n key 后缀：`community.jvm.conflict.<ruleId>`）
    /// @param matchedArg 命中的参数；与单条参数无关时为 `null`
    /// @param action 可执行动作：`remove` 表示可一键移除 [#matchedArg()]；无动作为 `null`
    public record Conflict(
            @NotNull Severity severity,
            @NotNull String ruleId,
            @Nullable String matchedArg,
            @Nullable String action
    ) {

        /// 返回文案 key。
        ///
        /// @return `community.jvm.conflict.<ruleId>`
        @NotNull
        public String messageKey() {
            return "community.jvm.conflict." + ruleId;
        }
    }

    /// 检测输入。
    ///
    /// @param jvmOptions 实例当前的自定义参数（允许空串）
    /// @param preset 向导档位
    /// @param javaMajor 实例 Java 大版本；`<= 0` 表示未知
    /// @param bits 实例 Java 位宽
    /// @param physicalMemoryBytes 物理内存字节数；`<= 0` 表示未知
    public record Input(
            @NotNull String jvmOptions,
            @NotNull JvmPreset preset,
            int javaMajor,
            @NotNull Bits bits,
            long physicalMemoryBytes
    ) {
    }

    /// 执行全部规则。
    ///
    /// @param input 输入
    /// @return 冲突列表（可能为空，顺序稳定）
    @NotNull
    @Unmodifiable
    public static List<Conflict> validate(@NotNull Input input) {
        List<String> tokens = tokenize(input.jvmOptions());
        List<Conflict> conflicts = new ArrayList<>();

        List<String> gcFlags = new ArrayList<>();
        for (String token : tokens) {
            Matcher matcher = GC_FLAG.matcher(token);
            if (matcher.matches()) {
                gcFlags.add(token);
            }
        }

        // 1. 已移除的 GC
        for (String token : gcFlags) {
            if (REMOVED_GC.contains(token.substring("-XX:+".length()))) {
                Severity severity = input.javaMajor() >= 14 ? Severity.ERROR : Severity.WARN;
                conflicts.add(new Conflict(severity, "removed_gc", token, "remove"));
                break;
            }
        }

        // 2. 多个不同 GC
        Set<String> distinctGc = new LinkedHashSet<>();
        for (String token : gcFlags) {
            distinctGc.add(token.substring("-XX:+Use".length()));
        }
        if (distinctGc.size() > 1) {
            conflicts.add(new Conflict(Severity.ERROR, "multiple_gc", gcFlags.get(1), "remove"));
        }

        // 3. ZGC 档 + 残留 G1 参数
        if (input.preset() == JvmPreset.ZGC) {
            String g1Token = findG1Token(tokens);
            if (g1Token != null) {
                conflicts.add(new Conflict(Severity.ERROR, "zgc_g1_mix", g1Token, "remove"));
            }
        }

        // 4. -Xmx 重复
        List<String> xmxTokens = heapTokens(tokens, 'x');
        if (xmxTokens.size() > 1) {
            conflicts.add(new Conflict(Severity.WARN, "duplicate_xmx", xmxTokens.get(1), "remove"));
        }

        // 5. -Xms > -Xmx
        List<String> xmsTokens = heapTokens(tokens, 's');
        if (!xmsTokens.isEmpty() && !xmxTokens.isEmpty()) {
            Integer xms = parseHeapMb(xmsTokens.get(0));
            Integer xmx = parseHeapMb(xmxTokens.get(0));
            if (xms != null && xmx != null && xms > xmx) {
                conflicts.add(new Conflict(Severity.ERROR, "xms_gt_xmx", xmsTokens.get(0), "remove"));
            }
        }

        Integer xmx = xmxTokens.isEmpty() ? null : parseHeapMb(xmxTokens.get(0));

        // 6. 32 位 JVM + 大堆
        if (input.bits() == Bits.BIT_32 && xmx != null && xmx >= X32_LARGE_HEAP_MB) {
            conflicts.add(new Conflict(Severity.WARN, "x32_large_heap", xmxTokens.get(0), "remove"));
        }

        // 7. 旧版 GC 日志参数
        for (String token : tokens) {
            if (LEGACY_GC_LOG_PREFIXES.stream().anyMatch(token::startsWith)) {
                conflicts.add(new Conflict(Severity.WARN, "legacy_gc_log", token, "remove"));
                break;
            }
        }

        // 8. 跟随上游但用户自定义了 G1
        if (input.preset() == JvmPreset.UPSTREAM) {
            for (String token : gcFlags) {
                if ("-XX:+UseG1GC".equals(token)) {
                    conflicts.add(new Conflict(Severity.INFO, "custom_gc_upstream", token, null));
                    break;
                }
            }
        }

        // 9. ZGC + 小堆 / Java 版本不足 / 32 位 / 物理内存不足
        if (input.preset() == JvmPreset.ZGC) {
            if (input.javaMajor() > 0 && input.javaMajor() < ZGC_MIN_JAVA_MAJOR) {
                conflicts.add(new Conflict(Severity.ERROR, "zgc_java_too_old", null, null));
            }
            if (input.bits() == Bits.BIT_32) {
                conflicts.add(new Conflict(Severity.ERROR, "zgc_32bit", null, null));
            }
            if (xmx != null && xmx < ZGC_MIN_HEAP_MB) {
                conflicts.add(new Conflict(Severity.ERROR, "zgc_small_heap", xmxTokens.get(0), "remove"));
            }
            long physicalMb = input.physicalMemoryBytes() / (1024L * 1024L);
            if (physicalMb > 0 && xmx != null && physicalMb < (long) xmx + ZGC_RESERVED_PHYSICAL_MB) {
                conflicts.add(new Conflict(Severity.WARN, "zgc_physical_memory", null, null));
            }
        }

        return List.copyOf(conflicts);
    }

    /// 解析单个堆参数（`-Xms` / `-Xmx`）为 MiB。
    ///
    /// @param token 参数，例如 `-Xms4096m`
    /// @return MiB；格式非法时返回 `null`
    @Nullable
    public static Integer parseHeapMb(@Nullable String token) {
        if (token == null) {
            return null;
        }
        Matcher matcher = HEAP_OPTION.matcher(token);
        if (!matcher.matches()) {
            return null;
        }
        try {
            long value = Long.parseLong(matcher.group(2));
            String unit = matcher.group(3).toLowerCase(Locale.ROOT);
            long mb = switch (unit) {
                case "k" -> (value + 1023L) / 1024L;
                case "m" -> value;
                case "g" -> value * 1024L;
                case "t" -> value * 1024L * 1024L;
                default -> (value + 1024L * 1024L - 1L) / (1024L * 1024L);
            };
            if (mb <= 0) {
                return null;
            }
            return mb > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) mb;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /// 按空白切分参数串。
    ///
    /// @param args 参数串
    /// @return 非空 token 列表
    @NotNull
    @Unmodifiable
    public static List<String> tokenize(@Nullable String args) {
        if (args == null || args.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : args.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    /// 返回指定前缀的堆参数（`'s'` = `-Xms`，`'x'` = `-Xmx`）。
    ///
    /// @param tokens token 列表
    /// @param kind `s` 或 `x`
    /// @return 匹配的参数，保持出现顺序
    @NotNull
    @Unmodifiable
    private static List<String> heapTokens(@NotNull List<String> tokens, char kind) {
        List<String> result = new ArrayList<>();
        String prefix = "-Xm" + kind;
        for (String token : tokens) {
            if (token.startsWith(prefix) && token.length() > prefix.length()
                    && Character.isDigit(token.charAt(prefix.length()))) {
                result.add(token);
            }
        }
        return result;
    }

    /// 查找 G1 开关或 G1 子参数。
    ///
    /// @param tokens token 列表
    /// @return 第一个命中的 token，没有则 `null`
    @Nullable
    private static String findG1Token(@NotNull List<String> tokens) {
        for (String token : tokens) {
            if ("-XX:+UseG1GC".equals(token) || token.startsWith("-XX:G1")) {
                return token;
            }
        }
        return null;
    }
}
