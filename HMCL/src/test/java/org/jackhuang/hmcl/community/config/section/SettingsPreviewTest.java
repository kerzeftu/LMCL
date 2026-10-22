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
package org.jackhuang.hmcl.community.config.section;

import org.jackhuang.hmcl.community.config.SettingsChange;
import org.jackhuang.hmcl.community.download.CommunityDownloadSource;
import org.jackhuang.hmcl.community.download.CommunityDownloadSources;
import org.jackhuang.hmcl.setting.DefaultIsolationType;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.GameSettingsPresetID;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 导入预览的可读变更摘要测试。
public final class SettingsPreviewTest {

    private static CommunityDownloadSource source(String name, String url) {
        return CommunityDownloadSource.of(name, url);
    }

    @Test
    public void defaultIsolationIsAlways() {
        GameSettings.Preset preset = new GameSettings.Preset(
                GameSettingsPresetID.parse("game-settings-preset:123e4567-e89b-12d3-a456-426614174000"));

        assertEquals(DefaultIsolationType.ALWAYS, preset.defaultIsolationTypeProperty().getValue());
    }

    @Test
    public void downloadPreviewListsAddedUrlsOnePerLine() {
        CommunityDownloadSources current = new CommunityDownloadSources(List.of(
                source("Alpha", "https://alpha.example.com"),
                source("Beta", "https://beta.example.com")));
        CommunityDownloadSources incoming = new CommunityDownloadSources(List.of(
                source("Alpha", "https://alpha.example.com"),
                source("Gamma", "https://gamma.example.com")));

        List<SettingsChange> changes = DownloadSourcesSectionExporter.describeChanges(current, incoming);

        assertEquals(1, changes.size());
        SettingsChange change = changes.get(0);
        assertEquals(DownloadSourcesSectionExporter.ID, change.section());
        assertTrue(change.newValue().startsWith(i18n("community.settings.transfer.preview.download.added", 1)));
        assertTrue(change.newValue().contains("\nhttps://gamma.example.com"));
        assertTrue(change.newValue().contains("\nhttps://beta.example.com"));
        assertFalse(change.newValue().contains("\"schema\""));
    }

    @Test
    public void downloadPreviewDescribesRenamedSources() {
        CommunityDownloadSources current = new CommunityDownloadSources(List.of(
                source("Alpha", "https://alpha.example.com")));
        CommunityDownloadSources incoming = new CommunityDownloadSources(List.of(
                source("Alpha 2", "https://alpha.example.com")));

        List<SettingsChange> changes = DownloadSourcesSectionExporter.describeChanges(current, incoming);

        assertEquals(1, changes.size());
        assertTrue(changes.get(0).newValue().contains(
                i18n("community.settings.transfer.preview.download.rename_line",
                        "https://alpha.example.com", "Alpha", "Alpha 2")));
    }

    @Test
    public void downloadPreviewDescribesReordering() {
        CommunityDownloadSources current = new CommunityDownloadSources(List.of(
                source("Alpha", "https://alpha.example.com"),
                source("Beta", "https://beta.example.com")));
        CommunityDownloadSources incoming = new CommunityDownloadSources(List.of(
                source("Beta", "https://beta.example.com"),
                source("Alpha", "https://alpha.example.com")));

        List<SettingsChange> changes = DownloadSourcesSectionExporter.describeChanges(current, incoming);

        assertEquals(1, changes.size());
        String text = changes.get(0).newValue();
        assertTrue(text.startsWith(i18n("community.settings.transfer.preview.download.reordered")));
        assertTrue(text.contains("1. https://beta.example.com"));
        assertTrue(text.contains("2. https://alpha.example.com"));
    }

    @Test
    public void downloadPreviewIsEmptyWithoutChanges() {
        CommunityDownloadSources sources = new CommunityDownloadSources(List.of(
                source("Alpha", "https://alpha.example.com")));

        assertTrue(DownloadSourcesSectionExporter.describeChanges(sources, sources).isEmpty());
    }

    @Test
    public void settingsChangeKeepsOptionalLabel() {
        SettingsChange plain = new SettingsChange("section", "key", null, "value");
        SettingsChange labeled = new SettingsChange("section", "key", null, "value", "Title");

        assertNull(plain.label());
        assertEquals("Title", labeled.label());
    }
}
