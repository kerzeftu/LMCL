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
import org.jackhuang.hmcl.community.config.SettingsSectionExporter;
import org.jackhuang.hmcl.community.download.CommunityDownloadInstaller;
import org.jackhuang.hmcl.community.download.CommunityDownloadRanker;
import org.jackhuang.hmcl.community.download.CommunityDownloadSources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// “下载源”设置段：与 `community-settings.json` 的 `downloadSources` 同构。
@NotNullByDefault
public final class DownloadSourcesSectionExporter implements SettingsSectionExporter {

    /// 段 id。
    public static final String ID = CommunityDownloadSources.SECTION;

    @Override
    public @NotNull String id() {
        return ID;
    }

    @Override
    public @NotNull String titleKey() {
        return "community.settings.transfer.section.download";
    }

    @Override
    public @NotNull JsonObject exportNow() {
        return CommunityDownloadSources.load().toJson();
    }

    @Override
    public void apply(@NotNull JsonObject incoming) {
        CommunityDownloadSources sources = CommunityDownloadSources.parse(incoming);
        sources.save();
        CommunityDownloadInstaller.apply();
        // 导入后立即重新测速，确保新源不会沿用旧缓存顺序。
        CommunityDownloadRanker.refreshAsync(true);
        LOG.info("Imported " + sources.size() + " community download source(s)");
    }
}
