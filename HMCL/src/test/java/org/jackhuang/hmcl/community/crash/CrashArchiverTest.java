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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [CrashArchiver] 的清理与导出测试（不启动游戏、不弹 UI）。
@NotNullByDefault
public final class CrashArchiverTest {

    @TempDir
    Path temp;

    @AfterEach
    public void resetRoot() {
        CrashHistory.setRootForTesting(null);
    }

    /// 份数上限：12 份删到 10 份，从最旧开始删。
    @Test
    public void pruneKeepsAtMostTen() throws Exception {
        CrashHistory.setRootForTesting(temp);
        Path root = CrashHistory.crashRoot();
        Files.createDirectories(root);
        for (int i = 0; i < 12; i++) {
            Path dir = root.resolve(String.format("crash-20260101-%06d", i));
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("meta.json"), "{}");
        }

        CrashArchiver.prune();

        try (var stream = Files.list(root)) {
            assertEquals(10, stream.filter(Files::isDirectory).count());
        }
        assertFalse(Files.exists(root.resolve(String.format("crash-20260101-%06d", 0))));
        assertFalse(Files.exists(root.resolve(String.format("crash-20260101-%06d", 1))));
        assertTrue(Files.exists(root.resolve(String.format("crash-20260101-%06d", 11))));
    }

    /// 归档目录可导出为 zip。
    @Test
    public void exportArchive() throws Exception {
        CrashHistory.setRootForTesting(temp);
        Path dir = CrashHistory.crashRoot().resolve("crash-a");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("meta.json"), "{\"schema\":1}");
        Files.writeString(dir.resolve("minecraft.log"), "hello");

        Path zip = temp.resolve("crash-a.zip");
        CrashArchiver.export(dir, zip);

        assertTrue(Files.isRegularFile(zip));
        assertTrue(Files.size(zip) > 4);
        byte[] magic = Files.readAllBytes(zip);
        assertEquals('P', magic[0]);
        assertEquals('K', magic[1]);
    }
}
