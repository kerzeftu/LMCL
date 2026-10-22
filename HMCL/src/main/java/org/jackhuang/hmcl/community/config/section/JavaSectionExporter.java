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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.config.SettingsChange;
import org.jackhuang.hmcl.community.config.SettingsPreviewValues;
import org.jackhuang.hmcl.community.config.SettingsSectionExporter;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jackhuang.hmcl.community.java.JavaChoice;
import org.jackhuang.hmcl.community.java.JavaPreference;
import org.jackhuang.hmcl.download.java.disco.DiscoJavaDistribution;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// “Java”设置段：偏好发行版与镜像展示开关。
@NotNullByDefault
public final class JavaSectionExporter implements SettingsSectionExporter {

    /// 段 id。
    public static final String ID = JavaPreference.SECTION;

    @Override
    public @NotNull String id() {
        return ID;
    }

    @Override
    public @NotNull String titleKey() {
        return "community.settings.transfer.section.java";
    }

    @Override
    public @NotNull JsonObject exportNow() {
        JavaPreference.Preference preference = JavaPreference.get();
        JsonObject object = new JsonObject();
        object.addProperty(JavaPreference.KEY_DISTRIBUTION, preference.storageValue());
        object.addProperty(JavaPreference.KEY_MIRROR_ENABLED, preference.mirrorEnabled());
        return object;
    }

    @Override
    public @NotNull @Unmodifiable List<SettingsChange> preview(@NotNull JsonObject incoming) {
        JsonObject current = exportNow();
        List<SettingsChange> changes = new ArrayList<>();
        for (String key : current.keySet()) {
            JsonElement incomingValue = incoming.get(key);
            if (incomingValue == null) {
                continue;
            }
            if (!SettingsPreviewValues.same(current.get(key), incomingValue)) {
                changes.add(new SettingsChange(
                        id(), key,
                        displayValue(key, current.get(key)),
                        displayValue(key, incomingValue),
                        i18n("community.settings.transfer.preview.java." + (
                                JavaPreference.KEY_DISTRIBUTION.equals(key) ? "distribution" : "mirror"))));
            }
        }
        return List.copyOf(changes);
    }

    @Nullable
    private static String displayValue(@NotNull String key, @Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (JavaPreference.KEY_DISTRIBUTION.equals(key)) {
            String value = SettingsPreviewValues.string(element);
            DiscoJavaDistribution distribution =
                    value == null || value.isBlank() || JavaPreference.VALUE_ANY.equalsIgnoreCase(value)
                            ? null : DiscoJavaDistribution.of(value);
            return i18n(JavaChoice.of(distribution).i18nKey());
        }
        Boolean value = SettingsPreviewValues.bool(element);
        return value == null ? null : SettingsPreviewValues.onOff(value);
    }

    @Override
    public void apply(@NotNull JsonObject incoming) {
        String value = JsonUtils.getString(incoming, JavaPreference.KEY_DISTRIBUTION);
        DiscoJavaDistribution distribution;
        if (value == null || value.isBlank() || JavaPreference.VALUE_ANY.equalsIgnoreCase(value.trim())) {
            distribution = null;
        } else {
            distribution = DiscoJavaDistribution.of(value.trim());
            if (distribution == null) {
                throw new IllegalArgumentException("unknown java distribution: " + value);
            }
        }
        boolean mirrorEnabled = JsonUtils.getBoolean(incoming, JavaPreference.KEY_MIRROR_ENABLED,
                CommunitySettings.get().section(JavaPreference.SECTION)
                        .has(JavaPreference.KEY_MIRROR_ENABLED)
                        && JsonUtils.getBoolean(CommunitySettings.get().section(JavaPreference.SECTION),
                        JavaPreference.KEY_MIRROR_ENABLED, false));
        JavaPreference.set(new JavaPreference.Preference(distribution, mirrorEnabled));
    }
}
