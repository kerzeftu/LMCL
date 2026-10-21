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

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.community.launch.LaunchContext;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.task.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 启动前体检的编排与结果持久化。
///
/// 五项检查在 `Schedulers.io()` 上并发执行，每项 800ms 软超时（超时 → SKIPPED），
/// 整个方法最多阻塞约 1s。检查过程只读，不写实例目录、不联网。
@NotNullByDefault
public final class PreflightService {

    /// 单项检查软超时（毫秒）。
    public static final long CHECK_TIMEOUT_MILLIS = 800;

    /// 体检结果相对本地根的路径。
    private static final Path REPORT_FILE = Metadata.HMCL_LOCAL_HOME.resolve("community/preflight/last.json");

    /// 上一次体检结果的内存缓存。
    private static final AtomicReference<@Nullable PreflightReport> LAST_REPORT = new AtomicReference<>();

    private PreflightService() {
    }

    /// 返回体检结果文件路径。
    ///
    /// @return 路径
    @NotNull
    public static Path reportFile() {
        return REPORT_FILE;
    }

    /// 返回上一次体检结果；内存缓存缺失时读盘，读失败返回 `null`。
    ///
    /// @return 报告或 `null`
    @Nullable
    public static PreflightReport lastReport() {
        PreflightReport cached = LAST_REPORT.get();
        if (cached != null) {
            return cached;
        }
        PreflightReport report = readReport();
        if (report != null) {
            LAST_REPORT.compareAndSet(null, report);
        }
        return report;
    }

