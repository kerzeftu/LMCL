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

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [JvmWizardStore] 的读写/清理测试。
@NotNullByDefault
public final class JvmWizardStoreTest {

    /// 临时目录。
    @TempDir
    public Path temp;

    /// 独立的设置实例。
    private CommunitySettings settings;

    /// 每个用例使用一份全新的设置文件。
    @BeforeEach
    public void setUp() {
        settings = new CommunitySettings(temp.resolve("community-settings.json"));
    }

    /// 写入后可按字段读回，并落盘。
    @Test
    public void saveAndLoad() throws Exception {
        JvmWizardStore.State state = new JvmWizardStore.State(
                JvmPreset.G1, 4096, "-Xms4096m -Xmx4096m", "2026-10-21T00:00:00Z");
        JvmWizardStore.save(settings, "1.20.1", state);

        JvmWizardStore.State loaded = JvmWizardStore.load(settings, "1.20.1");
        assertNotNull(loaded);
        assertEquals(JvmPreset.G1, loaded.mode());
        assertEquals(4096, loaded.heapMb());
        assertEquals("-Xms4096m -Xmx4096m", loaded.generated());
        assertEquals("2026-10-21T00:00:00Z", loaded.generatedAt());

        assertTrue(Files.isRegularFile(temp.resolve("community-settings.json")));
    }

    /// 缺失/损坏条目返回 `null`。
    @Test
    public void invalidEntryReturnsNull() throws Exception {
        assertNull(JvmWizardStore.load(settings, "missing"));

        JsonObject section = new JsonObject();
        JsonObject broken = new JsonObject();
        broken.addProperty("mode", "g1");
        section.add("broken", broken);
        settings.putSection(JvmWizardStore.SECTION, section);
        assertNull(JvmWizardStore.load(settings, "broken"));
    }

    /// 删除条目。
    @Test
    public void removeEntry() {
        JvmWizardStore.save(settings, "1.20.1", new JvmWizardStore.State(
                JvmPreset.ZGC, 12288, "-Xms12288m -Xmx12288m -XX:+UseZGC", null));
        JvmWizardStore.remove(settings, "1.20.1");
        assertNull(JvmWizardStore.load(settings, "1.20.1"));
    }

    /// 清理不存在的实例，保留仍存在的条目。
    @Test
    public void prune() {
        JvmWizardStore.save(settings, "a", new JvmWizardStore.State(JvmPreset.G1, 4096, "-Xms4096m -Xmx4096m", null));
        JvmWizardStore.save(settings, "b", new JvmWizardStore.State(JvmPreset.G1, 2048, "-Xms2048m -Xmx2048m", null));
        JvmWizardStore.prune(settings, List.of("a"));
        assertNotNull(JvmWizardStore.load(settings, "a"));
        assertNull(JvmWizardStore.load(settings, "b"));
    }

    /// 未知档位回退到跟随上游。
    @Test
    public void unknownModeFallsBackToUpstream() {
        JsonObject entry = new JsonObject();
        entry.addProperty("mode", "turbo");
        entry.addProperty("generated", "-Xmx1g");
        JsonObject section = new JsonObject();
        section.add("x", entry);
        settings.putSection(JvmWizardStore.SECTION, section);
        JvmWizardStore.State state = JvmWizardStore.load(settings, "x");
        assertNotNull(state);
        assertEquals(JvmPreset.UPSTREAM, state.mode());
        assertEquals(JvmWizardStore.DEFAULT_HEAP_MB, state.heapMb());
    }

    /// loadAll 只返回可解析条目。
    @Test
    public void loadAll() {
        JvmWizardStore.save(settings, "a", new JvmWizardStore.State(JvmPreset.G1, 4096, "-Xms4096m -Xmx4096m", null));
        JsonObject section = settings.section(JvmWizardStore.SECTION);
        JsonObject broken = new JsonObject();
        broken.addProperty("mode", "g1");
        section.add("broken", broken);
        settings.putSection(JvmWizardStore.SECTION, section);

        assertEquals(Set.of("a"), JvmWizardStore.loadAll(settings).keySet());
        assertFalse(JvmWizardStore.loadAll(settings).isEmpty());
    }
}
