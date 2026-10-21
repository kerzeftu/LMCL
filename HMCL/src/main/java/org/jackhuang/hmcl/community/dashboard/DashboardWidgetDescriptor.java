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
package org.jackhuang.hmcl.community.dashboard;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// 仪表盘组件元数据。
///
/// @param id 全局唯一，小写 kebab-case（正则 `[a-z0-9-]+`），不得包含 `'.'`
/// @param nameKey i18n 名称 key
/// @param descriptionKey i18n 描述 key（可为 `null`）
/// @param defaultColumns 默认列跨度（`1` 或 `2`）
/// @param defaultRows 默认行跨度（`1` 或 `2`）
/// @param order 默认排序权重（越小越靠前）
@NotNullByDefault
public record DashboardWidgetDescriptor(
        @NotNull String id,
        @NotNull String nameKey,
        @Nullable String descriptionKey,
        int defaultColumns,
        int defaultRows,
        int order) {

    /// 校验描述是否满足框架约束。
    ///
    /// @return 合法时返回 `true`
    public boolean isValid() {
        return DashboardItem.isValidId(id)
                && defaultColumns >= 1 && defaultColumns <= 2
                && defaultRows >= 1 && defaultRows <= 2
                && order >= 0;
    }
}
