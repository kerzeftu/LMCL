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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// 设置面板的扩展点；后续步骤（10/11/12/15/16/17）通过 `META-INF/services` 登记自己的分组。
///
/// 约束：`sections()` 只在面板打开时调用一次，必须可在 FX 线程调用且**不得**阻塞
/// （分组内容本身延迟到用户点击时构造）。
@NotNullByDefault
public interface SettingsSectionProvider {

    /// 返回本 provider 的分组。
    ///
    /// @return 不可变列表；不允许 `null` 元素
    @NotNull
    @Unmodifiable
    List<SettingsSection> sections();
}
