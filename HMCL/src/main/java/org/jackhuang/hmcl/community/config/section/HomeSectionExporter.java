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
package org.jackhuang.hmcl.community.config.section;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.community.config.SettingsSectionExporter;
import org.jackhuang.hmcl.community.dashboard.DashboardConfig;
import org.jackhuang.hmcl.community.dashboard.builtin.HomePane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.Path;

/// “主页布局”设置段：复用 `dashboard.json` 的 schema。
///
/// 不含 `updatedAt`；导入后重建主页左右栏。`schema > 1` 时拒绝导入。
@NotNullByDefault
public final class HomeSectionExporter implements SettingsSectionExporter {

    /// 段 id。
    public static final String ID = "home";

    private static Path configPath() {
        return Metadata.HMCL_LOCAL_HOME.resolve("config").resolve(DashboardConfig.FILE_NAME);
    }

    @Override
    public @NotNull String id() {
        return ID;
    }

    @Override
    public @NotNull String titleKey() {
        return "community.settings.transfer.section.home";
    }

    @Override
    public @NotNull JsonObject exportNow() {
        return DashboardConfig.load(configPath()).toExportJson();
    }

    @Override
    public void apply(@NotNull JsonObject incoming) {
        DashboardConfig config = DashboardConfig.load(configPath());
        if (config.readOnly()) {
            throw new IllegalStateException("dashboard configuration is read-only");
        }
        config.replaceFromJson(incoming);
        try {
            config.save();
        } catch (IOException e) {
            throw new IllegalStateException("failed to save dashboard: " + e.getMessage(), e);
        }
        HomePane.notifyConfigurationChanged();
    }
}
