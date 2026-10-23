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
import org.jackhuang.hmcl.community.env.CommunityPaths;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 每日一言的联网结果缓存。
///
/// 文件为 `<本地根>/community/daily-quote.json`，只保存一条成功结果及其获取时间。
/// TTL 为 10 分钟；未来时间戳超过 2 分钟视为时钟异常并按过期处理。
/// 读写失败时静默返回 `null`，不影响内置语料回退。
@NotNullByDefault
public final class DailyQuoteStore {

    /// 缓存有效期。
    public static final Duration TTL = Duration.ofMinutes(10);
    /// 允许的未来时间偏移；超过视为时钟异常。
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(2);

    /// 缓存内容。
    ///
    /// @param quote 成功获取的短句
    /// @param fetchedAt 获取时间
    public record Cached(@NotNull DailyQuote quote, @NotNull Instant fetchedAt) {

        /// 判断缓存是否仍在有效期内。
        ///
        /// @param now 当前时间
        /// @return 有效返回 `true`
        public boolean isFresh(@NotNull Instant now) {
            if (fetchedAt.isAfter(now.plus(FUTURE_TOLERANCE))) {
                return false;
            }
            return !fetchedAt.plus(TTL).isBefore(now);
        }
    }

    private final Path path;

    /// 创建缓存。
    ///
    /// @param path 缓存文件
    public DailyQuoteStore(@NotNull Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    /// 返回全局缓存（`<本地根>/community/daily-quote.json`）。
    ///
    /// @return 缓存
    @NotNull
    public static DailyQuoteStore get() {
        return new DailyQuoteStore(CommunityPaths.localHome().resolve("community").resolve("daily-quote.json"));
    }

    /// 缓存文件路径。
    ///
    /// @return 路径
    @NotNull
    public Path path() {
        return path;
    }

    /// 读取缓存；文件缺失或损坏返回 `null`。
    ///
    /// @return 缓存或 `null`
    @Nullable
    public synchronized Cached load() {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject root = JsonUtils.GSON.fromJson(text, JsonObject.class);
            if (root == null || !root.has("quote") || !root.get("quote").isJsonObject()) {
                return null;
            }
            String fetchedAtText = getString(root, "fetchedAt");
            if (fetchedAtText == null) {
                return null;
            }
            Instant fetchedAt = Instant.parse(fetchedAtText);
            JsonObject quote = root.getAsJsonObject("quote");
            String content = getString(quote, "text");
            if (content == null) {
                return null;
            }
            QuoteKind kind = QuoteKind.parse(getString(quote, "kind"));
            return new Cached(new DailyQuote(
                    content,
                    getString(quote, "author"),
                    getString(quote, "source"),
                    kind != null ? kind : QuoteKind.OTHER,
                    QuoteOrigin.ONLINE,
                    getString(quote, "sourceId"),
                    getString(quote, "sourceName")), fetchedAt);
        } catch (Throwable e) {
            LOG.warning("Failed to read daily quote cache: " + path, e);
            return null;
        }
    }

    /// 原子写入缓存；失败只记录日志。
    ///
    /// @param cached 缓存内容
    /// @return 成功返回 `true`
    public synchronized boolean save(@NotNull Cached cached) {
        Objects.requireNonNull(cached, "cached");
        try {
            JsonObject root = new JsonObject();
            root.addProperty("fetchedAt", cached.fetchedAt().toString());
            JsonObject quote = new JsonObject();
            quote.addProperty("text", cached.quote().text());
            if (cached.quote().author() != null) {
                quote.addProperty("author", cached.quote().author());
            }
            if (cached.quote().source() != null) {
                quote.addProperty("source", cached.quote().source());
            }
            quote.addProperty("kind", cached.quote().kind().wireName());
            if (cached.quote().sourceId() != null) {
                quote.addProperty("sourceId", cached.quote().sourceId());
            }
            if (cached.quote().sourceName() != null) {
                quote.addProperty("sourceName", cached.quote().sourceName());
            }
            root.add("quote", quote);

            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, JsonUtils.GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Throwable e) {
            LOG.warning("Failed to write daily quote cache: " + path, e);
            return false;
        }
    }

    /// 删除缓存文件；失败忽略。
    public synchronized void clear() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warning("Failed to delete daily quote cache: " + path, e);
        }
    }

    private static @Nullable String getString(@NotNull JsonObject object, @NotNull String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
                || !object.get(key).getAsJsonPrimitive().isString()) {
            return null;
        }
        String value = object.get(key).getAsString().trim();
        return value.isEmpty() ? null : value;
    }
}
