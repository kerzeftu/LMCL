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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import javafx.beans.property.Property;
import org.glavo.monetfx.ColorStyle;
import org.jackhuang.hmcl.community.config.SettingsChange;
import org.jackhuang.hmcl.community.config.SettingsPreviewValues;
import org.jackhuang.hmcl.community.config.SettingsSectionExporter;
import org.jackhuang.hmcl.setting.BackgroundType;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.ThemeColorType;
import org.jackhuang.hmcl.theme.BackgroundLoadPolicy;
import org.jackhuang.hmcl.theme.NetworkBackgroundImageCachePolicy;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.theme.ThemeReference;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// “外观”设置段：只导出可跨设备复用的 UI 偏好。
///
/// 白名单 19 项，**不含**本地背景路径、网络背景 URL、自定义 Paint、更新通道、
/// 预览更新与其它带设备/安全语义的字段。
@NotNullByDefault
public final class AppearanceSectionExporter implements SettingsSectionExporter {

    /// 段 id。
    public static final String ID = "appearance";

    /// 段标题 i18n key。
    public static final String TITLE_KEY = "community.settings.transfer.section.appearance";

    private static final String[] BOOLEAN_KEYS = {
            "titleBarTransparent", "windowTransparent", "animationDisabled"
    };

    private static final String[] STRING_KEYS = {
            "themeBrightnessMode", "builtinBackgroundId", "logFontFamily", "launcherFontFamily"
    };

    private static final String[] DOUBLE_KEYS = {
            "backgroundOpacity", "logFontSize"
    };

    private static final String[] OBJECT_ENUM_KEYS = {
            "themeColorType", "themeColorStyle", "backgroundType",
            "networkBackgroundImageCachePolicy", "backgroundFallbackType", "backgroundLoadPolicy"
    };

    @Override
    public @NotNull String id() {
        return ID;
    }

    @Override
    public @NotNull String titleKey() {
        return TITLE_KEY;
    }

    @Override
    public @NotNull JsonObject exportNow() {
        LauncherSettings settings = SettingsManager.settings();
        JsonObject object = new JsonObject();
        object.add("language", JsonUtils.GSON.toJsonTree(settings.languageProperty().get()));
        object.add("selectedTheme", JsonUtils.GSON.toJsonTree(settings.selectedThemeProperty().get()));
        object.add("themeAppearanceOverrides", JsonUtils.GSON.toJsonTree(
                new LinkedHashSet<>(settings.getThemeAppearanceOverrides())));

        for (String key : STRING_KEYS) {
            object.addProperty(key, switch (key) {
                case "themeBrightnessMode" -> settings.themeBrightnessModeProperty().get();
                case "builtinBackgroundId" -> settings.builtinBackgroundIdProperty().get();
                case "logFontFamily" -> settings.logFontFamilyProperty().get();
                case "launcherFontFamily" -> settings.launcherFontFamilyProperty().get();
                default -> throw new IllegalStateException(key);
            });
        }
        for (String key : BOOLEAN_KEYS) {
            object.addProperty(key, switch (key) {
                case "titleBarTransparent" -> settings.titleBarTransparentProperty().get();
                case "windowTransparent" -> settings.windowTransparentProperty().get();
                case "animationDisabled" -> settings.animationDisabledProperty().get();
                default -> throw new IllegalStateException(key);
            });
        }
        for (String key : DOUBLE_KEYS) {
            object.addProperty(key, switch (key) {
                case "backgroundOpacity" -> settings.backgroundOpacityProperty().get();
                case "logFontSize" -> settings.logFontSizeProperty().get();
                default -> throw new IllegalStateException(key);
            });
        }

        object.add("customThemeColor", JsonUtils.GSON.toJsonTree(settings.customThemeColorProperty().get()));
        for (String key : OBJECT_ENUM_KEYS) {
            object.add(key, JsonUtils.GSON.toJsonTree(switch (key) {
                case "themeColorType" -> settings.themeColorTypeProperty().get();
                case "themeColorStyle" -> settings.themeColorStyleProperty().get();
                case "backgroundType" -> settings.backgroundTypeProperty().get();
                case "networkBackgroundImageCachePolicy" -> settings.networkBackgroundImageCachePolicyProperty().get();
                case "backgroundFallbackType" -> settings.backgroundFallbackTypeProperty().get();
                case "backgroundLoadPolicy" -> settings.backgroundLoadPolicyProperty().get();
                default -> throw new IllegalStateException(key);
            }));
        }
        return object;
    }

