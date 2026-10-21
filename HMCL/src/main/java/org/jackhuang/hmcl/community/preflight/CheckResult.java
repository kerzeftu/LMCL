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
package org.jackhuang.hmcl.community.preflight;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/// 单项检查的结果。
///
/// @param id 检查 id（`java.compat`、`memory.budget`、`disk.space`、`mods.duplicate`、`mods.missing_deps`）
/// @param severity 严重程度
/// @param code i18n 后缀（如 `java_low`、`memory_high`、`disk_low`、`duplicate_mods`、`missing_deps`、`timeout`）
/// @param elapsedMillis 耗时（毫秒）
/// @param details 供 UI 渲染的占位参数（如剩余空间、分配内存）
@NotNullByDefault
public record CheckResult(
        @NotNull String id,
        @NotNull Severity severity,
        @NotNull String code,
        long elapsedMillis,
        @NotNull Map<String, String> details) {

    /// 创建结果并复制参数表。
    ///
    /// @param id 检查 id
    /// @param severity 严重程度
    /// @param code i18n 后缀
    /// @param elapsedMillis 耗时
    /// @param details 参数表，可为 `null`
    /// @return 结果
    @NotNull
    public static CheckResult of(
            @NotNull String id,
            @NotNull Severity severity,
            @NotNull String code,
            long elapsedMillis,
            @NotNull Map<String, String> details) {
        return new CheckResult(id, severity, code, elapsedMillis, Map.copyOf(details));
    }

    /// 创建只带参数表的结果（耗时为 0）。
    ///
    /// @param id 检查 id
    /// @param severity 严重程度
    /// @param code i18n 后缀
    /// @param details 参数表
    /// @return 结果
    @NotNull
    public static CheckResult of(
            @NotNull String id,
            @NotNull Severity severity,
            @NotNull String code,
            @NotNull Map<String, String> details) {
        return of(id, severity, code, 0, details);
    }

    /// 创建只带一个参数的结果。
    ///
    /// @param id 检查 id
    /// @param severity 严重程度
    /// @param code i18n 后缀
    /// @param key 参数名
    /// @param value 参数值
    /// @return 结果
    @NotNull
    public static CheckResult of(
            @NotNull String id,
            @NotNull Severity severity,
            @NotNull String code,
            @NotNull String key,
            @NotNull String value) {
        return of(id, severity, code, 0, Map.of(key, value));
    }

    /// 序列化为 JSON。
    ///
    /// @return JSON 对象
    @NotNull
    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("severity", severity.name());
        object.addProperty("code", code);
        object.addProperty("elapsedMillis", elapsedMillis);
        JsonObject detailObject = new JsonObject();
        details.forEach(detailObject::addProperty);
        object.add("details", detailObject);
        return object;
    }

    /// 从 JSON 反序列化；字段缺失时使用安全缺省值。
    ///
    /// @param object JSON 对象
    /// @return 结果
    @NotNull
    public static CheckResult fromJson(@NotNull JsonObject object) {
        Map<String, String> details = new LinkedHashMap<>();
        if (object.get("details") instanceof JsonObject detailObject) {
            for (String key : detailObject.keySet()) {
                details.put(key, detailObject.get(key).getAsString());
            }
        }
        return new CheckResult(
                object.has("id") ? object.get("id").getAsString() : "unknown",
                Severity.parse(object.has("severity") ? object.get("severity").getAsString() : null) == null
                        ? Severity.SKIPPED
                        : Severity.parse(object.get("severity").getAsString()),
                object.has("code") ? object.get("code").getAsString() : "unknown",
                object.has("elapsedMillis") ? object.get("elapsedMillis").getAsLong() : 0,
                Collections.unmodifiableMap(details));
    }
}
