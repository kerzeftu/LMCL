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
package org.jackhuang.hmcl.community.env;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 数据根目录计算的纯函数/探测测试（不依赖 `Metadata` 与 JavaFX）。
@NotNullByDefault
public final class CommunityPathsTest {

    /// Windows：`%APPDATA%/LMCL`。
    @Test
    public void windowsUserHome() {
        assertEquals(Path.of("C:/Users/x/AppData/Roaming/LMCL").toAbsolutePath(),
                CommunityPaths.defaultUserHome("Windows 11", "C:/Users/x",
                        "C:/Users/x/AppData/Roaming", null));
    }

    /// Linux：优先 `$XDG_DATA_HOME`，否则 `~/.local/share/LMCL`。
    @Test
    public void linuxUserHome() {
        assertEquals(Path.of("/data/LMCL").toAbsolutePath(),
                CommunityPaths.defaultUserHome("Linux", "/home/x", null, "/data"));
        assertEquals(Path.of("/home/x/.local/share/LMCL").toAbsolutePath(),
                CommunityPaths.defaultUserHome("Linux", "/home/x", null, null));
    }

    /// macOS：`~/Library/Application Support/LMCL`。
    @Test
    public void macOSUserHome() {
        assertEquals(Path.of("/Users/x/Library/Application Support/LMCL").toAbsolutePath(),
                CommunityPaths.defaultUserHome("Mac OS X", "/Users/x", null, null));
    }

    /// 可写目录探测成功。
    @Test
    public void ensureWritableCreatesDirectory(@TempDir Path temp) {
        Path dir = temp.resolve("a/b");
        assertTrue(CommunityPaths.ensureWritable(dir));
        assertTrue(Files.isDirectory(dir));
    }

    /// 只读目录探测失败（POSIX 环境）。
    @Test
    public void ensureWritableRejectsReadOnlyDirectory(@TempDir Path temp) throws Exception {
        Path dir = temp.resolve("readonly");
        Files.createDirectories(dir);
        var permissions = Files.getFileStore(dir).supportsFileAttributeView("posix");
        if (!permissions) {
            return;
        }
        Files.setPosixFilePermissions(dir, java.nio.file.attribute.PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assertFalse(CommunityPaths.ensureWritable(dir));
        } finally {
            Files.setPosixFilePermissions(dir, java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }
}
