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

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.util.Map;

/// Hitokoto 一言源（`https://v1.hitokoto.cn`）。
///
/// 默认只请求诗词分类 `c=i`；响应示例：
/// ```json
/// {"hitokoto":"...","from":"...","from_who":"...","type":"i"}
/// ```
/// 这是公益接口，不承诺 SLA；失败由 [DailyQuoteService] 吞掉并回退。
@NotNullByDefault
public final class HitokotoQuoteProvider implements QuoteProvider {

    /// 默认接口地址。
    public static final String DEFAULT_URL =
            "https://v1.hitokoto.cn/?c=i&encode=json&charset=utf-8";

    private final String url;

    /// 使用默认接口。
    public HitokotoQuoteProvider() {
        this(DEFAULT_URL);
    }

    /// 使用指定接口（测试可注入）。
    ///
    /// @param url 接口地址
    public HitokotoQuoteProvider(@NotNull String url) {
        this.url = url;
    }

    @Override
    public @NotNull DailyQuote fetch() throws Exception {
        String body = QuoteHttp.get(url, Map.of());
        return parse(body);
    }

    /// 解析 Hitokoto JSON。
    ///
    /// @param body 响应正文
    /// @return 短句
    /// @throws IOException 正文缺少有效字段
    static @NotNull DailyQuote parse(@NotNull String body) throws IOException {
        JsonObject root;
        try {
            root = JsonUtils.GSON.fromJson(body, JsonObject.class);
        } catch (RuntimeException e) {
            throw new IOException("invalid Hitokoto response", e);
        }
        if (root == null) {
            throw new IOException("empty Hitokoto response");
        }
        String text = getString(root, "hitokoto");
        if (text == null) {
            throw new IOException("Hitokoto response has no hitokoto field");
        }
        return new DailyQuote(
                text,
                getString(root, "from_who"),
                getString(root, "from"),
                QuoteKind.POETRY,
                QuoteOrigin.ONLINE,
                DailyQuoteSettings.HITOKOTO_ID,
                "Hitokoto");
    }

    private static String getString(@NotNull JsonObject object, @NotNull String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isString()) {
            return null;
        }
        String value = object.get(key).getAsString().trim();
        return value.isEmpty() ? null : value;
    }
}
