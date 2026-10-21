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
package org.jackhuang.hmcl.community.ui.about;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [AboutData] 的解析与资源加载测试。
public final class AboutDataTest {

    @Test
    public void parsesAllThanksEntries() {
        List<AboutData.Entry> entries = AboutData.parseThanks(JsonParser.parseString("""
                [
                  {"title":"A","subtitle":"a","externalLink":"https://example.com/a"},
                  {"titleLocalized":"about.thanks_to.b.statement","image":"/assets/img/icon.png"},
                  {"subtitle":"no title"},
                  "ignored"
                ]
                """).getAsJsonArray());

        assertEquals(2, entries.size());
        assertEquals("A", entries.get(0).title());
        assertEquals("https://example.com/a", entries.get(0).externalLink());
        assertEquals("about.thanks_to.b.statement", entries.get(1).titleLocalized());
        assertEquals("/assets/img/icon.png", entries.get(1).image().getAsString());
    }

    @Test
    public void parsesDonorsAndSkipsBlankNames() {
        List<AboutData.Donor> donors = AboutData.parseDonors(JsonParser.parseString("""
                [
                  {"name":"Alice","note":"Supporter","link":"https://example.com"},
                  {"name":"  "},
                  {"note":"missing name"},
                  "ignored"
                ]
                """).getAsJsonArray());

        assertEquals(1, donors.size());
        assertEquals("Alice", donors.get(0).name());
        assertEquals("Supporter", donors.get(0).note());
        assertEquals("https://example.com", donors.get(0).link());
    }

    @Test
    public void loadsUpstreamThanksAndEmptyDonorPlaceholder() {
        AboutData data = AboutData.load();

        assertFalse(data.thanks().isEmpty());
        assertTrue(data.thanks().stream().anyMatch(entry ->
                "yushijinhun".equals(entry.title()) || "Glavo".equals(entry.title())));
        assertTrue(data.donors().isEmpty());
    }
}
