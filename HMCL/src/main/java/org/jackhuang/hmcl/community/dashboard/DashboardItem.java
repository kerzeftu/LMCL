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

import java.util.regex.Pattern;

/// 仪表盘上的一个布局项。
///
/// 布局项是**可变的**：编辑模式在内存副本上修改 [DashboardItem#setOrder(int)]、
/// [DashboardItem#setColSpan(int)] 等字段，点“完成”时才写回 [DashboardConfig]；
/// 点“取消”直接丢弃副本。因此本类不实现 {@code equals}/{@code hashCode}。
@NotNullByDefault
public final class DashboardItem {

    /// 组件 id 的合法形式（小写 kebab-case，不含 `'.'`）。
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9-]+");

    /// 组件 id，见 [WidgetProvider] 的登记约定。
    private final String id;

    /// 是否渲染该布局项；`false` 表示“本次隐藏”，仍保留在配置中。
    private boolean enabled;

    /// 排序权重，越小越靠前；布局引擎按它稳定排序。
    private int order;

    /// 列跨度，取值 `1` 或 `2`。
    private int colSpan;

    /// 行跨度，取值 `1` 或 `2`。
    private int rowSpan;

    /// 创建一个布局项。
    ///
    /// @param id 组件 id（必须匹配 {@code [a-z0-9-]+}）
    /// @param enabled 是否渲染
    /// @param order 排序权重（越小越靠前）
    /// @param colSpan 列跨度，会被收敛到 `[1, 2]`
    /// @param rowSpan 行跨度，会被收敛到 `[1, 2]`
    public DashboardItem(@NotNull String id, boolean enabled, int order, int colSpan, int rowSpan) {
        this.id = id;
        this.enabled = enabled;
        this.order = order;
        this.colSpan = clampSpan(colSpan);
        this.rowSpan = clampSpan(rowSpan);
    }

    /// 判断 id 是否为合法的组件 id（`[a-z0-9-]+`）。
    ///
    /// @param id 待检查的 id，允许为 `null`
    /// @return 合法时返回 `true`
    public static boolean isValidId(@Nullable String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    /// 把跨度收敛到框架支持的范围 `[1, 2]`。
    ///
    /// @param span 原始跨度
    /// @return 收敛后的跨度
    public static int clampSpan(int span) {
        return span < 1 ? 1 : Math.min(span, 2);
    }

    /// 复制当前布局项（编辑模式用于回滚）。
    ///
    /// @return 内容相同的新实例
    @NotNull
    public DashboardItem copy() {
        return new DashboardItem(id, enabled, order, colSpan, rowSpan);
    }

    /// 返回组件 id。
    ///
    /// @return 组件 id
    @NotNull
    public String id() {
        return id;
    }

    /// 返回是否渲染该布局项。
    ///
    /// @return `true` 表示渲染
    public boolean enabled() {
        return enabled;
    }

    /// 设置是否渲染该布局项。
    ///
    /// @param enabled `true` 表示渲染
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /// 返回排序权重。
    ///
    /// @return 排序权重
    public int order() {
        return order;
    }

    /// 设置排序权重。
    ///
    /// @param order 排序权重
    public void setOrder(int order) {
        this.order = order;
    }

    /// 返回列跨度。
    ///
    /// @return 列跨度（`1` 或 `2`）
    public int colSpan() {
        return colSpan;
    }

    /// 设置列跨度；越界值会被收敛到 `[1, 2]`。
    ///
    /// @param colSpan 列跨度
    public void setColSpan(int colSpan) {
        this.colSpan = clampSpan(colSpan);
    }

    /// 返回行跨度。
    ///
    /// @return 行跨度（`1` 或 `2`）
    public int rowSpan() {
        return rowSpan;
    }

    /// 设置行跨度；越界值会被收敛到 `[1, 2]`。
    ///
    /// @param rowSpan 行跨度
    public void setRowSpan(int rowSpan) {
        this.rowSpan = clampSpan(rowSpan);
    }
}
