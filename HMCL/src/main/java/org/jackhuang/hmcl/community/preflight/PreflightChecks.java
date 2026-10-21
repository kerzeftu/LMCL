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

import org.jackhuang.hmcl.community.launch.LaunchContext;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 五项启动前检查的具体判定。
///
/// 阈值函数都设计成纯函数（[PreflightChecks#classifyMemory(int, long, double)]、
/// [PreflightChecks#classifyDisk(long, int, int)]），便于单测覆盖边界。
@NotNullByDefault
public final class PreflightChecks {

    /// Java 兼容性检查 id。
    public static final String ID_JAVA = "java.compat";
    /// 内存预算检查 id。
    public static final String ID_MEMORY = "memory.budget";
    /// 磁盘空间检查 id。
    public static final String ID_DISK = "disk.space";
    /// 模组重复检查 id。
    public static final String ID_MODS = "mods.duplicate";
    /// 缺失前置检查 id。
    public static final String ID_DEPS = "mods.missing_deps";

    /// 内存警告余量：2 GiB。
    private static final long WARN_MEMORY_MARGIN = 2L * 1024 * 1024 * 1024;
    /// 内存警告额外余量：512 MiB。
    private static final long WARN_MEMORY_EXTRA = 512L * 1024 * 1024;

    private PreflightChecks() {
    }

    /// Java 兼容性检查。
    ///
    /// @param context 启动上下文
    /// @return 结果
    @NotNull
    public static CheckResult checkJava(@NotNull LaunchContext context) {
        long start = System.nanoTime();
        String gameVersion = resolveGameVersion(context);
        if (gameVersion == null) {
            return skipped(ID_JAVA, "unreadable", start, "reason", "game_version_unknown");
        }

        GameJavaVersion minimum;
        try {
            minimum = GameJavaVersion.getMinimumJavaVersion(GameVersionNumber.asGameVersion(gameVersion));
        } catch (Throwable e) {
            LOG.warning("Failed to resolve minimum Java for " + gameVersion, e);
            return skipped(ID_JAVA, "unreadable", start, "reason", "game_version_unparsable");
        }
        if (minimum == null) {
            return skipped(ID_JAVA, "unreadable", start, "reason", "minimum_unknown");
        }

        JavaRuntime java = context.java();
        if (java == null) {
            Map<String, String> details = new LinkedHashMap<>();
            details.put("required", Integer.toString(minimum.majorVersion()));
            return CheckResult.of(ID_JAVA, Severity.ERROR, "java_no_java", elapsed(start), details);
        }

        int current = java.getParsedVersion();
        Map<String, String> details = new LinkedHashMap<>();
        details.put("required", Integer.toString(minimum.majorVersion()));
        details.put("current", Integer.toString(current));
        details.put("java", java.getVersion());
        if (current < minimum.majorVersion()) {
            return CheckResult.of(ID_JAVA, Severity.ERROR, "java_low", elapsed(start), details);
        }
        return CheckResult.of(ID_JAVA, Severity.OK, "java_ok", elapsed(start), details);
    }

    /// 内存预算检查。
    ///
    /// @param context 启动上下文
    /// @param maxRatio 占物理内存上限比例
    /// @return 结果
    @NotNull
    public static CheckResult checkMemory(@NotNull LaunchContext context, double maxRatio) {
        long start = System.nanoTime();
        if (context.setting() == null) {
            return skipped(ID_MEMORY, "offline", start, "reason", "no_setting");
        }
        int allocatedMb = context.setting().getMaxMemory();
        long totalBytes = SystemInfo.getTotalMemorySize();
        Severity severity = classifyMemory(allocatedMb, totalBytes, maxRatio);
        if (severity == Severity.SKIPPED) {
            return skipped(ID_MEMORY, "unreadable", start, "reason", "total_memory_unknown");
        }
        Map<String, String> details = new LinkedHashMap<>();
        details.put("allocatedMb", Integer.toString(allocatedMb));
        details.put("totalMb", Long.toString(totalBytes / (1024 * 1024)));
        details.put("ratio", String.format(Locale.ROOT, "%.2f", maxRatio));
        return CheckResult.of(ID_MEMORY, severity, "memory_" + severity.name().toLowerCase(Locale.ROOT),
                elapsed(start), details);
    }

    /// 磁盘空间检查。
    ///
    /// @param context 启动上下文
    /// @param config 配置（读取阈值）
    /// @return 结果
    @NotNull
    public static CheckResult checkDisk(@NotNull LaunchContext context, @NotNull PreflightConfig config) {
        long start = System.nanoTime();
        long usable;
        try {
            FileStore store = Files.getFileStore(context.repository().getBaseDirectory());
            usable = store.getUsableSpace();
        } catch (IOException | RuntimeException e) {
            LOG.warning("Failed to read disk space", e);
            return skipped(ID_DISK, "unreadable", start, "reason", "file_store_unreadable");
        }
        Severity severity = classifyDisk(usable, config.minDiskMb(), config.warnDiskMb());
        Map<String, String> details = new LinkedHashMap<>();
        details.put("remainingMb", Long.toString(usable / (1024 * 1024)));
        details.put("minDiskMb", Integer.toString(config.minDiskMb()));
        details.put("warnDiskMb", Integer.toString(config.warnDiskMb()));
        return CheckResult.of(ID_DISK, severity, "disk_" + severity.name().toLowerCase(Locale.ROOT),
                elapsed(start), details);
    }

