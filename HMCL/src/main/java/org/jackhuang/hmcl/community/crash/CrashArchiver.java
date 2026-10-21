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
package org.jackhuang.hmcl.community.crash;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.community.launch.CommunityLaunchHooks.LaunchSession;
import org.jackhuang.hmcl.community.launch.LaunchContext;
import org.jackhuang.hmcl.community.launch.LaunchEvent;
import org.jackhuang.hmcl.community.launch.LaunchTracker;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.logging.Logger;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 崩溃现场归档器（步骤 09 §3.3）。
///
/// 设计约束：所有失败只记日志，绝不影响启动/游戏/上游崩溃窗；归档在 IO 线程异步执行。
@NotNullByDefault
public final class CrashArchiver {

    /// 单个归档文件上限：8 MiB。
    public static final long MAX_FILE_BYTES = 8L << 20;

    private static final long HEAD_BYTES = 2L << 20;
    private static final long TAIL_BYTES = 2L << 20;
    private static final long MAX_HMCL_LOG_BYTES = 1L << 20;
    private static final int MAX_MOD_FILES = 500;
    private static final int MAX_ARCHIVES = 10;
    private static final int MIN_ARCHIVES = 3;
    private static final long MAX_ARCHIVE_AGE_DAYS = 90;
    private static final long MAX_TOTAL_BYTES = 200L << 20;

    private static final DateTimeFormatter DIR_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private CrashArchiver() {
    }

    /// 异步归档一次异常退出。
    ///
    /// @param session 启动会话快照
    /// @param event 退出事件
    /// @param survived 是否已发出过存活阈值事件
    public static void archiveAsync(@NotNull LaunchSession session, @NotNull LaunchEvent event, boolean survived) {
        CompletableFuture.runAsync(() -> archive(session, event, survived), Schedulers.io())
                .exceptionally(throwable -> {
                    LOG.warning("Community crash archive task failed", throwable);
                    return null;
                });
    }

    /// 异步补档（启动器被强杀后由下次启动的 `PREVIOUS_ABNORMAL` 触发）。
    ///
    /// @param session 启动会话快照
    /// @param versionId 实例 id
    public static void archiveRecoveredAsync(@NotNull LaunchSession session, @NotNull String versionId) {
        CompletableFuture.runAsync(() -> archiveRecovered(session, versionId), Schedulers.io())
                .exceptionally(throwable -> {
                    LOG.warning("Community recovered crash archive task failed", throwable);
                    return null;
                });
    }

    private static void archive(LaunchSession session, LaunchEvent event, boolean survived) {
        Instant at = Instant.now();
        try {
            LaunchContext context = session.context();
            String versionId = event.versionId();
            String gameVersion = event.gameVersion() != null
                    ? event.gameVersion()
                    : resolveGameVersion(session, context);
            long aliveMillis = event.alive() != null
                    ? event.alive().toMillis()
                    : Math.max(0, Duration.between(session.startedAt(), at).toMillis());
            int threshold = LaunchTracker.loadDefaultThreshold();
            boolean quickCrash = !survived && aliveMillis < threshold * 1000L;

            Path runDir = runDir(session, context);
            CrashAnalysis.Analysis analysis = runDir != null ? CrashAnalysis.analyze(runDir) : null;

            Path dir = nextArchiveDir(at, null);
            Files.createDirectories(dir);

            JsonObject meta = buildMeta(
                    dir.getFileName().toString(), at, versionId, gameVersion, event.exitCode(),
                    event.exitType() != null ? event.exitType().name() : "UNKNOWN",
                    aliveMillis / 1000, quickCrash, analysis, session, survived, false, threshold);
            writeMeta(dir, meta);

            copyGameLogs(runDir, dir);
            writeCommand(session.process(), dir);
            writeHmclLog(dir);
            writeMods(session, context, dir);

            CrashHistory.append(dir, new CrashMeta(
                    versionId,
                    gameVersion,
                    survived ? LaunchPhase.RUNTIME : LaunchPhase.STARTUP,
                    quickCrash,
                    analysis != null ? analysis.summary() : "game.crash.reason.unknown",
                    analysis != null ? analysis.rules() : List.of(),
                    analysis != null ? analysis.keywords() : List.of(),
                    null,
                    false,
                    at));

            prune();
        } catch (Throwable e) {
            LOG.warning("Community crash archive failed", e);
        }
    }

