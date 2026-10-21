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

import org.jackhuang.hmcl.game.CrashReportAnalyzer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 离线原因摘录：复用上游 [CrashReportAnalyzer] 规则，只存规则名与 i18n key。
///
/// 与上游 `GameCrashWindow.analyzeCrashReport()` 的判定保持一致；上游改规则时这里无需改文案。
@NotNullByDefault
public final class CrashAnalysis {

    private static final long MAX_ANALYZE_BYTES = 4L << 20;

    private CrashAnalysis() {
    }

    /// 原因分析结果。
    ///
    /// @param rules 命中的规则名
    /// @param keywords 关键词兜底
    /// @param summary i18n key
    public record Analysis(@NotNull List<String> rules,
                           @NotNull List<String> keywords,
                           @NotNull String summary) {
    }

    /// 分析实例运行目录下的最新崩溃报告或日志。
    ///
    /// @param runDir 实例运行目录
    /// @return 分析结果；无可用文本或异常时返回 `null`
    @Nullable
    public static Analysis analyze(@NotNull Path runDir) {
        try {
            String text = readLatestCrashReport(runDir.resolve("crash-reports"));
            if (text == null) {
                text = readLogAndExtract(runDir.resolve("logs/latest.log"));
            }
            if (text == null || text.isBlank()) {
                return null;
            }

            Set<CrashReportAnalyzer.Result> results = CrashReportAnalyzer.analyze(text);
            List<String> rules = new ArrayList<>(results.size());
            results.stream()
                    .map(result -> result.rule().name())
                    .sorted()
                    .forEach(rules::add);

            if (rules.size() == 1) {
                return new Analysis(List.copyOf(rules), List.of(),
                        "game.crash.reason." + rules.get(0).toLowerCase(Locale.ROOT));
            }
            if (rules.size() > 1) {
                return new Analysis(List.copyOf(rules), List.of(), "game.crash.reason.multiple");
            }

            List<String> keywords = CrashReportAnalyzer.findKeywordsFromCrashReport(text).stream()
                    .sorted()
                    .toList();
            return new Analysis(List.of(), keywords,
                    keywords.isEmpty() ? "game.crash.reason.unknown" : "game.crash.reason.stacktrace");
        } catch (Throwable e) {
            LOG.warning("Failed to analyze crash report", e);
            return null;
        }
    }

    @Nullable
    private static String readLatestCrashReport(Path directory) {
        if (!Files.isDirectory(directory)) {
            return null;
        }
        try (var stream = Files.list(directory)) {
            Path latest = stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".txt"))
                    .max(Comparator.comparingLong(CrashAnalysis::lastModified))
                    .orElse(null);
            return latest == null ? null : CrashArchiver.readTextTruncated(latest, MAX_ANALYZE_BYTES);
        } catch (Throwable e) {
            LOG.warning("Failed to read crash reports directory", e);
            return null;
        }
    }

    @Nullable
    private static String readLogAndExtract(Path logFile) {
        if (!Files.isRegularFile(logFile)) {
            return null;
        }
        String log = CrashArchiver.readTextTruncated(logFile, CrashArchiver.MAX_FILE_BYTES);
        if (log == null) {
            return null;
        }
        try {
            String report = CrashReportAnalyzer.findCrashReport(log);
            if (report != null) {
                return report;
            }
        } catch (Throwable e) {
            LOG.warning("Failed to locate crash report from log", e);
        }
        return CrashReportAnalyzer.extractCrashReport(log);
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Throwable e) {
            return 0;
        }
    }
}
