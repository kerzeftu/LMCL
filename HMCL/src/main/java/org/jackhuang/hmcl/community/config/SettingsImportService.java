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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 导入 `.lmcl.json`：先生成预览，用户确认后逐段应用。
///
/// 应用顺序与导出器顺序一致；单个段失败只记录并继续后续段，不回滚已成功的段。
@NotNullByDefault
public final class SettingsImportService {

    private SettingsImportService() {
    }

    /// 生成导入预览。
    ///
    /// @param bundle 设置包
    /// @return 预览
    @NotNull
    public static ImportPreview preview(@NotNull CommunitySettingsBundle bundle) {
        List<SettingsChange> changes = new ArrayList<>();
        List<String> warnings = new ArrayList<>(bundle.warnings());
        List<String> errors = new ArrayList<>();

        for (SettingsSectionExporter exporter : SettingsExportService.exporters()) {
            JsonObject incoming = bundle.sections().get(exporter.id());
            if (incoming == null) {
                continue;
            }
            try {
                changes.addAll(exporter.preview(incoming));
            } catch (Throwable e) {
                LOG.warning("Failed to preview settings section " + exporter.id(), e);
                errors.add(exporter.id() + ": " + rootMessage(e));
            }
        }
        return new ImportPreview(bundle.sections().size(), List.copyOf(changes),
                List.copyOf(warnings), List.copyOf(errors));
    }

    /// 应用全部导入段。
    ///
    /// @param bundle 设置包
    /// @return 应用结果
    @NotNull
    public static ImportResult apply(@NotNull CommunitySettingsBundle bundle) {
        List<String> applied = new ArrayList<>();
        List<SectionFailure> failures = new ArrayList<>();

        for (SettingsSectionExporter exporter : SettingsExportService.exporters()) {
            JsonObject incoming = bundle.sections().get(exporter.id());
            if (incoming == null) {
                continue;
            }
            try {
                exporter.apply(incoming);
                applied.add(exporter.id());
            } catch (Throwable e) {
                LOG.warning("Failed to apply settings section " + exporter.id(), e);
                failures.add(new SectionFailure(exporter.id(), rootMessage(e)));
            }
        }
        return new ImportResult(List.copyOf(applied), List.copyOf(failures));
    }

    /// 取异常链上最具体的消息。
    ///
    /// @param throwable 异常
    /// @return 消息
    @NotNull
    static String rootMessage(@NotNull Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message != null ? message : throwable.getClass().getSimpleName();
    }

    /// 导入预览。
    ///
    /// @param sectionCount 文件中的段数
    /// @param changes 字段级变更
    /// @param warnings 非致命警告
    /// @param errors 预览阶段错误
    @NotNullByDefault
    public record ImportPreview(
            int sectionCount,
            @NotNull @Unmodifiable List<SettingsChange> changes,
            @NotNull @Unmodifiable List<String> warnings,
            @NotNull @Unmodifiable List<String> errors) {
    }

    /// 单个段应用失败。
    ///
    /// @param sectionId 段 id
    /// @param message 错误消息
    @NotNullByDefault
    public record SectionFailure(@NotNull String sectionId, @NotNull String message) {
    }

    /// 应用结果。
    ///
    /// @param applied 成功应用的段 id
    /// @param failures 失败段
    @NotNullByDefault
    public record ImportResult(
            @NotNull @Unmodifiable List<String> applied,
            @NotNull @Unmodifiable List<SectionFailure> failures) {

        /// 是否全部成功。
        ///
        /// @return `failures` 为空时返回 `true`
        public boolean success() {
            return failures.isEmpty();
        }
    }
}
