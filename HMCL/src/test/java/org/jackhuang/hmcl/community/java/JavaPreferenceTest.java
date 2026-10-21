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
package org.jackhuang.hmcl.community.java;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jackhuang.hmcl.download.java.disco.DiscoJavaDistribution;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [JavaPreference] 的读写/回退测试。
@NotNullByDefault
public final class JavaPreferenceTest {

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

    /// 缺字段时默认 Zulu。
    @Test
    public void defaultsToZulu() {
        JavaPreference.Preference preference = JavaPreference.get(settings);
        assertEquals(DiscoJavaDistribution.ZULU, preference.distribution());
        assertFalse(preference.mirrorEnabled());
    }

    /// `ANY` 读出 `null`。
    @Test
    public void anyMapsToNull() {
        JsonObject java = new JsonObject();
        java.addProperty(JavaPreference.KEY_DISTRIBUTION, JavaPreference.VALUE_ANY);
        settings.putSection(JavaPreference.SECTION, java);

        assertNull(JavaPreference.get(settings).distribution());
        assertEquals(JavaChoice.ANY, JavaPreference.get(settings).choice());
    }

    /// 非法值/未知发行版回退 Zulu。
    @Test
    public void unknownFallsBackToZulu() {
        JsonObject java = new JsonObject();
        java.addProperty(JavaPreference.KEY_DISTRIBUTION, "NOT_A_DISTRIBUTION");
        settings.putSection(JavaPreference.SECTION, java);

        assertEquals(DiscoJavaDistribution.ZULU, JavaPreference.get(settings).distribution());
    }

    /// 写入后能读回，且立即落盘。
    @Test
    public void roundTripsDistribution() {
        JavaPreference.setDistribution(settings, DiscoJavaDistribution.TEMURIN);

        assertEquals(DiscoJavaDistribution.TEMURIN, JavaPreference.get(settings).distribution());
        assertTrue(Files.exists(settings.path()));
    }

    /// 写偏好时不破坏同段的其它字段。
    @Test
    public void preservesMirrorField() {
        JsonObject java = new JsonObject();
        java.addProperty(JavaPreference.KEY_MIRROR_ENABLED, false);
        settings.putSection(JavaPreference.SECTION, java);

        JavaPreference.setDistribution(settings, DiscoJavaDistribution.LIBERICA);

        JavaPreference.Preference preference = JavaPreference.get(settings);
        assertEquals(DiscoJavaDistribution.LIBERICA, preference.distribution());
        assertFalse(preference.mirrorEnabled());
    }
}
