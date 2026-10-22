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
package org.jackhuang.hmcl.community.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.download.AutoDownloadProvider;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 下载源配置的纯函数测试（不依赖 JavaFX/磁盘）。
@NotNullByDefault
public final class CommunityDownloadSourcesTest {

    /// 地址规范化：补协议、去尾斜杠、去空白。
    @Test
    public void normalizesUrls() {
        assertEquals("https://example.com", CommunityDownloadSource.normalizeUrl(" example.com/ "));
        assertEquals("http://example.com/api", CommunityDownloadSource.normalizeUrl("http://example.com/api/"));
        assertEquals("", CommunityDownloadSource.normalizeUrl("   "));
    }

    /// 地址合法性：仅接受带主机名的 http(s) 地址。
    @Test
    public void validatesUrls() {
        assertTrue(CommunityDownloadSource.isValidUrl("https://example.com"));
        assertTrue(CommunityDownloadSource.isValidUrl("http://127.0.0.1:8080"));
        assertFalse(CommunityDownloadSource.isValidUrl("example.com"));
        assertFalse(CommunityDownloadSource.isValidUrl("ftp://example.com"));
        assertFalse(CommunityDownloadSource.isValidUrl("https://"));
        assertFalse(CommunityDownloadSource.isValidUrl(""));
    }

    /// 显示名默认取主机名，超长名称被截断。
    @Test
    public void derivesNames() {
        assertEquals("example.com", CommunityDownloadSource.of("", "https://example.com").name());
        CommunityDownloadSource source = CommunityDownloadSource.of("x".repeat(200), "https://example.com");
        assertEquals(CommunityDownloadSource.MAX_NAME_LENGTH, source.name().length());
    }

    /// JSON 往返保留主源与镜像顺序。
    @Test
    public void roundTripsJson() {
        CommunityDownloadSource primary = CommunityDownloadSource.of("primary", "https://p.example");
        CommunityDownloadSource mirror = CommunityDownloadSource.of("mirror", "https://m.example");
        CommunityDownloadSources sources = new CommunityDownloadSources(primary, List.of(mirror));

        JsonObject json = sources.toJson();
        assertEquals(CommunityDownloadSources.SCHEMA, json.get("schema").getAsInt());
        assertEquals(2, json.getAsJsonArray("mirrors").size() + 1);

        CommunityDownloadSources parsed = CommunityDownloadSources.parse(json);
        assertEquals(primary, parsed.primary());
        assertEquals(List.of(mirror), parsed.mirrors());
        assertFalse(parsed.isEmpty());
    }

    /// 解析时丢弃非法条目与重复地址，并截断超过上限的镜像。
    @Test
    public void parseFiltersInvalidAndDuplicates() {
        JsonObject primary = new JsonObject();
        primary.addProperty("url", "https://p.example");
        JsonArray mirrors = new JsonArray();
        mirrors.add(primary.deepCopy());
        mirrors.add(CommunityDownloadSource.of("m1", "https://m1.example").toJson());
        mirrors.add(CommunityDownloadSource.of("m2", "https://m2.example").toJson());
        mirrors.add(CommunityDownloadSource.of("m3", "https://m3.example").toJson());
        mirrors.add(CommunityDownloadSource.of("m4", "https://m4.example").toJson());
        mirrors.add(CommunityDownloadSource.of("m5", "https://m5.example").toJson());
        mirrors.add(CommunityDownloadSource.of("m6", "https://m6.example").toJson());
        JsonObject invalid = new JsonObject();
        invalid.addProperty("url", "ftp://invalid.example");
        mirrors.add(invalid);

        JsonObject section = new JsonObject();
        section.add("primary", primary);
        section.add("mirrors", mirrors);

        CommunityDownloadSources parsed = CommunityDownloadSources.parse(section);
        assertEquals("https://p.example", parsed.primary().url());
        assertEquals(CommunityDownloadSources.MAX_MIRRORS, parsed.mirrors().size());
        assertEquals("https://m1.example", parsed.mirrors().get(0).url());
        assertEquals("https://m5.example", parsed.mirrors().get(4).url());
    }

    /// 空段落解析为“无自定义源”。
    @Test
    public void parsesEmptySection() {
        CommunityDownloadSources parsed = CommunityDownloadSources.parse(new JsonObject());
        assertTrue(parsed.isEmpty());
        assertNull(parsed.primary());
        assertEquals(List.of(), parsed.mirrors());
    }

    /// 重复地址检测跳过空槽位。
    @Test
    public void findsDuplicates() {
        List<String> urls = new ArrayList<>();
        urls.add("https://a.example");
        urls.add(null);
        urls.add("");
        urls.add("https://b.example");
        assertNull(CommunityDownloadSources.findDuplicate(urls));
        urls.add("https://a.example");
        assertEquals("https://a.example", CommunityDownloadSources.findDuplicate(urls));
    }

    /// 空配置返回 MissingDownloadProvider，不携带任何官方/内置 URL。
    @Test
    public void emptyConfigurationUsesMissingProvider() {
        DownloadProvider empty = CommunityDownloadInstaller.build(CommunityDownloadSources.empty());
        assertTrue(empty instanceof MissingDownloadProvider);
        assertEquals(List.of(), empty.getVersionListURLs());
        assertTrue(empty.getVersionListById("game") instanceof MissingVersionList);
        assertThrows(CommunityDownloadSourceMissingException.class,
                () -> empty.injectURL("https://piston-meta.mojang.com/version_manifest_v2.json"));
    }

    /// 候选顺序：只用主源 → 镜像，不追加官方源。
    @Test
    public void buildsProviderCandidatesInOrder() {
        CommunityDownloadSources sources = new CommunityDownloadSources(
                CommunityDownloadSource.of("primary", "https://p.example"),
                List.of(CommunityDownloadSource.of("m1", "https://m1.example")));
        DownloadProvider provider = CommunityDownloadInstaller.build(sources);
        assertTrue(provider instanceof AutoDownloadProvider);
        List<java.net.URI> urls = provider.getVersionListURLs();
        assertEquals(2, urls.size());
        assertTrue(urls.get(0).toString().startsWith("https://p.example/mc/game/version_manifest.json"));
        assertTrue(urls.get(1).toString().startsWith("https://m1.example/mc/game/version_manifest.json"));
        assertFalse(urls.stream().anyMatch(url -> url.toString().contains("mojang.com")));
    }
}
