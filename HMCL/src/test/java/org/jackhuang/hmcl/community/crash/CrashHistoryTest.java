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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [CrashHistory] 索引读写、去重、上限与损坏恢复测试。
@NotNullByDefault
public final class CrashHistoryTest {

    @TempDir
    Path temp;

    @AfterEach
    public void resetRoot() {
        CrashHistory.setRootForTesting(null);
    }

    private static CrashMeta meta(String versionId, @Nullable String rule, Instant at) {
        return new CrashMeta(versionId, "1.20.1", LaunchPhase.STARTUP, true,
                "game.crash.reason.unknown", rule != null ? List.of(rule) : List.of(), List.of(),
                null, false, at);
    }

    private Path append(String id, CrashMeta meta) throws IOException {
        Path dir = CrashHistory.crashRoot().resolve(id);
        Files.createDirectories(dir);
        CrashHistory.append(dir, meta);
        return dir;
    }

    /// 追加后可读回，字段保持一致，未读数自增。
    @Test
    public void appendAndLoad() throws IOException {
        CrashHistory.setRootForTesting(temp);
        append("crash-a", meta("v1", "OUT_OF_MEMORY", Instant.parse("2026-10-21T09:00:00Z")));

        List<CrashEntry> entries = CrashHistory.load();
        assertEquals(1, entries.size());
        CrashEntry entry = entries.get(0);
        assertEquals("crash-a", entry.archiveId());
        assertEquals("v1", entry.versionId());
        assertEquals("1.20.1", entry.gameVersion());
        assertEquals(LaunchPhase.STARTUP, entry.phase());
        assertEquals("OUT_OF_MEMORY", entry.reasonRule());
        assertEquals(1, CrashHistory.unseenCount());
        assertNotNull(CrashHistory.find("crash-a"));
        assertNull(CrashHistory.find("missing"));
    }

    /// 同 id 重复追加只保留一条并使用新的元数据。
    @Test
    public void appendReplacesDuplicate() throws IOException {
        CrashHistory.setRootForTesting(temp);
        append("crash-a", meta("v1", null, Instant.now()));
        append("crash-a", new CrashMeta("v2", "1.21", LaunchPhase.RUNTIME, false,
                "game.crash.reason.unknown", List.of(), List.of(), null, false, Instant.now()));

        List<CrashEntry> entries = CrashHistory.load();
        assertEquals(1, entries.size());
        assertEquals("v2", entries.get(0).versionId());
        assertEquals(LaunchPhase.RUNTIME, entries.get(0).phase());
    }

    /// 最多保留 20 条，新的在前。
    @Test
    public void appendCapsAtMaxEntries() throws IOException {
        CrashHistory.setRootForTesting(temp);
        for (int i = 0; i < 25; i++) {
            append("crash-" + i, meta("v" + i, null, Instant.now().plusSeconds(i)));
        }

        List<CrashEntry> entries = CrashHistory.load();
        assertEquals(CrashHistory.MAX_ENTRIES, entries.size());
        assertEquals("crash-24", entries.get(0).archiveId());
        assertEquals("crash-5", entries.get(entries.size() - 1).archiveId());
    }

    /// 索引损坏时备份为 `.bak-*` 并返回空列表。
    @Test
    public void corruptIndexIsBackedUp() throws IOException {
        CrashHistory.setRootForTesting(temp);
        Path index = CrashHistory.indexPath();
        Files.createDirectories(index.getParent());
        Files.writeString(index, "{broken-json");

        assertTrue(CrashHistory.load().isEmpty());
        assertFalse(Files.exists(index));
        try (var stream = Files.list(index.getParent())) {
            assertTrue(stream.anyMatch(path -> path.getFileName().toString().contains(".bak-")));
        }
    }

    /// 删除条目同时删除归档目录。
    @Test
    public void deleteRemovesDirectoryAndEntry() throws IOException {
        CrashHistory.setRootForTesting(temp);
        Path dir = append("crash-a", meta("v1", null, Instant.now()));

        assertTrue(CrashHistory.delete("crash-a"));
        assertFalse(Files.exists(dir));
        assertTrue(CrashHistory.load().isEmpty());
    }

    /// 未读数可清零。
    @Test
    public void markAllSeenResetsCounter() throws IOException {
        CrashHistory.setRootForTesting(temp);
        append("crash-a", meta("v1", null, Instant.now()));
        append("crash-b", meta("v1", null, Instant.now()));
        assertEquals(2, CrashHistory.unseenCount());

        CrashHistory.markAllSeen();
        assertEquals(0, CrashHistory.unseenCount());
    }
}