    private static void archiveRecovered(LaunchSession session, String versionId) {
        Instant at = Instant.now();
        try {
            // 24h 内已有该实例的归档则不重复补档。
            for (CrashEntry entry : CrashHistory.load()) {
                if (entry.versionId().equals(versionId) && entry.at().isAfter(at.minus(Duration.ofHours(24)))) {
                    return;
                }
            }

            LaunchContext context = session.context();
            String gameVersion = resolveGameVersion(session, context);
            Path runDir = runDir(session, context);
            Path dir = nextArchiveDir(at, "recovered");
            Files.createDirectories(dir);

            JsonObject meta = buildMeta(
                    dir.getFileName().toString(), at, versionId, gameVersion, -1, "UNKNOWN",
                    0, false, null, session, false, true, LaunchTracker.loadDefaultThreshold());
            writeMeta(dir, meta);
            copyGameLogs(runDir, dir);
            writeHmclLog(dir);

            CrashHistory.append(dir, new CrashMeta(
                    versionId, gameVersion, LaunchPhase.STARTUP, false,
                    "community.crash.recovered", List.of(), List.of(), null, false, at));
            prune();
        } catch (Throwable e) {
            LOG.warning("Community recovered crash archive failed", e);
        }
    }

    private static JsonObject buildMeta(String archiveId,
                                        Instant at,
                                        String versionId,
                                        String gameVersion,
                                        int exitCode,
                                        String exitType,
                                        long aliveSeconds,
                                        boolean quickCrash,
                                        @Nullable CrashAnalysis.Analysis analysis,
                                        LaunchSession session,
                                        boolean survived,
                                        boolean recovered,
                                        int thresholdSeconds) {
        JsonObject meta = new JsonObject();
        meta.addProperty("schema", 1);
        meta.addProperty("archiveId", archiveId);
        meta.addProperty("createdAt", DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(at.atZone(ZoneId.systemDefault())));
        meta.addProperty("versionId", versionId);
        meta.addProperty("gameVersion", gameVersion);
        meta.addProperty("launcherVersion", Metadata.VERSION);
        meta.addProperty("exitCode", exitCode);
        meta.addProperty("exitType", exitType);
        meta.addProperty("aliveSeconds", aliveSeconds);
        meta.addProperty("quickCrash", quickCrash);
        meta.addProperty("recovered", recovered);
        meta.addProperty("survived", survived);
        meta.addProperty("survivedThreshold", thresholdSeconds);

        LaunchContext context = session.context();
        JavaRuntime java = context != null ? context.java() : null;
        if (java != null) {
            JsonObject javaJson = new JsonObject();
            javaJson.addProperty("version", java.getVersion());
            javaJson.addProperty("vendor", java.getVendor());
            javaJson.addProperty("path", java.getBinary().toString());
            meta.add("java", javaJson);
        }

        JsonObject memory = new JsonObject();
        GameSettings.Effective setting = context != null ? context.setting() : null;
        if (setting != null) {
            Integer min = setting.getInheritable(GameSettings::minMemoryProperty);
            memory.addProperty("minMb", min != null ? min : 0);
            memory.addProperty("maxMb", setting.getMaxMemory());
        }
        long physical = SystemInfo.getTotalMemorySize();
        if (physical > 0) {
            memory.addProperty("physicalMb", physical / (1024 * 1024));
        }
        if (memory.size() > 0) {
            meta.add("memory", memory);
        }

        JsonObject os = new JsonObject();
        os.addProperty("name", System.getProperty("os.name", ""));
        os.addProperty("version", System.getProperty("os.version", ""));
        os.addProperty("arch", System.getProperty("os.arch", ""));
        meta.add("os", os);

        int modCount = countMods(session, context);
        meta.addProperty("modCount", modCount);

        if (analysis != null) {
            JsonObject analysisJson = new JsonObject();
            JsonArray rules = new JsonArray();
            analysis.rules().forEach(rules::add);
            analysisJson.add("rules", rules);
            JsonArray keywords = new JsonArray();
            analysis.keywords().forEach(keywords::add);
            analysisJson.add("keywords", keywords);
            analysisJson.addProperty("summary", analysis.summary());
            meta.add("analysis", analysisJson);
        }

        return meta;
    }

    private static void writeMeta(Path dir, JsonObject meta) throws IOException {
        Files.writeString(dir.resolve("meta.json"), JsonUtils.GSON.toJson(meta), StandardCharsets.UTF_8);
    }

