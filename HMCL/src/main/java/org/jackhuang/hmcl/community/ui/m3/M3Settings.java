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
 *
 * Modified by LMCL team on 2026-10-23: Material 3 visual settings.
 */
package org.jackhuang.hmcl.community.ui.m3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/// Material 3 视觉开关的读写入口。
///
/// 设置保存在 `<本地根>/config/community-settings.json` 的 `visual` 段，结构为：
///
/// ```json
/// {"visual":{"enabled":true,"glass":"auto","wavy":true,"reducedMotion":false}}
/// ```
///
/// 系统属性 `-Dlmcl.visual.m3=on/off` 优先于设置文件，供阶段 A 的 A/B 对比使用。
/// 本类只负责开关，不负责安装主题；主题安装见 [M3Host]。
@NotNullByDefault
public final class M3Settings {

    /// 系统属性键：覆盖设置文件中的 M3 开关。
    public static final String M3_PROPERTY = "lmcl.visual.m3";

    /// 系统属性键：覆盖玻璃模式。
    public static final String GLASS_PROPERTY = "lmcl.visual.glass";

    /// 系统属性键：覆盖 Wavy 进度开关。
    public static final String WAVY_PROPERTY = "lmcl.visual.wavy";

    /// 系统属性键：覆盖减弱动效开关。
    public static final String REDUCED_MOTION_PROPERTY = "lmcl.visual.reduced-motion";

    /// 设置文件中的段落名。
    private static final String SECTION = "visual";

    /// M3 开关在段落中的键名。
    private static final String ENABLED_KEY = "enabled";

    /// 阶段 A 早期使用的兼容键名；读取时回退，写入时清理。
    private static final String LEGACY_ENABLED_KEY = "m3";

    /// 未保存过视觉主题时的默认值：M3 Expressive。
    ///
    /// 只有显式保存过 `visual.enabled`（包括从外观页选择 Classic）时才会
    /// 覆盖该默认值，因此升级不会把已经选择 Classic 的用户强制切回 M3。
    public static final boolean DEFAULT_M3_ENABLED = true;

    /// 玻璃模式在段落中的键名。
    private static final String GLASS_KEY = "glass";

    /// Wavy 进度在段落中的键名。
    private static final String WAVY_KEY = "wavy";

    /// 减弱动效在段落中的键名。
    private static final String REDUCED_MOTION_KEY = "reducedMotion";

    /// 工具类，不允许实例化。
    private M3Settings() {
    }

    /// 返回是否启用 Material 3 视觉。
    ///
    /// 系统属性优先；未设置时读取共享设置。设置缺失或解析失败时按
    /// [DEFAULT_M3_ENABLED]（M3 Expressive）处理；显式保存的 `false`
    /// 仍然表示 Classic。
    ///
    /// @return 是否启用
    public static boolean isM3Enabled() {
        Boolean override = parseBoolean(System.getProperty(M3_PROPERTY));
        if (override != null) {
            return override;
        }
        return booleanSetting(ENABLED_KEY, booleanSetting(LEGACY_ENABLED_KEY, DEFAULT_M3_ENABLED));
    }

    /// [isM3Enabled()] 的短别名，供调用点保持可读。
    ///
    /// @return 是否启用
    public static boolean enabled() {
        return isM3Enabled();
    }

    /// 写入 M3 开关；系统属性仍会覆盖该值。
    ///
    /// @param enabled 是否启用
    public static void setM3Enabled(boolean enabled) {
        JsonObject section = CommunitySettings.get().section(SECTION);
        section.addProperty(ENABLED_KEY, enabled);
        section.remove(LEGACY_ENABLED_KEY);
        CommunitySettings.get().putSection(SECTION, section);
    }

