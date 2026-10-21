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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [CrashAnalysis] 崩溃文本选择与规则/关键词映射测试。
@NotNullByDefault
public final class CrashAnalysisTest {

    @TempDir
    Path temp;

    /// 无运行目录内容时返回 `null`。
    @Test
    public void analyzeEmptyDirectory() {
        assertNull(CrashAnalysis.analyze(temp));
    }

    /// 崩溃报告命中单条规则时给出对应 i18n key。
    @Test
    public void analyzeCrashReportRule() throws Exception {
        Path reports = temp.resolve("crash-reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("crash-2026-10-21_09.00.00-client.txt"), """
                ---- Minecraft Crash Report ----
                // Oh dear

                java.lang.OutOfMemoryError: Java heap space
                """);

        CrashAnalysis.Analysis analysis = CrashAnalysis.analyze(temp);
        assertNotNull(analysis);
        assertEquals("OUT_OF_MEMORY", analysis.rules().get(0));
        assertEquals("game.crash.reason.out_of_memory", analysis.summary());
    }

    /// 只有 latest.log 时从日志提取；无规则时走关键词/未知兜底。
    @Test
    public void analyzeLatestLog() throws Exception {
        Path logs = temp.resolve("logs");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve("latest.log"), """
                2026-10-21 09:00:00 [main/INFO] Launching game
                2026-10-21 09:00:01 [main/ERROR] ---- Minecraft Crash Report ----
                java.lang.OutOfMemoryError: Java heap space
                2026-10-21 09:00:02 [main/ERROR] #@!@# Game crashed! Crash report saved to: #@!@# /nonexistent/crash.txt
                """);

        CrashAnalysis.Analysis analysis = CrashAnalysis.analyze(temp);
        assertNotNull(analysis);
        assertFalse(analysis.rules().isEmpty());
        assertTrue(analysis.summary().startsWith("game.crash.reason."));
    }

    /// 阶段解析宽松：未知值回退 STARTUP。
    @Test
    public void launchPhaseParse() {
        assertEquals(LaunchPhase.RUNTIME, LaunchPhase.parse("runtime"));
        assertEquals(LaunchPhase.RUNTIME, LaunchPhase.parse("RUNTIME"));
        assertEquals(LaunchPhase.STARTUP, LaunchPhase.parse("startup"));
        assertEquals(LaunchPhase.STARTUP, LaunchPhase.parse("nonsense"));
        assertEquals(LaunchPhase.STARTUP, LaunchPhase.parse(""));
    }

    /// 截断：大文件保留头尾并带标记；缺失文件返回 `null`。
    @Test
    public void truncateLargeFile() throws Exception {
        Path file = temp.resolve("big.log");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < (5 << 20); i++) {
            builder.append('a');
        }
        Files.writeString(file, builder.toString());

        String text = CrashArchiver.readTextTruncated(file, 1 << 20);
        assertNotNull(text);
        assertTrue(text.contains("[truncated]"));
        assertTrue(text.length() > (4 << 20));

        assertNull(CrashArchiver.readTextTruncated(temp.resolve("missing.log"), 1024));
    }
}
