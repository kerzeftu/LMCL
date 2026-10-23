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
 * Modified by LMCL team on 2026-10-23: bridge HMCL animation setting to m3fx.
 */
package org.jackhuang.hmcl.community.ui.m3;

import org.glavo.m3fx.animation.M3MotionSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 把上游“禁用动画”设置桥接到 m3fx。
///
/// m3fx 的动画开关是全局静态状态；本类只在该状态与 HMCL 设置不一致时写入，
/// 不接管 `Scene` 或页面动画。构造/首次安装失败只记日志，不阻断 UI。
@NotNullByDefault
public final class M3MotionSupport {

    /// 是否已经安装监听器，避免重复注册。
    private static boolean installed;

    /// 工具类，不允许实例化。
    private M3MotionSupport() {
    }

    /// 安装一次监听；重复调用无副作用。
    public static void install() {
        if (installed) {
            return;
        }
        try {
            apply();
            SettingsManager.settings().animationDisabledProperty().addListener(
                    (observable, oldValue, newValue) -> apply());
            installed = true;
        } catch (Throwable e) {
            LOG.warning("Failed to install M3 motion bridge", e);
        }
    }

    /// 把当前 HMCL 动画设置与社区减弱动效开关写入 m3fx。
    private static void apply() {
        try {
            boolean disabled = SettingsManager.settings().animationDisabledProperty().get();
            M3MotionSettings.setAnimationsEnabled(!disabled && !M3Settings.isReducedMotion());
        } catch (Throwable e) {
            LOG.warning("Failed to apply M3 motion setting", e);
        }
    }
}