    @Override
    public @NotNull @Unmodifiable List<SettingsChange> preview(@NotNull JsonObject incoming) {
        JsonObject current = exportNow();
        List<SettingsChange> changes = new ArrayList<>();
        for (String key : current.keySet()) {
            JsonElement incomingValue = incoming.get(key);
            if (incomingValue == null) {
                // apply() 对缺失字段保持现状，预览也不应展示成“移除”。
                continue;
            }
            if (!SettingsPreviewValues.same(current.get(key), incomingValue)) {
                changes.add(new SettingsChange(
                        id(), key,
                        displayValue(key, current.get(key)),
                        displayValue(key, incomingValue),
                        i18n(labelKey(key))));
            }
        }
        return List.copyOf(changes);
    }

    /// 返回外观字段的人类可读标题 key。
    ///
    /// @param key 字段名
    /// @return i18n key
    @NotNull
    private static String labelKey(@NotNull String key) {
        return "community.settings.transfer.preview.appearance." + key;
    }

    /// 把外观字段值转成人类可读文本。
    ///
    /// 复杂对象不会原样输出 JSON：主题引用、自定义颜色和覆盖集合都有专门摘要。
    ///
    /// @param key 字段名
    /// @param element JSON 值
    /// @return 可读文本，缺失时为 `null`
    @Nullable
    private static String displayValue(@NotNull String key, @Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return switch (key) {
                case "selectedTheme" -> i18n("community.settings.transfer.preview.appearance.selectedTheme.default");
                case "customThemeColor" -> i18n("community.settings.transfer.preview.appearance.customThemeColor.default");
                default -> null;
            };
        }
        if ("selectedTheme".equals(key) && element instanceof JsonObject object) {
            String packId = JsonUtils.getString(object, "packId");
            String themeId = JsonUtils.getString(object, "themeId");
            if (packId == null) {
                return i18n("community.settings.transfer.preview.appearance.selectedTheme.unknown");
            }
            return themeId == null ? packId : packId + " / " + themeId;
        }
        if ("customThemeColor".equals(key) && element instanceof JsonObject object) {
            String name = JsonUtils.getString(object, "name");
            return name != null ? name : i18n("community.settings.transfer.preview.appearance.customThemeColor.updated");
        }
        if ("themeAppearanceOverrides".equals(key) && element instanceof JsonArray array) {
            return i18n("community.settings.transfer.value.items", array.size());
        }
        if ("language".equals(key)) {
            String name = SettingsPreviewValues.string(element);
            if (name != null) {
                return SupportedLocale.getLocaleByName(name)
                        .getDisplayName(SupportedLocale.DEFAULT);
            }
        }
        return SettingsPreviewValues.describe(element);
    }

    @Override
    public void apply(@NotNull JsonObject incoming) {
        LauncherSettings settings = SettingsManager.settings();

        // 先完整解析，任何一项非法都不写入，避免半套外观。
        @Nullable SupportedLocale language = incoming.has("language")
                ? JsonUtils.GSON.fromJson(incoming.get("language"), SupportedLocale.class) : null;
        boolean hasTheme = incoming.has("selectedTheme");
        @Nullable ThemeReference selectedTheme = hasTheme
                ? JsonUtils.GSON.fromJson(incoming.get("selectedTheme"), ThemeReference.class) : null;
        @Nullable Set<String> overrides = null;
        if (incoming.get("themeAppearanceOverrides") instanceof JsonArray array) {
            overrides = new LinkedHashSet<>();
            for (JsonElement element : array) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    overrides.add(element.getAsString());
                }
            }
        }
        @Nullable ThemeColor customThemeColor = incoming.has("customThemeColor")
                ? JsonUtils.GSON.fromJson(incoming.get("customThemeColor"), ThemeColor.class) : null;

        String brightnessMode = optString(incoming, "themeBrightnessMode");
        String builtinBackgroundId = optString(incoming, "builtinBackgroundId");
        String logFontFamily = optString(incoming, "logFontFamily");
        String launcherFontFamily = optString(incoming, "launcherFontFamily");

        @Nullable Boolean titleBarTransparent = optBoolean(incoming, "titleBarTransparent");
        @Nullable Boolean windowTransparent = optBoolean(incoming, "windowTransparent");
        @Nullable Boolean animationDisabled = optBoolean(incoming, "animationDisabled");

        @Nullable Double backgroundOpacity = optDouble(incoming, "backgroundOpacity");
        @Nullable Double logFontSize = optDouble(incoming, "logFontSize");

        @Nullable ThemeColorType themeColorType = incoming.has("themeColorType")
                ? JsonUtils.GSON.fromJson(incoming.get("themeColorType"), ThemeColorType.class) : null;
        @Nullable ColorStyle themeColorStyle = incoming.has("themeColorStyle")
                ? JsonUtils.GSON.fromJson(incoming.get("themeColorStyle"), ColorStyle.class) : null;
        @Nullable BackgroundType backgroundType = incoming.has("backgroundType")
                ? JsonUtils.GSON.fromJson(incoming.get("backgroundType"), BackgroundType.class) : null;
        @Nullable NetworkBackgroundImageCachePolicy cachePolicy = incoming.has("networkBackgroundImageCachePolicy")
                ? JsonUtils.GSON.fromJson(incoming.get("networkBackgroundImageCachePolicy"), NetworkBackgroundImageCachePolicy.class) : null;
        @Nullable BackgroundType backgroundFallbackType = incoming.has("backgroundFallbackType")
                ? JsonUtils.GSON.fromJson(incoming.get("backgroundFallbackType"), BackgroundType.class) : null;
        @Nullable BackgroundLoadPolicy backgroundLoadPolicy = incoming.has("backgroundLoadPolicy")
                ? JsonUtils.GSON.fromJson(incoming.get("backgroundLoadPolicy"), BackgroundLoadPolicy.class) : null;

        // 全部解析成功后才写入。
        if (language != null) {
            settings.languageProperty().set(language);
        }
        if (hasTheme) {
            settings.selectedThemeProperty().set(selectedTheme);
        }
        if (overrides != null) {
            settings.getThemeAppearanceOverrides().clear();
            settings.getThemeAppearanceOverrides().addAll(overrides);
        }
        if (customThemeColor != null) {
            settings.customThemeColorProperty().set(customThemeColor);
        }
        if (brightnessMode != null) {
            settings.themeBrightnessModeProperty().set(brightnessMode);
        }
        if (builtinBackgroundId != null) {
            settings.builtinBackgroundIdProperty().set(builtinBackgroundId);
        }
        if (logFontFamily != null) {
            settings.logFontFamilyProperty().set(logFontFamily);
        }
        if (launcherFontFamily != null) {
            settings.launcherFontFamilyProperty().set(launcherFontFamily);
        }
        if (titleBarTransparent != null) {
            settings.titleBarTransparentProperty().set(titleBarTransparent);
        }
        if (windowTransparent != null) {
            settings.windowTransparentProperty().set(windowTransparent);
        }
        if (animationDisabled != null) {
            settings.animationDisabledProperty().set(animationDisabled);
        }
        if (backgroundOpacity != null) {
            settings.backgroundOpacityProperty().set(backgroundOpacity);
        }
        if (logFontSize != null) {
            settings.logFontSizeProperty().set(logFontSize);
        }
        if (themeColorType != null) {
            settings.themeColorTypeProperty().set(themeColorType);
        }
        if (themeColorStyle != null) {
            settings.themeColorStyleProperty().set(themeColorStyle);
        }
        if (backgroundType != null) {
            settings.backgroundTypeProperty().set(backgroundType);
        }
        if (cachePolicy != null) {
            settings.networkBackgroundImageCachePolicyProperty().set(cachePolicy);
        }
        if (backgroundFallbackType != null) {
            settings.backgroundFallbackTypeProperty().set(backgroundFallbackType);
        }
        if (backgroundLoadPolicy != null) {
            settings.backgroundLoadPolicyProperty().set(backgroundLoadPolicy);
        }
    }

    private static @Nullable String optString(@NotNull JsonObject object, @NotNull String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }

    private static @Nullable Boolean optBoolean(@NotNull JsonObject object, @NotNull String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean() : null;
    }

    private static @Nullable Double optDouble(@NotNull JsonObject object, @NotNull String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsDouble() : null;
    }
}
