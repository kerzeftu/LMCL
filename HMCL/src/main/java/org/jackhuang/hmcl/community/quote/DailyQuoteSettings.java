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
package org.jackhuang.hmcl.community.quote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 每日一言设置，存放在 `<本地根>/config/community-settings.json` 的 `dailyQuote` 段。
///
/// ```json
/// {
///   "dailyQuote": {
///     "enabled": true,
///     "online": true,
///     "sources": [
///       { "id": "hitokoto", "type": "hitokoto", "name": "Hitokoto", "enabled": true },
///       { "id": "jinrishici", "type": "jinrishici", "name": "Jinrishici", "enabled": false },
///       { "id": "my-api", "type": "custom", "name": "My API", "url": "https://example.com/quote", "enabled": true }
///     ]
///   }
/// }
/// ```
///
/// 缺省的 Hitokoto / 今日诗词会自动补回；自定义源按 id 保留。读取失败时使用默认值，
/// 不阻塞启动；写入失败只记录日志，由 UI 的预览状态提示用户。
@NotNullByDefault
public final class DailyQuoteSettings {

    /// 设置段名。
    public static final String SECTION = "dailyQuote";

    /// Hitokoto 源 id。
    public static final String HITOKOTO_ID = "hitokoto";
    /// 今日诗词源 id。
    public static final String JINRISHICI_ID = "jinrishici";

    private static final List<QuoteSource> DEFAULT_SOURCES = List.of(
            new QuoteSource(HITOKOTO_ID, QuoteSourceType.HITOKOTO, "Hitokoto", null, true),
            new QuoteSource(JINRISHICI_ID, QuoteSourceType.JINRISHICI, "Jinrishici", null, false));

    /// 设置快照。
    ///
    /// @param enabled 主页是否显示每日一言
    /// @param online 是否允许联网获取
    /// @param sources 全部源，包含未启用项
    public record Config(boolean enabled, boolean online, @NotNull List<QuoteSource> sources) {

        /// 复制源列表，避免调用方修改。
        public Config {
            sources = List.copyOf(sources);
        }

        /// 当前启用且可用的源。
        ///
        /// @return 启用源列表
        @NotNull
        public List<QuoteSource> enabledSources() {
            return sources.stream().filter(QuoteSource::enabled).toList();
        }

        /// 修改显示开关。
        ///
        /// @param enabled 是否显示
        /// @return 新配置
        @NotNull
        public Config withEnabled(boolean enabled) {
            return new Config(enabled, online, sources);
        }

        /// 修改联网开关。
        ///
        /// @param online 是否联网
        /// @return 新配置
        @NotNull
        public Config withOnline(boolean online) {
            return new Config(enabled, online, sources);
        }

        /// 替换源列表。
        ///
        /// @param sources 新源列表
        /// @return 新配置
        @NotNull
        public Config withSources(@NotNull List<QuoteSource> sources) {
            return new Config(enabled, online, sources);
        }
    }

    private DailyQuoteSettings() {
    }

    /// 默认配置：显示、允许联网、Hitokoto 启用、今日诗词停用。
    ///
    /// @return 默认配置
    @NotNull
    public static Config defaults() {
        return new Config(true, true, DEFAULT_SOURCES);
    }

    /// 读取配置；文件缺失或损坏时返回 [DailyQuoteSettings#defaults()] 并补回内置源。
    ///
    /// @return 配置快照
    @NotNull
    public static Config load() {
        try {
            JsonObject section = CommunitySettings.get().section(SECTION);
            if (section.entrySet().isEmpty()) {
                return defaults();
            }
            boolean enabled = JsonUtils.getBoolean(section, "enabled", true);
            boolean online = JsonUtils.getBoolean(section, "online", true);
            return new Config(enabled, online, mergeDefaults(parseSources(section.get("sources"))));
        } catch (Throwable e) {
            LOG.warning("Failed to read daily quote settings", e);
            return defaults();
        }
    }

    /// 保存配置；失败只记录日志。
    ///
    /// @param config 配置
    /// @return 成功返回 `true`
    public static boolean save(@NotNull Config config) {
        try {
            JsonObject section = new JsonObject();
            section.addProperty("enabled", config.enabled());
            section.addProperty("online", config.online());
            JsonArray sources = new JsonArray();
            for (QuoteSource source : config.sources()) {
                JsonObject item = new JsonObject();
                item.addProperty("id", source.id());
                item.addProperty("type", source.type().wireName());
                item.addProperty("name", source.name());
                if (source.url() != null) {
                    item.addProperty("url", source.url());
                }
                item.addProperty("enabled", source.enabled());
                sources.add(item);
            }
            section.add("sources", sources);
            CommunitySettings.get().putSection(SECTION, section);
            return true;
        } catch (Throwable e) {
            LOG.warning("Failed to save daily quote settings", e);
            return false;
        }
    }

    /// 内置源列表（不可变）。
    ///
    /// @return 内置源
    @NotNull
    public static List<QuoteSource> builtinSources() {
        return DEFAULT_SOURCES;
    }

    /// 解析设置中的源；非法条目跳过，自定义 URL 非法也跳过。
    private static @NotNull List<QuoteSource> parseSources(@Nullable JsonElement element) {
        List<QuoteSource> result = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return result;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject object = item.getAsJsonObject();
            String id = getString(object, "id");
            String typeName = getString(object, "type");
            String name = getString(object, "name");
            String url = getString(object, "url");
            QuoteSourceType type = QuoteSourceType.parse(typeName);
            if (id == null || type == null) {
                continue;
            }
            if (name == null || name.isBlank()) {
                name = defaultName(type);
            }
            try {
                boolean enabled = JsonUtils.getBoolean(object, "enabled", type == QuoteSourceType.HITOKOTO);
                result.add(new QuoteSource(id, type, name, url, enabled));
            } catch (IllegalArgumentException e) {
                LOG.warning("Skipping invalid daily quote source: " + id, e);
            }
        }
        return result;
    }

    /// 按 id 合并内置源：缺失的内置源补到末尾，已有项保留用户启用状态。
    private static @NotNull List<QuoteSource> mergeDefaults(@NotNull List<QuoteSource> sources) {
        Map<String, QuoteSource> byId = new LinkedHashMap<>();
        for (QuoteSource source : sources) {
            byId.put(source.id(), source);
        }
        for (QuoteSource builtin : DEFAULT_SOURCES) {
            byId.putIfAbsent(builtin.id(), builtin);
        }
        return List.copyOf(byId.values());
    }

    private static @NotNull String defaultName(@NotNull QuoteSourceType type) {
        return switch (type) {
            case HITOKOTO -> "Hitokoto";
            case JINRISHICI -> "Jinrishici";
            case CUSTOM -> "Custom";
        };
    }

    private static @Nullable String getString(@NotNull JsonObject object, @NotNull String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        String text = value.getAsString();
        return text.isBlank() ? null : text;
    }
}
