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
package org.jackhuang.hmcl.community.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// `.lmcl.json` 格式测试。
public final class CommunitySettingsBundleTest {

    private static CommunitySettingsBundle bundle() {
        JsonObject download = new JsonObject();
        download.addProperty("schema", 1);
        Map<String, JsonObject> map = new LinkedHashMap<>();
        map.put("downloadSources", download);
        return CommunitySettingsBundle.create(map);
    }

    @Test
    public void roundTrips() {
        CommunitySettingsBundle parsed = CommunitySettingsBundle.parse(bundle().toJson().toString());
        assertEquals(CommunitySettingsBundle.FORMAT, "lmcl.settings");
        assertEquals(1, parsed.sections().size());
        assertTrue(parsed.sections().containsKey("downloadSources"));
        assertTrue(parsed.warnings().isEmpty());
    }

    @Test
    public void rejectsWrongFormat() {
        String json = "{\"format\":\"other\",\"version\":1,\"sections\":{\"downloadSources\":{}}}";
        assertThrows(JsonParseException.class, () -> CommunitySettingsBundle.parse(json));
    }

    @Test
    public void rejectsNewerVersion() {
        String json = "{\"format\":\"lmcl.settings\",\"version\":2,\"sections\":{\"downloadSources\":{}}}";
        assertThrows(JsonParseException.class, () -> CommunitySettingsBundle.parse(json));
    }

    @Test
    public void rejectsNoSupportedSection() {
        String json = "{\"format\":\"lmcl.settings\",\"version\":1,\"sections\":{\"unknown\":{}}}";
        assertThrows(JsonParseException.class, () -> CommunitySettingsBundle.parse(json));
    }

    @Test
    public void ignoresUnknownSectionWithWarning() {
        String json = "{\"format\":\"lmcl.settings\",\"version\":1,"
                + "\"sections\":{\"downloadSources\":{},\"unknown\":{}}}";
        CommunitySettingsBundle parsed = CommunitySettingsBundle.parse(json);
        assertFalse(parsed.warnings().isEmpty());
        assertEquals(1, parsed.sections().size());
    }
}
