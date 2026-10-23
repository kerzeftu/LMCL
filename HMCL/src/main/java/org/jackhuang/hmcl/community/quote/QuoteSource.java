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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/// 一个可启用的联网源。
///
/// 内置源（Hitokoto、今日诗词）的 [QuoteSource#url()] 可以为 `null`，
/// 由对应 provider 使用固定接口；自定义源必须有 http/https URL。
///
/// @param id 稳定 id，只允许小写字母、数字和连字符
/// @param type 源类型
/// @param name 显示名
/// @param url 自定义源的 URL；内置源可为 `null`
/// @param enabled 是否参与随机选择
@NotNullByDefault
public record QuoteSource(
        @NotNull String id,
        @NotNull QuoteSourceType type,
        @NotNull String name,
        @Nullable String url,
        boolean enabled) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");

    /// 校验 id、名称与 URL。
    public QuoteSource {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid quote source id: " + id);
        }
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("quote source name must not be blank");
        }
        url = url == null || url.isBlank() ? null : url.trim();
        if (type == QuoteSourceType.CUSTOM) {
            if (url == null) {
                throw new IllegalArgumentException("custom quote source requires url");
            }
            validateUrl(url);
        } else if (url != null) {
            url = null;
        }
    }

    /// 返回启用状态变化后的副本。
    ///
    /// @param enabled 是否启用
    /// @return 新实例
    @NotNull
    public QuoteSource withEnabled(boolean enabled) {
        return new QuoteSource(id, type, name, url, enabled);
    }

    /// 返回用于展示的地址（去掉协议与末尾斜杠）。
    ///
    /// @return 地址摘要；内置源返回 `null`
    @Nullable
    public String host() {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return url;
            }
            String path = uri.getPath();
            return path == null || path.isEmpty() || "/".equals(path) ? host : host + path;
        } catch (RuntimeException e) {
            return url;
        }
    }

    private static void validateUrl(@NotNull String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid quote source url: " + value, e);
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !("http".equals(scheme.toLowerCase(Locale.ROOT))
                || "https".equals(scheme.toLowerCase(Locale.ROOT)))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("invalid quote source url: " + value);
        }
    }
}
