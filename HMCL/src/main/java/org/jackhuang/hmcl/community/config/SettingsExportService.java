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
import org.jackhuang.hmcl.community.config.section.AppearanceSectionExporter;
import org.jackhuang.hmcl.community.config.section.DownloadSourcesSectionExporter;
import org.jackhuang.hmcl.community.config.section.HomeSectionExporter;
import org.jackhuang.hmcl.community.config.section.JavaSectionExporter;
import org.jackhuang.hmcl.community.config.section.MemorySectionExporter;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 导出 `.lmcl.json`：汇总勾选的设置段并原子写盘。
///
/// 导出器顺序固定为 下载源 → 外观 → 主页布局 → 内存 → Java，导入应用也按同一顺序。
@NotNullByDefault
public final class SettingsExportService {

    private static final List<SettingsSectionExporter> EXPORTERS = List.of(
            new DownloadSourcesSectionExporter(),
            new AppearanceSectionExporter(),
            new HomeSectionExporter(),
            new MemorySectionExporter(),
            new JavaSectionExporter());

    private SettingsExportService() {
    }

    /// 全部导出器（固定顺序）。
    ///
    /// @return 不可变列表
    @NotNull
    @Unmodifiable
    public static List<SettingsSectionExporter> exporters() {
        return EXPORTERS;
    }

    /// 按 id 查找导出器。
    ///
    /// @param id 段 id
    /// @return 导出器或 `null`
    @Nullable
    public static SettingsSectionExporter find(@NotNull String id) {
        for (SettingsSectionExporter exporter : EXPORTERS) {
            if (exporter.id().equals(id)) {
                return exporter;
            }
        }
        return null;
    }

    /// 导出指定段。
    ///
    /// @param sectionIds 段 id 集合
    /// @return 设置包
    /// @throws IllegalStateException 一个段都没选时抛出
    @NotNull
    public static CommunitySettingsBundle export(@NotNull Set<String> sectionIds) {
        Map<String, JsonObject> sections = new LinkedHashMap<>();
        for (SettingsSectionExporter exporter : EXPORTERS) {
            if (sectionIds.contains(exporter.id())) {
                sections.put(exporter.id(), exporter.exportNow());
            }
        }
        if (sections.isEmpty()) {
            throw new IllegalStateException("no settings section selected");
        }
        return CommunitySettingsBundle.create(sections);
    }

    /// 原子写入 `.lmcl.json`。
    ///
    /// @param path 目标文件
    /// @param bundle 设置包
    /// @throws IOException 写入失败
    public static void write(@NotNull Path path, @NotNull CommunitySettingsBundle bundle) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, JsonUtils.GSON.toJson(bundle.toJson()), StandardCharsets.UTF_8);
        Files.move(temp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /// 生成默认文件名 `LMCL-settings-<yyyyMMdd-HHmmss>.lmcl.json`。
    ///
    /// @return 文件名
    @NotNull
    public static String suggestedFileName() {
        return "LMCL-settings-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + CommunitySettingsBundle.FILE_SUFFIX;
    }
}
