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

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 插件下载地址的内置放行规则测试（不依赖 JavaFX/网络）。
@NotNullByDefault
public final class CommunityAddonSourcesTest {

    /// 四个官方端点都被识别。
    @Test
    public void acceptsOfficialAddonEndpoints() {
        assertTrue(CommunityAddonSources.isBuiltinAddonUrl(
                "https://api.modrinth.com/v2/search?query=sodium"));
        assertTrue(CommunityAddonSources.isBuiltinAddonUrl(
                "https://cdn.modrinth.com/data/AABBCC/versions/1.0/mod.jar"));
        assertTrue(CommunityAddonSources.isBuiltinAddonUrl(
                "https://api.curseforge.com/v1/mods/search"));
        assertTrue(CommunityAddonSources.isBuiltinAddonUrl(
                "https://edge.forgecdn.net/files/1234/567/mod.jar"));
    }

    /// 相似域名、非 HTTPS 与无效地址不会被误放行。
    @Test
    public void rejectsLookalikesAndInvalidUrls() {
        assertFalse(CommunityAddonSources.isBuiltinAddonUrl(
                "https://api.modrinth.com.evil.example/v2/search"));
        assertFalse(CommunityAddonSources.isBuiltinAddonUrl(
                "http://api.modrinth.com/v2/search"));
        assertFalse(CommunityAddonSources.isBuiltinAddonUrl(
                "https://piston-meta.mojang.com/version_manifest_v2.json"));
        assertFalse(CommunityAddonSources.isBuiltinAddonUrl("not a url"));
    }

    /// 未配置游戏下载源时，插件 API/CDN 仍直接放行。
    @Test
    public void missingProviderPassesAddonUrlsThrough() {
        MissingDownloadProvider provider = new MissingDownloadProvider();
        String modrinth = "https://api.modrinth.com/v2/project/sodium";
        assertEquals(modrinth, provider.injectURL(modrinth));
        assertEquals(List.of(URI.create(modrinth)), provider.injectURLWithCandidates(modrinth));

        String curseForge = "https://api.curseforge.com/v1/mods/123";
        assertEquals(curseForge, provider.injectURL(curseForge));
        assertEquals(List.of(URI.create(curseForge)), provider.injectURLWithCandidates(curseForge));
    }

    /// 同一 provider 仍然拒绝 Minecraft 版本/资源下载地址。
    @Test
    public void missingProviderStillRejectsGameUrls() {
        MissingDownloadProvider provider = new MissingDownloadProvider();
        assertThrows(CommunityDownloadSourceMissingException.class,
                () -> provider.injectURL("https://piston-meta.mojang.com/version_manifest_v2.json"));
        assertThrows(CommunityDownloadSourceMissingException.class,
                () -> provider.injectURLWithCandidates("https://libraries.minecraft.net/foo.jar"));
    }
}
