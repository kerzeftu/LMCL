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
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 仪表盘布局引擎：**纯函数**，不引用 JavaFX，便于单测。
///
/// 算法是确定性的、无回溯的“行优先首次适配”：
/// 逐项（按 `order` 稳定排序）从第 0 行开始扫描，找到第一个矩形区域全空的
/// `(row, column)` 放下；放不下就追加到当前已占用的最后一行之后。
/// 结果只依赖输入，因此可以对着单元测试逐条断言。
@NotNullByDefault
public final class DashboardLayout {

    /// 卡片之间的间距（像素）。
    public static final int GAP = 12;

    /// 单行卡片的高度（像素）。
    public static final int ROW_HEIGHT = 120;

    /// 单列卡片的建议最小宽度（像素）；窗口更窄时列数强制为 1。
    public static final int MIN_TILE_WIDTH = 180;

    /// 允许的最大行数；超出的布局项进入“异常区”，不渲染。
    public static final int MAX_ROWS = 64;

    /// 布局结果中的单个已放置项。
    ///
    /// @param item 布局项
    /// @param column 起始列（0 起）
    /// @param row 起始行（0 起）
    /// @param columnSpan 实际列跨度
    /// @param rowSpan 实际行跨度
    public record PlacedItem(@NotNull DashboardItem item, int column, int row, int columnSpan, int rowSpan) {

        /// 返回该项占据的列区间终点（不含）。
        ///
        /// @return `column + columnSpan`
        public int columnEnd() {
            return column + columnSpan;
        }

        /// 返回该项占据的行区间终点（不含）。
        ///
        /// @return `row + rowSpan`
        public int rowEnd() {
            return row + rowSpan;
        }
    }

    /// 一次布局计算的完整结果。
    ///
    /// @param placed 已放置项，顺序即渲染顺序
    /// @param rejected 因 `MAX_ROWS` 越界或 id 重复而未放置的组件 id
    /// @param rows 占用的总行数
    public record LayoutResult(
            @NotNull @Unmodifiable List<PlacedItem> placed,
            @NotNull @Unmodifiable List<String> rejected,
            int rows) {
    }

    /// 本类只提供静态方法。
    private DashboardLayout() {
    }

    /// 按容器宽度计算自动列数：`< 700` → 1；`< 1000` → 2；`< 1400` → 3；否则 4。
    ///
    /// 宽度无效（`NaN`/无穷/小于 [MIN_TILE_WIDTH]）时强制返回 1，
    /// 避免首帧宽度为 0 时把卡片挤成 4 列。
    ///
    /// @param width 容器宽度（像素）
    /// @return 列数，`1..4`
    public static int autoColumns(double width) {
        if (!Double.isFinite(width) || width < MIN_TILE_WIDTH) {
            return 1;
        }
        if (width < 700) {
            return 1;
        }
        if (width < 1000) {
            return 2;
        }
        if (width < 1400) {
            return 3;
        }
        return 4;
    }

    /// 计算布局结果。
    ///
    /// 规则：只布局 `enabled = true` 的项；id 非法的项跳过；重复 id 先者生效；
    /// 跨度收敛到 `[1, columns]`；项数超过 [MAX_ROWS] 后进入 `rejected`。
    ///
    /// @param items 布局项（可为未排序、含隐藏项）
    /// @param columns 列数，小于 1 时按 1 处理
    /// @return 布局结果
    @NotNull
    public static LayoutResult computeResult(@NotNull List<DashboardItem> items, int columns) {
        int columnCount = Math.max(1, columns);

        // 稳定排序：order 相同保持输入顺序（List.sort 是稳定排序）。
        List<DashboardItem> ordered = new ArrayList<>();
        for (DashboardItem item : items) {
            if (item.enabled()) {
                ordered.add(item);
            }
        }
        ordered.sort(Comparator.comparingInt(DashboardItem::order));

        List<PlacedItem> placed = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        List<boolean[]> occupied = new ArrayList<>();

        for (DashboardItem item : ordered) {
            if (!DashboardItem.isValidId(item.id())) {
                rejected.add(item.id());
                continue;
            }
            if (!seenIds.add(item.id())) {
                rejected.add(item.id());
                continue;
            }

            int columnSpan = Math.min(DashboardItem.clampSpan(item.colSpan()), columnCount);
            int rowSpan = DashboardItem.clampSpan(item.rowSpan());

            int placedRow = -1;
            int placedColumn = -1;

            // 逐行扫描；行数上限 + 当前行数之后还有一行“尾部空行”用于追加。
            for (int row = 0; row <= occupied.size() && placedRow < 0; row++) {
                for (int column = 0; column + columnSpan <= columnCount; column++) {
                    if (isFree(occupied, row, column, columnSpan, rowSpan)) {
                        placedRow = row;
                        placedColumn = column;
                        break;
                    }
                }
            }

            if (placedRow < 0) {
                rejected.add(item.id());
                continue;
            }
            if (placedRow + rowSpan > MAX_ROWS) {
                rejected.add(item.id());
                continue;
            }

            // 标记占用，按需增长矩阵。
            while (occupied.size() < placedRow + rowSpan) {
                occupied.add(new boolean[columnCount]);
            }
            for (int row = placedRow; row < placedRow + rowSpan; row++) {
                boolean[] cells = occupied.get(row);
                for (int column = placedColumn; column < placedColumn + columnSpan; column++) {
                    cells[column] = true;
                }
            }

            placed.add(new PlacedItem(item, placedColumn, placedRow, columnSpan, rowSpan));
        }

        return new LayoutResult(List.copyOf(placed), List.copyOf(rejected), occupied.size());
    }

    /// 判断矩形区域是否空闲；矩阵之外的行视为空闲。
    ///
    /// @param occupied 已占用矩阵
    /// @param row 起始行
    /// @param column 起始列
    /// @param columnSpan 列跨度
    /// @param rowSpan 行跨度
    /// @return 全部空闲时返回 `true`
    private static boolean isFree(
            @NotNull List<boolean[]> occupied,
            int row,
            int column,
            int columnSpan,
            int rowSpan) {
        for (int r = row; r < row + rowSpan; r++) {
            if (r >= occupied.size()) {
                return true;
            }
            boolean[] cells = occupied.get(r);
            for (int c = column; c < column + columnSpan; c++) {
                if (cells[c]) {
                    return false;
                }
            }
        }
        return true;
    }
}