    private static void copyGameLogs(@Nullable Path runDir, Path dir) {
        if (runDir == null) {
            return;
        }
        copyFiltered(runDir.resolve("logs/latest.log"), dir.resolve("minecraft.log"), MAX_FILE_BYTES);

        Path reports = runDir.resolve("crash-reports");
        if (!Files.isDirectory(reports)) {
            return;
        }
        try (var stream = Files.list(reports)) {
            Path latest = stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".txt"))
                    .max(Comparator.comparingLong(CrashArchiver::lastModified))
                    .orElse(null);
            if (latest != null) {
                copyFiltered(latest, dir.resolve("crash-report.txt"), MAX_FILE_BYTES);
            }
        } catch (Throwable e) {
            LOG.warning("Failed to copy crash report", e);
        }
    }

    private static void writeCommand(@Nullable ManagedProcess process, Path dir) {
        if (process == null) {
            return;
        }
        try {
            List<String> commands = process.getCommands();
            StringBuilder builder = new StringBuilder();
            for (String command : commands) {
                builder.append(Logger.filterForbiddenToken(command)).append('\n');
            }
            Files.writeString(dir.resolve("command.txt"), builder.toString(), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            LOG.warning("Failed to write crash command.txt", e);
        }
    }

    private static void writeHmclLog(Path dir) {
        try {
            String logs = LOG.getLogs();
            if (logs == null) {
                return;
            }
            String tail = tail(logs, MAX_HMCL_LOG_BYTES);
            Files.writeString(dir.resolve("hmcl.log"), Logger.filterForbiddenToken(tail), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            LOG.warning("Failed to write crash hmcl.log", e);
        }
    }

    private static void writeMods(LaunchSession session, @Nullable LaunchContext context, Path dir) {
        Path modsDir = modsDirectory(session, context);
        if (modsDir == null || !Files.isDirectory(modsDir)) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        try (var stream = Files.list(modsDir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(CrashArchiver::isModFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit(MAX_MOD_FILES)
                    .toList();
            for (Path file : files) {
                builder.append(file.getFileName()).append('\t').append(sha1(file)).append('\n');
            }
            Files.writeString(dir.resolve("mods.txt"), builder.toString(), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            LOG.warning("Failed to write crash mods.txt", e);
        }
    }

    private static int countMods(LaunchSession session, @Nullable LaunchContext context) {
        Path modsDir = modsDirectory(session, context);
        if (modsDir == null || !Files.isDirectory(modsDir)) {
            return 0;
        }
        try (var stream = Files.list(modsDir)) {
            return (int) stream.filter(Files::isRegularFile).filter(CrashArchiver::isModFile).count();
        } catch (Throwable e) {
            return 0;
        }
    }

    private static boolean isModFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".disabled") || name.endsWith(".old")) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        return name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".litemod");
    }

    private static String sha1(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            StringBuilder builder = new StringBuilder();
            for (byte b : digest.digest()) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Throwable e) {
            return "unavailable";
        }
    }

    @Nullable
    private static Path modsDirectory(LaunchSession session, @Nullable LaunchContext context) {
        try {
            return context != null && context.version() != null
                    ? session.repository().getModsDirectory(context.versionId())
                    : null;
        } catch (Throwable e) {
            return null;
        }
    }

    @Nullable
    private static Path runDir(LaunchSession session, @Nullable LaunchContext context) {
        try {
            return context != null && context.version() != null
                    ? session.repository().getRunDirectory(context.versionId())
                    : null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static String resolveGameVersion(LaunchSession session, @Nullable LaunchContext context) {
        try {
            if (context != null && context.gameVersion() != null) {
                return context.gameVersion();
            }
            if (context != null) {
                Version version = context.version();
                return session.repository().getGameVersion(version).orElse("");
            }
        } catch (Throwable e) {
            LOG.warning("Failed to resolve game version for crash archive", e);
        }
        return "";
    }

    private static void copyFiltered(Path source, Path target, long maxBytes) {
        if (!Files.isRegularFile(source)) {
            return;
        }
        try {
            String text = readTextTruncated(source, maxBytes);
            if (text != null) {
                Files.writeString(target, Logger.filterForbiddenToken(text), StandardCharsets.UTF_8);
            }
        } catch (Throwable e) {
            LOG.warning("Failed to copy crash file " + source, e);
        }
    }

    /// 读取文本；超过上限时保留头 2 MiB + 尾 2 MiB，中间插入截断标记。
    ///
    /// @param file 文件
    /// @param maxBytes 上限（<=0 表示不限制）
    /// @return 文本或 `null`
    @Nullable
    public static String readTextTruncated(@NotNull Path file, long maxBytes) {
        try {
            long size = Files.size(file);
            if (maxBytes <= 0 || size <= maxBytes) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }

            long head = Math.min(HEAD_BYTES, size);
            long tail = Math.min(TAIL_BYTES, size - head);
            byte[] headBytes = new byte[(int) head];
            byte[] tailBytes = new byte[(int) tail];
            try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
                ByteBuffer headBuffer = ByteBuffer.wrap(headBytes);
                channel.read(headBuffer);
                channel.position(size - tail);
                ByteBuffer tailBuffer = ByteBuffer.wrap(tailBytes);
                while (tailBuffer.hasRemaining() && channel.read(tailBuffer) > 0) {
                    // keep reading
                }
            }
            return new String(headBytes, StandardCharsets.UTF_8)
                    + "\n...[truncated]...\n"
                    + new String(tailBytes, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            LOG.warning("Failed to read crash file " + file, e);
            return null;
        }
    }

    private static String tail(String text, long maxBytes) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        int offset = (int) (bytes.length - maxBytes);
        return "...[truncated]...\n" + new String(bytes, offset, (int) maxBytes, StandardCharsets.UTF_8);
    }

    private static Path nextArchiveDir(Instant at, @Nullable String suffix) throws IOException {
        Path root = CrashHistory.crashRoot();
        Files.createDirectories(root);
        String base = "crash-" + DIR_TIME.format(at.atZone(ZoneId.systemDefault())) + "-"
                + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000000) | 0x1000000).substring(1)
                + (suffix != null ? "-" + suffix : "");
        Path candidate = root.resolve(base);
        int index = 2;
        while (Files.exists(candidate) && index < 10) {
            candidate = root.resolve(base + "-" + index);
            index++;
        }
        return candidate;
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Throwable e) {
            return 0;
        }
    }

    /// 清理旧归档：份数、天数、总配额三重上限；从最旧开始删，至少保留 3 份。
    public static void prune() {
        Path root = CrashHistory.crashRoot();
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.list(root)) {
            List<Path> dirs = stream
                    .filter(path -> Files.isDirectory(path) && path.getFileName().toString().startsWith("crash-"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

            Instant expireBefore = Instant.now().minus(Duration.ofDays(MAX_ARCHIVE_AGE_DAYS));
            long total = 0;
            for (Path dir : dirs) {
                total += directorySize(dir);
            }

            for (Path dir : dirs) {
                if (dirs.size() <= MIN_ARCHIVES) {
                    break;
                }
                boolean tooMany = Files.exists(dir) && countArchives(root) > MAX_ARCHIVES;
                boolean tooOld = lastModified(dir) < expireBefore.toEpochMilli();
                boolean tooLarge = total > MAX_TOTAL_BYTES;
                if (!tooMany && !tooOld && !tooLarge) {
                    continue;
                }
                long size = directorySize(dir);
                CrashHistory.delete(dir.getFileName().toString());
                total -= size;
            }
        } catch (Throwable e) {
            LOG.warning("Failed to prune crash archives", e);
        }
    }

    private static long countArchives(Path root) {
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("crash-"))
                    .count();
        } catch (Throwable e) {
            return 0;
        }
    }

    private static long directorySize(Path dir) {
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (Throwable e) {
            return 0;
        }
    }

    /// 导出诊断包：把归档目录打成 zip。
    ///
    /// @param archiveDir 归档目录
    /// @param target zip 目标路径
    /// @throws IOException IO 失败
    public static void export(@NotNull Path archiveDir, @NotNull Path target) throws IOException {
        try (var zipper = new org.jackhuang.hmcl.util.io.Zipper(target)) {
            zipper.putDirectory(archiveDir, "");
        }
    }

    /// 返回归档目录；不存在时返回不存在的路径（供 UI 判断）。
    ///
    /// @param entry 历史条目
    /// @return 归档目录
    @NotNull
    public static Path archiveDir(@NotNull CrashEntry entry) {
        return CrashHistory.crashRoot().resolve(entry.dirName());
    }
}
