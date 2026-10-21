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
 *
 * Modified by LMCL team on 2026-10-22: data source for the About LMCL page.
 */
package org.jackhuang.hmcl.community.ui.about;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 关于 LMCL 页的数据源。
///
/// 致谢列表直接读取上游 `assets/about/thanks.json`，因此上游新增贡献者后
/// 重新构建即可同步，不在 LMCL 中维护第二份名单。捐赠者列表读取
/// `assets/about/lmcl-donors.json`，当前为空，只用于未来扩展。
///
/// 线程：`load()` 会读 classpath，应在 IO 线程调用；解析方法是纯函数，可单测。
@NotNullByDefault
public final class AboutData {

    /// 上游致谢列表路径。
    public static final String THANKS_RESOURCE = "/assets/about/thanks.json";

    /// LMCL 捐赠者列表路径。
    public static final String DONORS_RESOURCE = "/assets/about/lmcl-donors.json";

    /// 一条致谢记录。
    ///
    /// @param image 头像（字符串或 `{light, dark}` 对象），可为 `null`
    /// @param title 直接标题，可为 `null`
    /// @param titleLocalized 标题 i18n key，可为 `null`
    /// @param subtitle 直接副标题，可为 `null`
    /// @param subtitleLocalized 副标题 i18n key，可为 `null`
    /// @param externalLink 外部链接，可为 `null`
    public record Entry(@Nullable JsonElement image,
                        @Nullable String title,
                        @Nullable String titleLocalized,
                        @Nullable String subtitle,
                        @Nullable String subtitleLocalized,
                        @Nullable String externalLink) {
    }

    /// 一条捐赠者记录。
    ///
    /// @param name 名称
    /// @param note 备注，可为 `null`
    /// @param link 链接，可为 `null`
    public record Donor(@NotNull String name, @Nullable String note, @Nullable String link) {
    }

    private final List<Entry> thanks;
    private final List<Donor> donors;

    private AboutData(@NotNull List<Entry> thanks, @NotNull List<Donor> donors) {
        this.thanks = List.copyOf(thanks);
        this.donors = List.copyOf(donors);
    }

    /// 致谢列表（全部条目，不做截断）。
    ///
    /// @return 致谢列表
    @NotNull
    public List<Entry> thanks() {
        return thanks;
    }

    /// 捐赠者列表。
    ///
    /// @return 捐赠者列表
    @NotNull
    public List<Donor> donors() {
        return donors;
    }

    /// 读取并解析两个资源；单个资源失败只返回空列表，不影响另一个。
    ///
    /// @return 数据
    @NotNull
    public static AboutData load() {
        return new AboutData(loadEntries(THANKS_RESOURCE), loadDonors(DONORS_RESOURCE));
    }

    /// 从 classpath 读取致谢列表。
    @NotNull
    private static List<Entry> loadEntries(@NotNull String path) {
        JsonArray array = readArray(path);
        return array == null ? List.of() : parseThanks(array);
    }

    /// 从 classpath 读取捐赠者列表。
    @NotNull
    private static List<Donor> loadDonors(@NotNull String path) {
        JsonArray array = readArray(path);
        return array == null ? List.of() : parseDonors(array);
    }

    /// 读取 JSON 数组；文件缺失或格式错误返回 `null`。
    @Nullable
    private static JsonArray readArray(@NotNull String path) {
        try (InputStream input = AboutData.class.getResourceAsStream(path)) {
            if (input == null) {
                LOG.warning("About resource not found: " + path);
                return null;
            }
            return JsonUtils.fromJsonFully(input, JsonArray.class);
        } catch (IOException | RuntimeException e) {
            LOG.warning("Failed to read about resource: " + path, e);
            return null;
        }
    }

    /// 解析致谢数组；跳过非对象、没有标题的条目。
    ///
    /// @param array JSON 数组
    /// @return 条目列表
    @NotNull
    static List<Entry> parseThanks(@NotNull JsonArray array) {
        List<Entry> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject obj = element.getAsJsonObject();
            String title = string(obj, "title");
            String titleLocalized = string(obj, "titleLocalized");
            if (title == null && titleLocalized == null) {
                continue;
            }

            result.add(new Entry(
                    obj.has("image") ? obj.get("image") : null,
                    title,
                    titleLocalized,
                    string(obj, "subtitle"),
                    string(obj, "subtitleLocalized"),
                    string(obj, "externalLink")
            ));
        }
        return result;
    }

    /// 解析捐赠者数组；跳过非对象与空名称。
    ///
    /// @param array JSON 数组
    /// @return 捐赠者列表
    @NotNull
    static List<Donor> parseDonors(@NotNull JsonArray array) {
        List<Donor> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject obj = element.getAsJsonObject();
            String name = string(obj, "name");
            if (name == null || name.isBlank()) {
                continue;
            }
            result.add(new Donor(name, string(obj, "note"), string(obj, "link")));
        }
        return result;
    }

    /// 读取可选字符串。
    @Nullable
    private static String string(@NotNull JsonObject obj, @NotNull String name) {
        if (obj.get(name) instanceof JsonPrimitive primitive && primitive.isString()) {
            return primitive.getAsString();
        }
        return null;
    }
}
