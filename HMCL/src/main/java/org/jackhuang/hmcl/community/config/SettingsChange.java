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
package org.jackhuang.hmcl.community.config;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// 导入预览中的一条设置变更。
///
/// @param section 设置段 id（见 [SettingsSectionExporter#id()]）
/// @param key 段内字段名
/// @param oldValue 当前值的简短字符串形式
/// @param newValue 导入值的简短字符串形式；可包含换行，用于逐行展示变更说明
/// @param label 面向用户的标题；`null` 时回退到 [key]
@NotNullByDefault
public record SettingsChange(
        @NotNull String section,
        @NotNull String key,
        @Nullable String oldValue,
        @Nullable String newValue,
        @Nullable String label) {

    /// 兼容只提供字段名与值的旧构造方式。
    ///
    /// @param section 设置段 id
    /// @param key 段内字段名
    /// @param oldValue 当前值
    /// @param newValue 导入值
    public SettingsChange(@NotNull String section, @NotNull String key,
                          @Nullable String oldValue, @Nullable String newValue) {
        this(section, key, oldValue, newValue, null);
    }

    /// 把 JSON 值压缩成适合在对话框中展示的单行文本。
    ///
    /// @param element JSON 值，可为 `null`
    /// @return 单行文本，`null` 表示字段不存在
    @Nullable
    public static String display(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        String text = element.toString();
        return text.length() <= 120 ? text : text.substring(0, 117) + "...";
    }
}