    /// 供步骤 15 的“复查”使用：只读检查、不弹窗、不落盘，异步执行。
    ///
    /// @param repository 实例仓库
    /// @param versionId 实例 id
    /// @return 报告
    @NotNull
    public static CompletableFuture<PreflightReport> collect(
            @NotNull HMCLGameRepository repository,
            @NotNull String versionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var version = repository.getVersion(versionId);
                String gameVersion = repository.getGameVersion(version).orElse(null);
                LaunchContext context = new LaunchContext(
                        repository, version, versionId, null, null, gameVersion, false);
                return collect(context);
            } catch (Throwable e) {
                LOG.warning("Failed to prepare offline preflight for " + versionId, e);
                List<CheckResult> checks = new ArrayList<>();
                for (String id : List.of(PreflightChecks.ID_JAVA, PreflightChecks.ID_MEMORY,
                        PreflightChecks.ID_DISK, PreflightChecks.ID_MODS, PreflightChecks.ID_DEPS)) {
                    checks.add(CheckResult.of(id, Severity.SKIPPED, "unreadable",
                            Map.of("reason", "instance_unreadable")));
                }
                return PreflightReport.create(versionId, checks);
            }
        }, Schedulers.io());
    }

    /// 执行一次完整体检（同步、阻塞约 1s）。
    ///
    /// @param context 启动上下文
    /// @return 报告
    @NotNull
    public static PreflightReport collect(@NotNull LaunchContext context) {
        PreflightConfig config = PreflightConfig.load();
        if (!config.enabled()) {
            return PreflightReport.create(context.versionId(), skippedAll("disabled"));
        }

        Path modsDirectory;
        try {
            modsDirectory = context.repository().getModsDirectory(context.versionId());
        } catch (Throwable e) {
            modsDirectory = Path.of("").toAbsolutePath().resolve("mods");
        }
        Path finalModsDirectory = modsDirectory;

        List<CompletableFuture<CheckResult>> futures = new ArrayList<>();
        futures.add(timed(PreflightChecks.ID_JAVA, config.isCheckEnabled("java"),
                () -> PreflightChecks.checkJava(context)));
        futures.add(timed(PreflightChecks.ID_MEMORY, config.isCheckEnabled("memory"),
                () -> PreflightChecks.checkMemory(context, config.maxMemoryRatio())));
        futures.add(timed(PreflightChecks.ID_DISK, config.isCheckEnabled("disk"),
                () -> PreflightChecks.checkDisk(context, config)));

        boolean scanMods = config.isCheckEnabled("mods") || config.isCheckEnabled("deps");
        CompletableFuture<ModScanner.ScanResult> scanFuture = scanMods
                ? CompletableFuture.supplyAsync(() -> ModScanner.scanCached(finalModsDirectory), Schedulers.io())
                : CompletableFuture.completedFuture(new ModScanner.ScanResult(List.of(), 0, 0, false));

        futures.add(timed(PreflightChecks.ID_MODS, config.isCheckEnabled("mods"),
                () -> PreflightChecks.checkDuplicateMods(joinQuietly(scanFuture))));
        futures.add(timed(PreflightChecks.ID_DEPS, config.isCheckEnabled("deps"),
                () -> PreflightChecks.checkMissingDependencies(joinQuietly(scanFuture))));

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        List<CheckResult> checks = new ArrayList<>();
        for (CompletableFuture<CheckResult> future : futures) {
            try {
                checks.add(future.join());
            } catch (CompletionException e) {
                LOG.warning("Preflight check failed", e);
            }
        }
        return PreflightReport.create(context.versionId(), checks);
    }

    /// 执行体检并在交互式启动时持久化（供启动任务调用）。
    ///
    /// @param context 启动上下文
    /// @return 报告
    @NotNull
    public static PreflightReport runAndPersist(@NotNull LaunchContext context) {
        PreflightReport report = collect(context);
        if (context.interactive()) {
            LAST_REPORT.set(report);
            writeReport(report);
        }
        return report;
    }

    /// 清空缓存（测试用）。
    public static void clearCache() {
        LAST_REPORT.set(null);
    }

    /// 以软超时执行单项检查。
    private static CompletableFuture<CheckResult> timed(
            @NotNull String id,
            boolean enabled,
            @NotNull Supplier<CheckResult> supplier) {
        if (!enabled) {
            return CompletableFuture.completedFuture(
                    CheckResult.of(id, Severity.SKIPPED, "disabled", Map.of()));
        }
        CompletableFuture<CheckResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Throwable e) {
                LOG.warning("Preflight check " + id + " failed", e);
                return CheckResult.of(id, Severity.SKIPPED, "unreadable", Map.of("reason", "exception"));
            }
        }, Schedulers.io());
        return future.completeOnTimeout(
                CheckResult.of(id, Severity.SKIPPED, "timeout", Map.of("timeoutMillis", Long.toString(CHECK_TIMEOUT_MILLIS))),
                CHECK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    /// 等待扫描结果；异常时返回空结果（依赖检查会因 unparseableRatio 0 而误判，但扫描本身很少失败）。
    private static ModScanner.ScanResult joinQuietly(@NotNull CompletableFuture<ModScanner.ScanResult> future) {
        try {
            return future.join();
        } catch (Throwable e) {
            LOG.warning("Failed to scan mods", e);
            return new ModScanner.ScanResult(List.of(), 0, 0, false);
        }
    }

    /// 生成全 SKIPPED 报告。
    private static List<CheckResult> skippedAll(@NotNull String code) {
        List<CheckResult> checks = new ArrayList<>();
        for (String id : List.of(PreflightChecks.ID_JAVA, PreflightChecks.ID_MEMORY,
                PreflightChecks.ID_DISK, PreflightChecks.ID_MODS, PreflightChecks.ID_DEPS)) {
            checks.add(CheckResult.of(id, Severity.SKIPPED, code, Map.of()));
        }
        return checks;
    }

    /// 读取落盘报告。
    private static @Nullable PreflightReport readReport() {
        try {
            if (!Files.isRegularFile(REPORT_FILE)) {
                return null;
            }
            String json = Files.readString(REPORT_FILE, StandardCharsets.UTF_8);
            JsonObject object = JsonUtils.GSON.fromJson(json, JsonObject.class);
            return object == null ? null : PreflightReport.fromJson(object);
        } catch (Throwable e) {
            LOG.warning("Failed to read preflight report " + REPORT_FILE, e);
            return null;
        }
    }

    /// 原子写入落盘报告；失败只记录日志。
    private static void writeReport(@NotNull PreflightReport report) {
        try {
            Path parent = REPORT_FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = REPORT_FILE.resolveSibling(REPORT_FILE.getFileName() + ".tmp");
            Files.writeString(temp, JsonUtils.GSON.toJson(report.toJson()), StandardCharsets.UTF_8);
            try {
                Files.move(temp, REPORT_FILE,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, REPORT_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable e) {
            LOG.warning("Failed to write preflight report " + REPORT_FILE, e);
        }
    }

    /// 构建详情表（供未来扩展使用）。
    ///
    /// @param key 键
    /// @param value 值
    /// @return 详情表
    @NotNull
    static Map<String, String> details(@NotNull String key, @NotNull String value) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}
