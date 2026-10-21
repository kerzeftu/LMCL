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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [ModScanner] 与模组类检查的测试（使用临时 jar）。
@NotNullByDefault
public final class ModScannerTest {

    /// 重复 mod id 被检测为 WARN，禁用文件不参与扫描。
    @Test
    public void detectsDuplicateMods(@TempDir @NotNull Path tempDir) throws IOException {
        Path mods = Files.createDirectories(tempDir.resolve("mods"));
        writeJar(mods, "a.jar", "{\"id\":\"a\",\"depends\":{\"b\":\">=1.0\"}}");
        writeJar(mods, "b.jar", "{\"id\":\"b\"}");
        writeJar(mods, "b2.jar", "{\"id\":\"b\"}");
        writeJar(mods, "c.jar.disabled", "{\"id\":\"c\"}");

        ModScanner.ScanResult result = ModScanner.scan(mods);
        assertEquals(3, result.total());
        assertEquals(3, result.mods().size());
        assertFalse(result.mods().stream().anyMatch(mod -> mod.fileName().endsWith(".disabled")));

        CheckResult duplicate = PreflightChecks.checkDuplicateMods(result);
        assertEquals(Severity.WARN, duplicate.severity());
        assertEquals("duplicate_mods", duplicate.code());
        assertEquals("1", duplicate.details().get("count"));

        CheckResult missing = PreflightChecks.checkMissingDependencies(result);
        assertEquals(Severity.OK, missing.severity());
        assertEquals("missing_deps_none", missing.code());
    }

    /// 缺失前置被检测为 WARN。
    @Test
    public void detectsMissingDependencies(@TempDir @NotNull Path tempDir) throws IOException {
        Path mods = Files.createDirectories(tempDir.resolve("mods"));
        writeJar(mods, "a.jar", "{\"id\":\"a\",\"depends\":{\"missinglib\":\">=1\",\"minecraft\":\"*\"}}");
        writeJar(mods, "b.jar", "{\"id\":\"b\"}");

        ModScanner.ScanResult result = ModScanner.scan(mods);
        Map<String, List<String>> missing = ModScanner.missingDependencies(result);
        assertEquals(List.of("a.jar"), missing.get("missinglib"));

        CheckResult check = PreflightChecks.checkMissingDependencies(result);
        assertEquals(Severity.WARN, check.severity());
        assertEquals("missing_deps", check.code());
        assertEquals("1", check.details().get("count"));
    }

    /// 无法识别元数据的比例过高时跳过依赖检查。
    @Test
    public void skipsWhenMetadataUnparseable(@TempDir @NotNull Path tempDir) throws IOException {
        Path mods = Files.createDirectories(tempDir.resolve("mods"));
        writeJar(mods, "broken.jar", null);

        ModScanner.ScanResult result = ModScanner.scan(mods);
        assertEquals(1, result.total());
        assertEquals(1, result.unparseable());
        assertTrue(result.unparseableRatio() > ModScanner.MAX_UNPARSEABLE_RATIO);

        CheckResult check = PreflightChecks.checkMissingDependencies(result);
        assertEquals(Severity.SKIPPED, check.severity());
        assertEquals("unreadable", check.code());
    }

    /// 写入一个包含 `fabric.mod.json` 的 jar；内容为 `null` 时写空 jar。
    private static void writeJar(@NotNull Path dir, @NotNull String name, @Nullable String json) throws IOException {
        try (OutputStream out = Files.newOutputStream(dir.resolve(name));
             ZipOutputStream zip = new ZipOutputStream(out)) {
            if (json != null) {
                zip.putNextEntry(new ZipEntry("fabric.mod.json"));
                zip.write(json.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }
}
