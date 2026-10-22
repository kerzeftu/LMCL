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
import org.jackhuang.hmcl.community.config.SettingsChange;
import org.jackhuang.hmcl.community.config.SettingsSectionExporter;
import org.jackhuang.hmcl.community.download.CommunityDownloadInstaller;
import org.jackhuang.hmcl.community.download.CommunityDownloadRanker;
import org.jackhuang.hmcl.community.download.CommunityDownloadSource;
import org.jackhuang.hmcl.community.download.CommunityDownloadSources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
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
    public @NotNull @Unmodifiable List<SettingsChange> preview(@NotNull JsonObject incoming) {
        return describeChanges(CommunityDownloadSources.load(), CommunityDownloadSources.parse(incoming));
    }

    /// 把下载源差异转成可读的变更列表。
    ///
    /// 每行一个地址；只描述新增/移除/重命名/顺序调整，绝不输出段 JSON 原文。
    ///
    /// @param current 当前下载源
    /// @param incoming 导入的下载源
    /// @return 变更列表；无变化时为空
    @NotNull
    @Unmodifiable
    static List<SettingsChange> describeChanges(@NotNull CommunityDownloadSources current,
                                                @NotNull CommunityDownloadSources incoming) {
        Map<String, String> currentByUrl = byUrl(current.sources());
        Map<String, String> incomingByUrl = byUrl(incoming.sources());

        List<CommunityDownloadSource> added = new ArrayList<>();
        List<CommunityDownloadSource> removed = new ArrayList<>();
        List<CommunityDownloadSource> renamed = new ArrayList<>();
        for (CommunityDownloadSource source : incoming.sources()) {
            if (!currentByUrl.containsKey(source.url())) {
                added.add(source);
            } else if (!Objects.equals(currentByUrl.get(source.url()), source.name())) {
                renamed.add(source);
            }
        }
        for (CommunityDownloadSource source : current.sources()) {
            if (!incomingByUrl.containsKey(source.url())) {
                removed.add(source);
            }
        }

        boolean sameOrder = current.sources().stream().map(CommunityDownloadSource::url).toList()
                .equals(incoming.sources().stream().map(CommunityDownloadSource::url).toList());

        List<String> lines = new ArrayList<>();
        if (!added.isEmpty()) {
            lines.add(i18n("community.settings.transfer.preview.download.added", added.size()));
            added.forEach(source -> lines.add(source.url()));
        }
        if (!removed.isEmpty()) {
            lines.add(i18n("community.settings.transfer.preview.download.removed", removed.size()));
            removed.forEach(source -> lines.add(source.url()));
        }
        if (!renamed.isEmpty()) {
            lines.add(i18n("community.settings.transfer.preview.download.renamed", renamed.size()));
            renamed.forEach(source -> lines.add(i18n(
                    "community.settings.transfer.preview.download.rename_line",
                    source.url(), currentByUrl.get(source.url()), source.name())));
        }
        if (lines.isEmpty() && !sameOrder) {
            lines.add(i18n("community.settings.transfer.preview.download.reordered"));
            int index = 1;
            for (CommunityDownloadSource source : incoming.sources()) {
                lines.add(index++ + ". " + source.url());
            }
        }
        if (lines.isEmpty()) {
            return List.of();
        }
        return List.of(new SettingsChange(ID, "sources", null, String.join("\n", lines),
                i18n("community.settings.transfer.section.download")));
    }

    private static @NotNull Map<String, String> byUrl(@NotNull List<CommunityDownloadSource> sources) {
        Map<String, String> result = new LinkedHashMap<>();
        for (CommunityDownloadSource source : sources) {
            result.putIfAbsent(source.url(), source.name());
        }
        return result;
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