    /// 模组重复检查（只 WARN）。
    ///
    /// @param result 扫描结果
    /// @return 结果
    @NotNull
    public static CheckResult checkDuplicateMods(@NotNull ModScanner.ScanResult result) {
        long start = System.nanoTime();
        if (result.tooMany()) {
            return skipped(ID_MODS, "unreadable", start, "reason", "too_many_files");
        }
        Map<String, List<String>> byId = new LinkedHashMap<>();
        for (ModScanner.ModInfo mod : result.mods()) {
            byId.computeIfAbsent(mod.id().toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(mod.fileName());
        }
        List<String> duplicated = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : byId.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicated.add(entry.getKey() + ": " + String.join(", ", entry.getValue()));
            }
        }
        if (duplicated.isEmpty()) {
            return CheckResult.of(ID_MODS, Severity.OK, "duplicate_none", elapsed(start), Map.of());
        }
        Map<String, String> details = new LinkedHashMap<>();
        details.put("mods", String.join("; ", duplicated));
        details.put("count", Integer.toString(duplicated.size()));
        return CheckResult.of(ID_MODS, Severity.WARN, "duplicate_mods", elapsed(start), details);
    }

    /// 缺失前置检查（只 WARN）。
    ///
    /// @param result 扫描结果
    /// @return 结果
    @NotNull
    public static CheckResult checkMissingDependencies(@NotNull ModScanner.ScanResult result) {
        long start = System.nanoTime();
        if (result.tooMany()) {
            return skipped(ID_DEPS, "unreadable", start, "reason", "too_many_files");
        }
        if (result.unparseableRatio() > ModScanner.MAX_UNPARSEABLE_RATIO) {
            return skipped(ID_DEPS, "unreadable", start, "reason", "too_many_unparseable");
        }
        Map<String, List<String>> missing = ModScanner.missingDependencies(result);
        if (missing.isEmpty()) {
            return CheckResult.of(ID_DEPS, Severity.OK, "missing_deps_none", elapsed(start), Map.of());
        }
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : missing.entrySet()) {
            items.add(entry.getKey() + " ← " + String.join(", ", entry.getValue()));
        }
        Map<String, String> details = new LinkedHashMap<>();
        details.put("mods", String.join("; ", items));
        details.put("count", Integer.toString(items.size()));
        return CheckResult.of(ID_DEPS, Severity.WARN, "missing_deps", elapsed(start), details);
    }

    /// 内存阈值判定（纯函数）。
    ///
    /// @param allocatedMb 分配的 Xmx（MiB）
    /// @param totalBytes 物理内存（字节，`<=0` 表示未知）
    /// @param maxRatio 占物理内存上限比例
    /// @return 严重程度
    @NotNull
    public static Severity classifyMemory(int allocatedMb, long totalBytes, double maxRatio) {
        if (totalBytes <= 0) {
            return Severity.SKIPPED;
        }
        long allocatedBytes = (long) allocatedMb * 1024 * 1024;
        if (allocatedBytes > totalBytes * maxRatio) {
            return Severity.ERROR;
        }
        if (allocatedBytes > totalBytes - WARN_MEMORY_MARGIN - WARN_MEMORY_EXTRA) {
            return Severity.WARN;
        }
        return Severity.OK;
    }

    /// 磁盘阈值判定（纯函数）。
    ///
    /// @param usableBytes 可用字节
    /// @param minDiskMb ERROR 阈值（MiB）
    /// @param warnDiskMb WARN 阈值（MiB）
    /// @return 严重程度
    @NotNull
    public static Severity classifyDisk(long usableBytes, int minDiskMb, int warnDiskMb) {
        long usableMb = usableBytes / (1024 * 1024);
        if (usableMb < minDiskMb) {
            return Severity.ERROR;
        }
        if (usableMb < warnDiskMb) {
            return Severity.WARN;
        }
        return Severity.OK;
    }

    /// 解析游戏版本；上下文中缺失时回查实例。
    static @Nullable String resolveGameVersion(@NotNull LaunchContext context) {
        if (context.gameVersion() != null && !context.gameVersion().isBlank()) {
            return context.gameVersion();
        }
        try {
            return context.repository().getGameVersion(context.version()).orElse(null);
        } catch (Throwable e) {
            LOG.warning("Failed to resolve game version for " + context.versionId(), e);
            return null;
        }
    }

    /// 创建 SKIPPED 结果。
    private static @NotNull CheckResult skipped(
            @NotNull String id,
            @NotNull String code,
            long start,
            @NotNull String key,
            @NotNull String value) {
        return CheckResult.of(id, Severity.SKIPPED, code, elapsed(start), Map.of(key, value));
    }

    /// 计算耗时（毫秒）。
    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
