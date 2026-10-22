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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 下载源配置与自动测速排序的纯函数测试（不依赖 JavaFX/磁盘）。
@NotNullByDefault
public final class CommunityDownloadSourcesTest {

    /// 每个测试前清空测速缓存。
    @BeforeEach
    public void resetRanking() {
        CommunityDownloadRanker.resetForTesting();
    }

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

    /// JSON 往返保留平级源的顺序。
    @Test
    public void roundTripsJson() {
        CommunityDownloadSource a = CommunityDownloadSource.of("a", "https://a.example");
        CommunityDownloadSource b = CommunityDownloadSource.of("b", "https://b.example");
        CommunityDownloadSource c = CommunityDownloadSource.of("c", "https://c.example");
        CommunityDownloadSources sources = new CommunityDownloadSources(List.of(a, b, c));

        JsonObject json = sources.toJson();
        assertEquals(CommunityDownloadSources.SCHEMA, json.get("schema").getAsInt());
        assertEquals(3, json.getAsJsonArray("sources").size());
        assertFalse(json.has("primary"));
        assertFalse(json.has("mirrors"));

        CommunityDownloadSources parsed = CommunityDownloadSources.parse(json);
        assertEquals(List.of(a, b, c), parsed.sources());
        assertFalse(parsed.isEmpty());
    }

    /// 解析时丢弃非法条目与重复地址，并截断超过上限的源。
    @Test
    public void parseFiltersInvalidAndDuplicates() {
        JsonArray array = new JsonArray();
        array.add(CommunityDownloadSource.of("a", "https://a.example").toJson());
        array.add(CommunityDownloadSource.of("a", "https://a.example").toJson());
        array.add(CommunityDownloadSource.of("b", "https://b.example").toJson());
        array.add(CommunityDownloadSource.of("c", "https://c.example").toJson());
        array.add(CommunityDownloadSource.of("d", "https://d.example").toJson());
        array.add(CommunityDownloadSource.of("e", "https://e.example").toJson());
        array.add(CommunityDownloadSource.of("f", "https://f.example").toJson());
        JsonObject invalid = new JsonObject();
        invalid.addProperty("url", "ftp://invalid.example");
        array.add(invalid);

        JsonObject section = new JsonObject();
        section.add("sources", array);

        CommunityDownloadSources parsed = CommunityDownloadSources.parse(section);
        assertEquals(CommunityDownloadSources.MAX_SOURCES, parsed.size());
        assertEquals("https://a.example", parsed.sources().get(0).url());
        assertEquals("https://e.example", parsed.sources().get(4).url());
    }

    /// v1 的 primary + mirrors 会按顺序迁移为平级源。
    @Test
    public void parsesLegacyPrimaryAndMirrors() {
        JsonObject primary = CommunityDownloadSource.of("p", "https://p.example").toJson();
        JsonArray mirrors = new JsonArray();
        mirrors.add(CommunityDownloadSource.of("m1", "https://m1.example").toJson());
        mirrors.add(CommunityDownloadSource.of("m2", "https://m2.example").toJson());

        JsonObject section = new JsonObject();
        section.addProperty("schema", 1);
        section.add("primary", primary);
        section.add("mirrors", mirrors);

        CommunityDownloadSources parsed = CommunityDownloadSources.parse(section);
        assertEquals(List.of("https://p.example", "https://m1.example", "https://m2.example"),
                parsed.sources().stream().map(CommunityDownloadSource::url).toList());
    }

    /// 空段落解析为“无自定义源”。
    @Test
    public void parsesEmptySection() {
        CommunityDownloadSources parsed = CommunityDownloadSources.parse(new JsonObject());
        assertTrue(parsed.isEmpty());
        assertEquals(List.of(), parsed.sources());
        assertEquals(0, parsed.size());
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

    /// 无测速结果时按配置顺序构建候选链，不追加官方源。
    @Test
    public void buildsProviderCandidatesInConfiguredOrder() {
        CommunityDownloadSources sources = new CommunityDownloadSources(List.of(
                CommunityDownloadSource.of("a", "https://a.example"),
                CommunityDownloadSource.of("b", "https://b.example")));
        DownloadProvider provider = CommunityDownloadInstaller.build(sources);
        assertTrue(provider instanceof AutoDownloadProvider);
        List<java.net.URI> urls = provider.getVersionListURLs();
        assertEquals(2, urls.size());
        assertTrue(urls.get(0).toString().startsWith("https://a.example/mc/game/version_manifest.json"));
        assertTrue(urls.get(1).toString().startsWith("https://b.example/mc/game/version_manifest.json"));
        assertFalse(urls.stream().anyMatch(url -> url.toString().contains("mojang.com")));
    }

    /// 有测速结果时按延迟排序，未出现在测速结果中的源按配置顺序追加。
    @Test
    public void buildsProviderCandidatesInRankedOrder() {
        CommunityDownloadSources sources = new CommunityDownloadSources(List.of(
                CommunityDownloadSource.of("a", "https://a.example"),
                CommunityDownloadSource.of("b", "https://b.example"),
                CommunityDownloadSource.of("c", "https://c.example")));
        CommunityDownloadRanker.setRankedUrlsForTesting(List.of(
                "https://c.example", "https://a.example"));

        List<CommunityDownloadSource> ordered = CommunityDownloadRanker.order(sources.sources());
        assertEquals(List.of("https://c.example", "https://a.example", "https://b.example"),
                ordered.stream().map(CommunityDownloadSource::url).toList());

        DownloadProvider provider = CommunityDownloadInstaller.build(sources);
        List<java.net.URI> urls = provider.getVersionListURLs();
        assertEquals(3, urls.size());
        assertTrue(urls.get(0).toString().startsWith("https://c.example/mc/game/version_manifest.json"));
        assertTrue(urls.get(1).toString().startsWith("https://a.example/mc/game/version_manifest.json"));
        assertTrue(urls.get(2).toString().startsWith("https://b.example/mc/game/version_manifest.json"));
    }

    /// 测速排序：成功按延迟升序，失败按原顺序排在最后。
    @Test
    public void ranksByLatencyWithFailuresLast() {
        List<String> ranked = CommunityDownloadRanker.rank(List.of(
                new CommunityDownloadRanker.Probe("https://slow.example", true, 300, 0),
                new CommunityDownloadRanker.Probe("https://failed.example", false, -1, 1),
                new CommunityDownloadRanker.Probe("https://fast.example", true, 50, 2),
                new CommunityDownloadRanker.Probe("https://failed2.example", false, -1, 3)));

        assertEquals(List.of("https://fast.example", "https://slow.example",
                "https://failed.example", "https://failed2.example"), ranked);
    }
}
