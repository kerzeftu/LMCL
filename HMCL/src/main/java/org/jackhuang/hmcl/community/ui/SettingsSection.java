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
package org.jackhuang.hmcl.community.ui;

import javafx.scene.Parent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/// 一个设置分组。
///
/// `content` 只在首次选中时调用，允许返回 `null`（隐藏该分组）。
///
/// @param id 全局唯一分组 id（小写 kebab-case）
/// @param i18nKey 分组显示名的 i18n key
/// @param order 排序权重（越小越靠前；内置分组为 100 以内，扩展分组建议 100+）
/// @param content 内容提供者
@NotNullByDefault
public record SettingsSection(
        @NotNull String id,
        @NotNull String i18nKey,
        int order,
        @NotNull Supplier<@Nullable Parent> content) {
}
