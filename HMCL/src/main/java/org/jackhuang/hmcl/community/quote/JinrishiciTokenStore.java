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

import org.jackhuang.hmcl.community.env.CommunityPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 今日诗词的按安装 token 存储。
///
/// 文件为 `<本地根>/community/jinrishici-token.txt`，只保存服务端下发的 token。
/// 今日诗词按 token 计费/限流，因此必须持久化，不能每次请求都换新 token。
@NotNullByDefault
public final class JinrishiciTokenStore {

    private final Path path;

    /// 创建 token 存储。
    ///
    /// @param path token 文件
    public JinrishiciTokenStore(@NotNull Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    /// 返回默认存储。
    ///
    /// @return token 存储
    @NotNull
    public static JinrishiciTokenStore get() {
        return new JinrishiciTokenStore(
                CommunityPaths.localHome().resolve("community").resolve("jinrishici-token.txt"));
    }

    /// token 文件路径。
    ///
    /// @return 路径
    @NotNull
    public Path path() {
        return path;
    }

    /// 读取 token。
    ///
    /// @return token 或 `null`
    @Nullable
    public synchronized String load() {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            String token = Files.readString(path, StandardCharsets.UTF_8).trim();
            return token.isEmpty() ? null : token;
        } catch (IOException e) {
            LOG.warning("Failed to read Jinrishici token: " + path, e);
            return null;
        }
    }

    /// 保存 token。
    ///
    /// @param token token
    public synchronized void save(@NotNull String token) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, token, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warning("Failed to save Jinrishici token: " + path, e);
        }
    }

    /// 删除 token（失效时调用）。
    public synchronized void clear() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warning("Failed to delete Jinrishici token: " + path, e);
        }
    }
}
