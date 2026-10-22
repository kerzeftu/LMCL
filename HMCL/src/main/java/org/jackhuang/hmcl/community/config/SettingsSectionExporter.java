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

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// 一类可导入/导出的启动器设置。
///
/// 导出时由 [SettingsExportService] 调用 [#exportNow()]；导入时先调用
/// [#preview(JsonObject)] 生成变更列表，用户确认后调用 [#apply(JsonObject)]。
/// 实现必须在 FX 线程调用（读取 JavaFX 属性/控件），但不得弹出阻塞对话框。
@NotNullByDefault
public interface SettingsSectionExporter {

    /// 段 id；写入 `.lmcl.json` 的 `sections` 键。
    @NotNull
    String id();

    /// 段标题的 i18n key。
    @NotNull
    String titleKey();

    /// 导出当前设置（不得返回 `null`）。
    @NotNull
    JsonObject exportNow();

    /// 计算导入后会发生的变更；默认按字段级比较。
    ///
    /// @param incoming 导入文件中的该段
    /// @return 变更列表
    @NotNull
    @Unmodifiable
    default List<SettingsChange> preview(@NotNull JsonObject incoming) {
        JsonObject current = exportNow();
        Set<String> keys = new LinkedHashSet<>(current.keySet());
        keys.addAll(incoming.keySet());
        List<SettingsChange> changes = new ArrayList<>();
        for (String key : keys) {
            String oldValue = SettingsChange.display(current.get(key));
            String newValue = SettingsChange.display(incoming.get(key));
            if (!java.util.Objects.equals(oldValue, newValue)) {
                changes.add(new SettingsChange(id(), key, oldValue, newValue));
            }
        }
        return List.copyOf(changes);
    }

    /// 应用导入的段；失败时抛出异常，由 [SettingsImportService] 汇总。
    ///
    /// @param incoming 导入文件中的该段
    void apply(@NotNull JsonObject incoming);
}
