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

import java.util.Objects;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 导入预览用的值格式化工具。
///
/// 目标是把 JSON 值变成人能直接读懂的短文本，而不是把配置原文直接展示给用户。
@NotNullByDefault
public final class SettingsPreviewValues {

    private SettingsPreviewValues() {
    }

    /// 把布尔值格式化为“开启/关闭”。
    ///
    /// @param value 布尔值
    /// @return 本地化文本
    @NotNull
    public static String onOff(boolean value) {
        return i18n(value
                ? "community.settings.transfer.value.on"
                : "community.settings.transfer.value.off");
    }

    /// 返回 JSON 元素的本地化短描述。
    ///
    /// 基本类型直接返回可读值；数组/对象只返回条目数量，避免把整个配置块塞进一行。
    ///
    /// @param element JSON 元素，可为 `null`
    /// @return 可读文本，`null` 表示字段不存在
    @Nullable
    public static String describe(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return onOff(primitive.getAsBoolean());
            }
            if (primitive.isString()) {
                String value = primitive.getAsString();
                return value.isEmpty() ? i18n("community.settings.transfer.value.empty") : value;
            }
            return primitive.getAsString();
        }
        if (element.isJsonArray()) {
            return i18n("community.settings.transfer.value.items", element.getAsJsonArray().size());
        }
        if (element.isJsonObject()) {
            return i18n("community.settings.transfer.value.fields", element.getAsJsonObject().size());
        }
        return element.toString();
    }

    /// 判断两个 JSON 值在预览层面是否相同。
    ///
    /// 使用结构化比较；缺失字段与 `null` 视为相同。
    ///
    /// @param first 第一个值
    /// @param second 第二个值
    /// @return 相同时返回 `true`
    public static boolean same(@Nullable JsonElement first, @Nullable JsonElement second) {
        return Objects.equals(normalize(first), normalize(second));
    }

    /// 取字符串值；类型不匹配时返回 `null`。
    ///
    /// @param element JSON 元素
    /// @return 字符串或 `null`
    @Nullable
    public static String string(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }

    /// 取布尔值；类型不匹配时返回 `null`。
    ///
    /// @param element JSON 元素
    /// @return 布尔值或 `null`
    @Nullable
    public static Boolean bool(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()
                ? element.getAsBoolean() : null;
    }

    /// 取数字值；类型不匹配时返回 `null`。
    ///
    /// @param element JSON 元素
    /// @return 数字或 `null`
    @Nullable
    public static Double number(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsDouble() : null;
    }

    private static @Nullable JsonElement normalize(@Nullable JsonElement element) {
        return element != null && element.isJsonNull() ? null : element;
    }
}
