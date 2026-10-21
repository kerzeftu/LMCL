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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 按白名单扫描运行目录，产出可写入 manifest 的文件列表。
///
/// 不跟随符号链接；`config/**` 等可能被原地改写的文件标记为必须复制，单文件超过
/// [ #MAX_COPY_SIZE ] 时跳过并记录。
@NotNullByDefault
public final class SnapshotScanner {

    /// 必须复制的文件大小上限（256 KiB）。
    public static final long MAX_COPY_SIZE = 256L * 1024L;

    /// 固定排除的目录/文件说明。
    private static final List<String> DEFAULT_EXCLUDED = List.of(
            "saves/", "logs/", "crash-reports/", "screenshots/", "backups/",
            "natives-*/", "libraries/", "assets/", "*.log", "符号链接");

    private SnapshotScanner() {
    }

    /// 扫描运行目录。
    ///
    /// @param runDirectory 运行目录
    /// @param settings 快照设置
    /// @return 按路径排序的文件列表
    @NotNull
    public static List<Source> scan(@NotNull Path runDirectory, @NotNull SnapshotSettings settings) {
        Map<String, Source> files = new LinkedHashMap<>();
        addTree(runDirectory, "mods", files, false, 0L);
        addTree(runDirectory, "config", files, true, MAX_COPY_SIZE);
        addFile(runDirectory, "options.txt", files, true);
        addFile(runDirectory, "optionsof.txt", files, true);
        addFile(runDirectory, "servers.dat", files, true);
        addFile(runDirectory, ".hmcl/config/instance-game-settings.json", files, true);
        if (settings.includeResourcePacks()) {
            addTree(runDirectory, "resourcepacks", files, false, 0L);
            addTree(runDirectory, "shaderpacks", files, false, 0L);
        }
        if (settings.includeDatapacks()) {
            addTree(runDirectory, "datapacks", files, false, 0L);
        }
        List<Source> result = new ArrayList<>(files.values());
        result.sort(Comparator.comparing(Source::path));
        return List.copyOf(result);
    }

    /// 返回固定排除项。
    ///
    /// @return 排除项
    @NotNull
    public static List<String> defaultExcluded() {
        return DEFAULT_EXCLUDED;
    }

    /// 添加单个顶层文件。
    private static void addFile(@NotNull Path runDirectory,
                                @NotNull String relative,
                                @NotNull Map<String, Source> files,
                                boolean copyOnly) {
        Path file = runDirectory.resolve(relative);
        addFile(file, relative, files, copyOnly, 0L);
    }

    /// 添加文件。
    private static void addFile(@NotNull Path file,
                                @NotNull String relative,
                                @NotNull Map<String, Source> files,
                                boolean copyOnly,
                                long maxSize) {
        try {
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                return;
            }
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                return;
            }
            if (maxSize > 0 && attributes.size() > maxSize) {
                return;
            }
            String normalized = relative.replace('\\', '/');
            files.putIfAbsent(normalized, new Source(
                    normalized, file, attributes.size(), attributes.lastModifiedTime().toMillis(), copyOnly));
        } catch (IOException ignored) {
        }
    }

    /// 递归添加目录树。
    private static void addTree(@NotNull Path runDirectory,
                                @NotNull String directory,
                                @NotNull Map<String, Source> files,
                                boolean copyOnly,
                                long maxSize) {
        Path root = runDirectory.resolve(directory);
        if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) {
                    if (Files.isSymbolicLink(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && !attrs.isSymbolicLink()
                            && !file.getFileName().toString().toLowerCase().endsWith(".log")) {
                        String relative = runDirectory.relativize(file).toString().replace('\\', '/');
                        addFile(file, relative, files, copyOnly, maxSize);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    /// 一个待采集文件。
    ///
    /// @param path 相对运行目录的路径
    /// @param file 源文件
    /// @param size 大小
    /// @param mtime 修改时间
    /// @param copyOnly 是否必须真实复制
    @NotNullByDefault
    public record Source(
            @NotNull String path,
            @NotNull Path file,
            long size,
            long mtime,
            boolean copyOnly) {
    }
}
