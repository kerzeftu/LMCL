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
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// 组件持久化偏好：落在 `dashboard.json` 的 `widgetProps[id]` 段；
/// 置顶实例列表走顶层 `pinnedInstanceIds`。
///
/// 所有写操作只改内存并触发 `dirty` 回调；真正落盘由 [DashboardPane] 统一负责
/// （编辑“完成”、`pinnedInstanceIds` 变更后 1 秒防抖、退出三处）。
@NotNullByDefault
public interface DashboardPrefs {

    /// 读取一个字符串偏好。
    ///
    /// @param key 偏好 key（组件自行加 id 前缀，如 `launch.showVersion`）
    /// @return 值；不存在或类型不符时返回 `null`
    @Nullable
    String getString(@NotNull String key);

    /// 读取一个布尔偏好。
    ///
    /// @param key 偏好 key
    /// @param defaultValue 不存在时使用的默认值
    /// @return 值
    boolean getBoolean(@NotNull String key, boolean defaultValue);

    /// 读取一个字符串列表偏好。
    ///
    /// @param key 偏好 key
    /// @return 字符串列表；不存在或类型不符时返回空列表（不返回 `null`）
    @NotNull
    @Unmodifiable
    List<String> getStringList(@NotNull String key);

    /// 写入一个偏好。
    ///
    /// @param key 偏好 key
    /// @param value 可被 Gson 序列化的标量/数组/对象；传 `null` 表示删除该 key
    void put(@NotNull String key, @Nullable Object value);

    /// 返回仪表盘置顶实例 id 列表（`dashboard.json` 顶层字段）。
    ///
    /// @return 实例 id 列表
    @NotNull
    @Unmodifiable
    List<String> pinnedInstanceIds();

    /// 覆盖置顶实例 id 列表；只改内存，调用方需自行触发落盘。
    ///
    /// @param ids 实例 id 列表
    void setPinnedInstanceIds(@NotNull List<String> ids);
}
