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
package org.jackhuang.hmcl.community.snapshot;

import org.jackhuang.hmcl.util.DigestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/// 快照作用域：一个规范化后的运行目录。
///
/// @param scopeKey 路径 SHA-256 的前 16 位十六进制
/// @param label `version-root` 或 `shared`
/// @param runDirectory 规范化后的运行目录
/// @param instanceIds 使用该运行目录的实例 id
/// @param shared 是否与非同版本的其他实例共享
@NotNullByDefault
public record SnapshotScope(
        @NotNull String scopeKey,
        @NotNull String label,
        @NotNull Path runDirectory,
        @NotNull @Unmodifiable List<String> instanceIds,
        boolean shared) {

    /// 规范构造器。
    public SnapshotScope {
        if (!scopeKey.matches("[0-9a-f]{16}")) {
            throw new IllegalArgumentException("Invalid scope key: " + scopeKey);
        }
        instanceIds = List.copyOf(instanceIds);
        runDirectory = runDirectory.toAbsolutePath().normalize();
    }

    /// 按运行目录、仓库根、版本目录与实例列表创建作用域。
    ///
    /// @param runDirectory 运行目录
    /// @param baseDirectory 仓库根
    /// @param versionRoot 版本目录
    /// @param instanceIds 使用该运行目录的实例
    /// @return 作用域
    @NotNull
    public static SnapshotScope of(
            @NotNull Path runDirectory,
            @NotNull Path baseDirectory,
            @NotNull Path versionRoot,
            @NotNull List<String> instanceIds) {
        Path normalized = runDirectory.toAbsolutePath().normalize();
        boolean versionRootScope = normalized.equals(versionRoot.toAbsolutePath().normalize());
        boolean shared = !versionRootScope || instanceIds.size() > 1;
        String key = DigestUtils.digestToString(
                "SHA-256",
                normalized.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 16);
        return new SnapshotScope(
                key,
                versionRootScope && !shared ? "version-root" : "shared",
                normalized,
                instanceIds,
                shared);
    }

    /// 返回该作用域是否与多个实例共享。
    ///
    /// @return 是否共享
    public boolean isShared() {
        return shared;
    }

    /// 返回共享实例数（至少 1）。
    ///
    /// @return 实例数
    public int instanceCount() {
        return Math.max(1, instanceIds.size());
    }
}
