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
 * Modified by LMCL team on 2026-10-27: three selectable visual themes.
 */
package org.jackhuang.hmcl.community.ui.m3;

import org.jetbrains.annotations.NotNullByDefault;

/// LMCL 的三个可选视觉主题。
///
/// 这三个主题直接对应启动器外壳的视觉模式：
///
/// - [M3_EXPRESSIVE]：Material 3 Expressive，启用玻璃表面；
/// - [M3_GLASS_OFF]：Material 3 Expressive，关闭玻璃表面；
/// - [CLASSIC]：原版 HMCL 外观。
///
/// 选择结果保存在 `community-settings.json` 的 `visual` 段中。切换时由
/// [M3Settings#setVisualTheme(VisualTheme)] 写入设置，再由
/// [org.jackhuang.hmcl.ui.decorator.DecoratorController#applyVisualTheme()]
/// 重新包裹外壳，因此不需要重启启动器。
///
/// 未保存过视觉主题时默认使用 [M3_EXPRESSIVE]；显式选择 [CLASSIC] 的用户
/// 保持 Classic。
@NotNullByDefault
public enum VisualTheme {

    /// Material 3 Expressive，启用玻璃。
    M3_EXPRESSIVE("m3-expressive", true, "on"),

    /// Material 3 Expressive，关闭玻璃。
    M3_GLASS_OFF("m3-glass-off", true, "off"),

    /// 原版 HMCL 外观。
    CLASSIC("classic", false, "off");

    /// 写入设置文件与 i18n key 使用的稳定标识。
    private final String id;

    /// 是否启用 M3 外壳。
    private final boolean m3Enabled;

    /// 玻璃模式：`on` 或 `off`。
    private final String glassMode;

    /// 创建一个视觉主题。
    ///
    /// @param id 稳定标识
    /// @param m3Enabled 是否启用 M3
    /// @param glassMode 玻璃模式
    VisualTheme(String id, boolean m3Enabled, String glassMode) {
        this.id = id;
        this.m3Enabled = m3Enabled;
        this.glassMode = glassMode;
    }

    /// 返回稳定标识；用于 `settings.launcher.visual_theme.<id>` i18n key。
    ///
    /// @return 稳定标识
    public String id() {
        return id;
    }

    /// 返回是否启用 M3 外壳。
    ///
    /// @return 是否启用
    public boolean m3Enabled() {
        return m3Enabled;
    }

    /// 返回玻璃模式。
    ///
    /// @return `on` 或 `off`
    public String glassMode() {
        return glassMode;
    }

    /// 根据已有的 M3/玻璃开关推导视觉主题。
    ///
    /// M3 关闭时始终返回 [CLASSIC]；M3 开启且玻璃为 `off` 时返回
    /// [M3_GLASS_OFF]；其余情况（包括 `auto`）都视为 [M3_EXPRESSIVE]。
    ///
    /// @param m3Enabled 是否启用 M3
    /// @param glassMode 玻璃模式
    /// @return 对应主题
    public static VisualTheme from(boolean m3Enabled, String glassMode) {
        if (!m3Enabled) {
            return CLASSIC;
        }
        return "off".equals(glassMode) ? M3_GLASS_OFF : M3_EXPRESSIVE;
    }

    /// 返回当前生效的视觉主题。
    ///
    /// @return 当前主题
    public static VisualTheme current() {
        return from(M3Settings.isM3Enabled(), M3Settings.glassMode());
    }

    /// 持久化该主题，并立即应用到启动器外壳。
    ///
    /// 系统属性只用于本次启动的 A/B 调试；用户从外观页选择主题时清除这些
    /// 覆盖，保证设置文件中的选择立即生效。实际外壳替换由调用方在 FX 线程
    /// 调用 [org.jackhuang.hmcl.ui.decorator.DecoratorController#applyVisualTheme()]。
    public void apply() {
        System.clearProperty(M3Settings.M3_PROPERTY);
        System.clearProperty(M3Settings.GLASS_PROPERTY);
        M3Settings.setVisualTheme(this);
    }
}
