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

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 用户自定义下载源集合：1 个可选的主源 + 最多 5 个镜像源。
///
/// 默认（空集合）不内置任何第三方镜像，只使用官方 Mojang 源；
/// 配置后候选顺序为 主源 → 镜像源（按配置顺序）→ 官方源。
///
/// 数据存放在 `<本地根>/config/community-settings.json` 的 `downloadSources` 段：
/// ```json
/// {
///   "downloadSources": {
///     "schema": 1,
///     "primary": { "name": "示例", "url": "https://example.com" },
///     "mirrors": [ { "name": "镜像 1", "url": "https://mirror.example.com" } ]
///   }
/// }
/// ```
@NotNullByDefault
public final class CommunityDownloadSources {

    /// 配置段名。
    public static final String SECTION = "downloadSources";

    /// 镜像源数量上限。
    public static final int MAX_MIRRORS = 5;

    /// 当前 schema 版本。
    public static final int SCHEMA = 1;

    /// 未配置主源。
    private static final @Nullable CommunityDownloadSource NO_PRIMARY = null;

    /// 主源（可为 `null`）。
    private final @Nullable CommunityDownloadSource primary;

    /// 镜像源（不可变，按用户顺序）。
    private final @NotNull List<CommunityDownloadSource> mirrors;

    /// 构造下载源集合。
    ///
    /// @param primary 主源或 `null`
    /// @param mirrors 镜像源，超过 [CommunityDownloadSources#MAX_MIRRORS] 时截断
    public CommunityDownloadSources(@Nullable CommunityDownloadSource primary,
                                    @NotNull List<CommunityDownloadSource> mirrors) {
        this.primary = primary;
        this.mirrors = List.copyOf(mirrors.size() > MAX_MIRRORS ? mirrors.subList(0, MAX_MIRRORS) : mirrors);
    }

    /// 返回空集合（仅官方源）。
    ///
    /// @return 空集合
    @NotNull
    public static CommunityDownloadSources empty() {
        return new CommunityDownloadSources(NO_PRIMARY, List.of());
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
    /// @param section JSON 段落
    /// @return 下载源集合
    @NotNull
    public static CommunityDownloadSources parse(@NotNull JsonObject section) {
        Set<String> seen = new LinkedHashSet<>();
        CommunityDownloadSource parsedPrimary = null;
        if (section.get("primary") instanceof JsonObject object) {
            CommunityDownloadSource source = CommunityDownloadSource.fromJson(object);
            if (source != null && seen.add(source.url())) {
                parsedPrimary = source;
            }
        }

        List<CommunityDownloadSource> parsedMirrors = new ArrayList<>();
        if (section.get("mirrors") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (parsedMirrors.size() >= MAX_MIRRORS) {
                    LOG.warning("Too many community download mirrors, extra entries ignored");
                    break;
                }
                if (element instanceof JsonObject object) {
                    CommunityDownloadSource source = CommunityDownloadSource.fromJson(object);
                    if (source != null && seen.add(source.url())) {
                        parsedMirrors.add(source);
                    }
                }
            }
        }
        return new CommunityDownloadSources(parsedPrimary, parsedMirrors);
    }

    /// 返回主源或 `null`。
    ///
    /// @return 主源
    @Nullable
    public CommunityDownloadSource primary() {
        return primary;
    }

    /// 返回镜像源（不可变）。
    ///
    /// @return 镜像源列表
    @NotNull
    public List<CommunityDownloadSource> mirrors() {
        return mirrors;
    }

    /// 返回是否未配置任何自定义源。
    ///
    /// @return 是否为空
    public boolean isEmpty() {
        return primary == null && mirrors.isEmpty();
    }

    /// 返回候选顺序：主源 → 镜像源 → 官方源（官方源由调用方追加）。
    ///
    /// @return 自定义源列表
    @NotNull
    public List<CommunityDownloadSource> orderedSources() {
        List<CommunityDownloadSource> result = new ArrayList<>(1 + mirrors.size());
        if (primary != null) {
            result.add(primary);
        }
        result.addAll(mirrors);
        return List.copyOf(result);
    }

    /// 将本集合写入社区共享设置（立即落盘）。
    public void save() {
        CommunitySettings.get().putSection(SECTION, toJson());
    }

    /// 序列化为 JSON。
    ///
    /// @return JSON 对象
    @NotNull
    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("schema", SCHEMA);
        if (primary != null) {
            object.add("primary", primary.toJson());
        }
        JsonArray array = new JsonArray();
        for (CommunityDownloadSource mirror : mirrors) {
            array.add(mirror.toJson());
        }
        object.add("mirrors", array);
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