    /// 返回玻璃模式：`auto`、`on` 或 `off`。
    ///
    /// 阶段 A 只读取，不消费该值；阶段 B 的玻璃实现使用。
    ///
    /// @return 模式，默认 `auto`
    public static String glassMode() {
        String override = System.getProperty(GLASS_PROPERTY);
        if (override != null && !override.isBlank()) {
            return validateGlassMode(override);
        }
        JsonElement element = CommunitySettings.get().section(SECTION).get(GLASS_KEY);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return validateGlassMode(element.getAsString());
        }
        return "auto";
    }

    /// 写入玻璃模式；未知值按 `auto` 保存。
    ///
    /// @param mode `auto`、`on` 或 `off`
    public static void setGlassMode(String mode) {
        JsonObject section = CommunitySettings.get().section(SECTION);
        section.addProperty(GLASS_KEY, validateGlassMode(mode));
        CommunitySettings.get().putSection(SECTION, section);
    }

    /// 返回当前视觉主题。
    ///
    /// 该值由 [VisualTheme] 从 M3 与玻璃开关推导，不单独持久化。
    ///
    /// @return 当前视觉主题
    public static VisualTheme visualTheme() {
        return VisualTheme.from(isM3Enabled(), glassMode());
    }

    /// 写入视觉主题。
    ///
    /// 同时写入 M3 开关与玻璃模式；不会清理系统属性。用户主动选择主题时
    /// 应调用 [VisualTheme#apply()]，由它清理本次启动的系统属性覆盖。
    ///
    /// @param theme 视觉主题
    public static void setVisualTheme(VisualTheme theme) {
        JsonObject section = CommunitySettings.get().section(SECTION);
        section.addProperty(ENABLED_KEY, theme.m3Enabled());
        section.addProperty(GLASS_KEY, theme.glassMode());
        section.remove(LEGACY_ENABLED_KEY);
        CommunitySettings.get().putSection(SECTION, section);
    }

    /// 返回是否启用玻璃表面。
    ///
    /// M3 总开关关闭时始终返回 `false`；玻璃模式为 `off` 时也返回 `false`。
    /// `auto` 与 `on` 都允许尝试快照，实际无壁纸时由 [GlassSurface] 回退到
    /// CSS 半透明表面色。
    ///
    /// @return 是否启用玻璃
    public static boolean isGlassEnabled() {
        return isM3Enabled() && !"off".equals(glassMode());
    }

    /// 返回是否启用 Wavy 进度条。
    ///
    /// 默认 `true`；阶段 E 才消费该值。
    ///
    /// @return 是否启用
    public static boolean isWavyEnabled() {
        Boolean override = parseBoolean(System.getProperty(WAVY_PROPERTY));
        return override != null ? override : booleanSetting(WAVY_KEY, true);
    }

    /// 写入 Wavy 进度开关。
    ///
    /// @param enabled 是否启用
    public static void setWavyEnabled(boolean enabled) {
        writeBoolean(WAVY_KEY, enabled);
    }

    /// 返回是否要求减弱动效。
    ///
    /// 默认 `false`；[M3MotionSupport] 会把它与上游“禁用动画”合并。
    ///
    /// @return 是否减弱动效
    public static boolean isReducedMotion() {
        Boolean override = parseBoolean(System.getProperty(REDUCED_MOTION_PROPERTY));
        return override != null ? override : booleanSetting(REDUCED_MOTION_KEY, false);
    }

    /// 写入减弱动效开关。
    ///
    /// @param reduced 是否减弱动效
    public static void setReducedMotion(boolean reduced) {
        writeBoolean(REDUCED_MOTION_KEY, reduced);
    }

    /// 读取布尔设置。
    ///
    /// @param key 键名
    /// @param fallback 缺失/类型不符时的默认值
    /// @return 设置值
    private static boolean booleanSetting(String key, boolean fallback) {
        JsonElement element = CommunitySettings.get().section(SECTION).get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    /// 写入布尔设置。
    ///
    /// @param key 键名
    /// @param value 值
    private static void writeBoolean(String key, boolean value) {
        JsonObject section = CommunitySettings.get().section(SECTION);
        section.addProperty(key, value);
        CommunitySettings.get().putSection(SECTION, section);
    }

    /// 校验玻璃模式，未知值回退 `auto`。
    ///
    /// @param value 原始值
    /// @return 规范化模式
    private static String validateGlassMode(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "on", "off" -> normalized;
            default -> "auto";
        };
    }

    /// 解析布尔值；无法识别时返回 `null`，表示“没有覆盖”。
    ///
    /// 接受 `on/off`、`true/false`、`yes/no`、`1/0`，忽略大小写与首尾空白。
    ///
    /// @param value 原始值
    /// @return 布尔值或 `null`
    @Nullable
    static Boolean parseBoolean(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> Boolean.TRUE;
            case "off", "false", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
    }
}
