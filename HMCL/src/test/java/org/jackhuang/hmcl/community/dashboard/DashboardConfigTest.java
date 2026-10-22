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
package org.jackhuang.hmcl.community.dashboard;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// `dashboard.json` 首次运行语义测试。
public final class DashboardConfigTest {

    @TempDir
    Path directory;

    @Test
    public void missingFileIsFirstRun() {
        DashboardConfig config = DashboardConfig.load(directory.resolve("dashboard.json"));
        assertTrue(config.firstRun());
        assertTrue(config.items().isEmpty());
    }

    @Test
    public void existingEmptyLayoutIsNotFirstRun() throws Exception {
        Path path = directory.resolve("dashboard.json");
        Files.writeString(path, "{\"schema\":1,\"items\":[],\"pinnedInstanceIds\":[],\"widgetProps\":{}}",
                StandardCharsets.UTF_8);

        DashboardConfig config = DashboardConfig.load(path);
        assertFalse(config.firstRun());
        assertTrue(config.items().isEmpty());
    }

    @Test
    public void existingLayoutIsNotFirstRun() throws Exception {
        Path path = directory.resolve("dashboard.json");
        Files.writeString(path, "{\"schema\":1,\"items\":[{\"id\":\"launch\",\"enabled\":true,"
                        + "\"order\":10,\"colSpan\":2,\"rowSpan\":1}],\"pinnedInstanceIds\":[],\"widgetProps\":{}}",
                StandardCharsets.UTF_8);

        DashboardConfig config = DashboardConfig.load(path);
        assertFalse(config.firstRun());
        assertEquals(1, config.items().size());
        assertEquals("launch", config.items().get(0).id());
    }

    @Test
    public void exportAndReplaceRoundTrip() throws Exception {
        Path path = directory.resolve("dashboard.json");
        Files.writeString(path, "{\"schema\":1,\"items\":[{\"id\":\"launch\",\"enabled\":true,"
                        + "\"order\":10,\"colSpan\":2,\"rowSpan\":1},{\"id\":\"memory\",\"enabled\":false,"
                        + "\"order\":20,\"colSpan\":1,\"rowSpan\":1}],"
                        + "\"pinnedInstanceIds\":[\"a\",\"a\",\"b\"],\"widgetProps\":{\"x\":{\"v\":1}},"
                        + "\"updatedAt\":\"2026-01-01T00:00:00Z\"}",
                StandardCharsets.UTF_8);

        DashboardConfig source = DashboardConfig.load(path);
        JsonObject exported = source.toExportJson();
        assertFalse(exported.has("updatedAt"));

        DashboardConfig target = DashboardConfig.load(directory.resolve("target.json"));
        target.replaceFromJson(exported);
        assertEquals(2, target.items().size());
        assertEquals(2, target.pinnedInstanceIds().size());
        assertEquals("a", target.pinnedInstanceIds().get(0));
        assertFalse(target.firstRun());
        target.save();

        DashboardConfig reloaded = DashboardConfig.load(directory.resolve("target.json"));
        assertEquals(2, reloaded.items().size());
        assertEquals(20, reloaded.items().get(1).order());
        assertEquals(2, reloaded.pinnedInstanceIds().size());
    }

    @Test
    public void rejectsNewerSchemaOnImport() {
        DashboardConfig config = DashboardConfig.load(directory.resolve("dashboard.json"));
        JsonObject object = new JsonObject();
        object.addProperty("schema", 2);
        assertThrows(com.google.gson.JsonParseException.class, () -> config.replaceFromJson(object));
    }

    @Test
    public void corruptFileIsFirstRun() throws Exception {
        Path path = directory.resolve("dashboard.json");
        Files.writeString(path, "{not json", StandardCharsets.UTF_8);

        DashboardConfig config = DashboardConfig.load(path);
        assertTrue(config.firstRun());
        assertTrue(config.items().isEmpty());
    }
}
