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
package org.jackhuang.hmcl.community.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 用户自定义的下载源集合：最多 5 个平级源，没有主源/镜像之分。
///
/// 默认（空集合）不内置任何下载源，包括 Mojang 官方源；未配置时下载入口
/// 会明确提示先配置。下载时由 [CommunityDownloadRanker] 对全部源测速，
/// 按延迟从低到高排序后再交给 [org.jackhuang.hmcl.download.AutoDownloadProvider]
/// 作为候选顺序；配置里的顺序只作为测速失败时的兜底顺序。
///
/// 数据存放在 `<本地根>/config/community-settings.json` 的 `downloadSources` 段：
/// ```json
/// {
///   "downloadSources": {
///     "schema": 2,
///     "sources": [
///       { "name": "BMCLAPI", "url": "https://bmclapi2.bangbang93.com" },
///       { "name": "示例镜像", "url": "https://mirror.example.com" }
///     ]
///   }
/// }
/// ```
@NotNullByDefault
public final class CommunityDownloadSources {

    /// 配置段名。
    public static final String SECTION = "downloadSources";

    /// 下载源数量上限。
    public static final int MAX_SOURCES = 5;

    /// 当前 schema 版本。
    public static final int SCHEMA = 2;

    /// 保存代数；每次 [CommunityDownloadSources#save()] 自增，供设置页判断是否需要刷新。
    private static final AtomicLong REVISION = new AtomicLong();

    /// 下载源（不可变，按用户配置顺序）。
    private final @NotNull List<CommunityDownloadSource> sources;

    /// 构造下载源集合。
    ///
    /// @param sources 下载源，超过 [CommunityDownloadSources#MAX_SOURCES] 时截断
    public CommunityDownloadSources(@NotNull List<CommunityDownloadSource> sources) {
        this.sources = List.copyOf(sources.size() > MAX_SOURCES ? sources.subList(0, MAX_SOURCES) : sources);
    }

    /// 当前保存代数；导入或保存后变化，设置页据此刷新列表而不覆盖未保存的编辑。
    ///
    /// @return 代数
    public static long revision() {
        return REVISION.get();
    }

    /// 返回空集合（未配置任何下载源）。
    ///
    /// @return 空集合
    @NotNull
    public static CommunityDownloadSources empty() {
        return new CommunityDownloadSources(List.of());
    }

    /// 从社区共享设置读取。
    ///
    /// @return 下载源集合；读取失败时返回空集合
    @NotNull
    public static CommunityDownloadSources load() {
        try {
            return parse(CommunitySettings.get().section(SECTION));
        } catch (Throwable e) {
            LOG.warning("Failed to load community download sources", e);
            return empty();
        }
    }

    /// 解析 JSON 段落；非法条目被丢弃，重复地址只保留第一次出现。
    ///
    /// 兼容 v1 的 `primary` + `mirrors` 结构：按“主源、镜像 1..n”顺序合并为
    /// 平级源，超过 5 个时截断。写出时统一使用 v2 的 `sources` 数组。
    ///
    /// @param section JSON 段落
    /// @return 下载源集合
    @NotNull
    public static CommunityDownloadSources parse(@NotNull JsonObject section) {
        List<CommunityDownloadSource> parsed = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (section.get("sources") instanceof JsonArray array) {
            parseArray(array, parsed, seen);
        } else {
            parseLegacy(section, parsed, seen);
        }

        return new CommunityDownloadSources(parsed);
    }

    /// 解析 v2 的 `sources` 数组。
    private static void parseArray(@NotNull JsonArray array,
                                   @NotNull List<CommunityDownloadSource> parsed,
                                   @NotNull Set<String> seen) {
        for (JsonElement element : array) {
            if (parsed.size() >= MAX_SOURCES) {
                LOG.warning("Too many community download sources, extra entries ignored");
                break;
            }
            if (element instanceof JsonObject object) {
                CommunityDownloadSource source = CommunityDownloadSource.fromJson(object);
                if (source != null && seen.add(source.url())) {
                    parsed.add(source);
                }
            }
        }
    }

    /// 解析 v1 的 `primary` + `mirrors` 结构。
    private static void parseLegacy(@NotNull JsonObject section,
                                    @NotNull List<CommunityDownloadSource> parsed,
                                    @NotNull Set<String> seen) {
        if (section.get("primary") instanceof JsonObject object) {
            CommunityDownloadSource source = CommunityDownloadSource.fromJson(object);
            if (source != null && seen.add(source.url())) {
                parsed.add(source);
            }
        }
        if (section.get("mirrors") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (parsed.size() >= MAX_SOURCES) {
                    LOG.warning("Too many community download sources, extra entries ignored");
                    break;
                }
                if (element instanceof JsonObject object) {
                    CommunityDownloadSource source = CommunityDownloadSource.fromJson(object);
                    if (source != null && seen.add(source.url())) {
                        parsed.add(source);
                    }
                }
            }
        }
    }

    /// 返回全部下载源（不可变，按配置顺序）。
    ///
    /// @return 下载源列表
    @NotNull
    public List<CommunityDownloadSource> sources() {
        return sources;
    }

    /// 返回下载源数量。
    ///
    /// @return 数量
    public int size() {
        return sources.size();
    }

    /// 返回是否未配置任何自定义源。
    ///
    /// @return 是否为空
    public boolean isEmpty() {
        return sources.isEmpty();
    }

    /// 返回全部下载源；保留该名称以兼容既有调用方，语义等同于 [CommunityDownloadSources#sources()]。
    ///
    /// @return 下载源列表
    @NotNull
    public List<CommunityDownloadSource> orderedSources() {
        return sources;
    }

    /// 将本集合写入社区共享设置（立即落盘）。
    public void save() {
        CommunitySettings.get().putSection(SECTION, toJson());
        REVISION.incrementAndGet();
    }

    /// 序列化为 JSON（schema 2）。
    ///
    /// @return JSON 对象
    @NotNull
    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("schema", SCHEMA);
        JsonArray array = new JsonArray();
        for (CommunityDownloadSource source : sources) {
            array.add(source.toJson());
        }
        object.add("sources", array);
        return object;
    }

    /// 校验一组规范化地址：重复时返回重复的地址，否则返回 `null`。
    ///
    /// @param urls 已规范化的地址（`null` 表示该槽位留空）
    /// @return 重复地址或 `null`
    @Nullable
    public static String findDuplicate(@NotNull List<@Nullable String> urls) {
        Set<String> seen = new LinkedHashSet<>();
        for (String url : urls) {
            if (url == null || url.isEmpty()) {
                continue;
            }
            if (!seen.add(url)) {
                return url;
            }
        }
        return null;
    }
}
