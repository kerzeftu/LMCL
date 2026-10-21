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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/// 一次启动前体检的完整结果（只读、可序列化）。
///
/// @param schema 结构版本，当前为 1
/// @param at 体检时间
/// @param versionId 实例 id
/// @param worst 最坏严重程度；[Severity#SKIPPED] 不参与计算，全部跳过时为 [Severity#SKIPPED]
/// @param checks 各检查结果（顺序与执行顺序一致）
@NotNullByDefault
public record PreflightReport(
        int schema,
        @NotNull Instant at,
        @NotNull String versionId,
        @NotNull Severity worst,
        @NotNull List<CheckResult> checks) {

    /// 当前结构版本。
    public static final int CURRENT_SCHEMA = 1;

    /// 创建报告并自动计算最坏严重程度。
    ///
    /// @param versionId 实例 id
    /// @param checks 检查结果
    /// @return 报告
    @NotNull
    public static PreflightReport create(@NotNull String versionId, @NotNull List<CheckResult> checks) {
        return new PreflightReport(
                CURRENT_SCHEMA,
                Instant.now(),
                versionId,
                worstOf(checks),
                List.copyOf(checks));
    }

    /// 计算最坏严重程度：ERROR > WARN > OK；SKIPPED 忽略。
    ///
    /// @param checks 检查结果
    /// @return 最坏值
    @NotNull
    public static Severity worstOf(@NotNull List<CheckResult> checks) {
        Severity worst = null;
        for (CheckResult check : checks) {
            if (check.severity() == Severity.SKIPPED) {
                continue;
            }
            worst = worst == null ? check.severity() : Severity.worst(worst, check.severity());
        }
        return worst == null ? Severity.SKIPPED : worst;
    }

    /// 返回是否存在用户可见的问题（WARN/ERROR）。
    ///
    /// @return 是否有问题
    public boolean hasIssues() {
        return worst == Severity.WARN || worst == Severity.ERROR;
    }

    /// 返回指定 id 的结果。
    ///
    /// @param id 检查 id
    /// @return 结果或 `null`
    @Nullable
    public CheckResult get(@NotNull String id) {
        for (CheckResult check : checks) {
            if (check.id().equals(id)) {
                return check;
            }
        }
        return null;
    }

    /// 序列化为 JSON。
    ///
    /// @return JSON 对象
    @NotNull
    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("schema", schema);
        object.addProperty("at", at.toString());
        object.addProperty("versionId", versionId);
        object.addProperty("worst", worst.name());
        JsonArray array = new JsonArray();
        for (CheckResult check : checks) {
            array.add(check.toJson());
        }
        object.add("checks", array);
        return object;
    }

    /// 从 JSON 反序列化；结构不合法时返回 `null`。
    ///
    /// @param object JSON 对象
    /// @return 报告或 `null`
    @Nullable
    public static PreflightReport fromJson(@NotNull JsonObject object) {
        int schema = object.has("schema") ? object.get("schema").getAsInt() : 0;
        Instant at;
        try {
            at = Instant.parse(object.get("at").getAsString());
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
        String versionId = object.has("versionId") ? object.get("versionId").getAsString() : "";
        List<CheckResult> checks = new ArrayList<>();
        if (object.get("checks") instanceof JsonArray array) {
            for (int i = 0; i < array.size(); i++) {
                if (array.get(i) instanceof JsonObject checkObject) {
                    checks.add(CheckResult.fromJson(checkObject));
                }
            }
        }
        Severity worst = Severity.parse(object.has("worst") ? object.get("worst").getAsString() : null);
        return new PreflightReport(
                schema,
                at,
                versionId,
                worst == null ? worstOf(checks) : worst,
                List.copyOf(checks));
    }
}
