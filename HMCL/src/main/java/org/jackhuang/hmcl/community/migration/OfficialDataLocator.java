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
package org.jackhuang.hmcl.community.migration;

import org.jackhuang.hmcl.community.env.CommunityPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/// 检测本机上可能存在的官方 HMCL 数据目录（只读，绝不复制）。
///
/// 检测结果写入 `<用户根>/state/community-import-scan.json`，供设置面板 `data` 分组与
/// 首页提示条使用。首次启动不会弹任何模态框。
@NotNullByDefault
public final class OfficialDataLocator {

    /// 扫描结果文件相对用户根的位置。
    public static final String SCAN_FILE = "state/community-import-scan.json";

    /// 官方数据目录的“像数据目录”标记文件（任一存在即算）。
    private static final List<String> MARKERS = List.of(
            "config/launcher-settings.json",
            "settings.json",
            "hmcl.json",
            ".hmcl.json");

    /// 独立日志，避免依赖上游 Logger 的类初始化顺序。
    private static final Logger LOG = Logger.getLogger("lmcl.migration");

    /// 本类只提供静态方法。
    private OfficialDataLocator() {
    }

    /// 扫描候选目录并落盘；检测失败返回 `detected=false`，不抛异常。
    ///
    /// @return 扫描结果
    @NotNull
    public static ScanResult scan() {
        try {
            List<Path> found = candidates().stream()
                    .filter(Files::isDirectory)
                    .filter(OfficialDataLocator::looksLikeDataRoot)
                    .toList();
            ScanResult result = new ScanResult(!found.isEmpty(), found, readIgnored(), Instant.now());
            write(result);
            return result;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to scan official HMCL data", e);
            return new ScanResult(false, List.of(), readIgnored(), Instant.now());
        }
    }

    /// 读取上次扫描结果；文件不存在/损坏时返回 `detected=false`。
    ///
    /// @return 扫描结果
    @NotNull
    public static ScanResult lastScan() {
        Path file = scanFile();
        if (!Files.isRegularFile(file)) {
            return new ScanResult(false, List.of(), false, Instant.EPOCH);
        }
        try {
            return read(file);
        } catch (IOException | RuntimeException e) {
            return new ScanResult(false, List.of(), false, Instant.EPOCH);
        }
    }

    /// 设置“忽略导入提示”；保留已有扫描结果。
    ///
    /// @param ignored 是否忽略
    public static void markIgnored(boolean ignored) {
        ScanResult current = lastScan();
        try {
            write(new ScanResult(current.detected(), current.sources(), ignored, current.scannedAt()));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to write scan result", e);
        }
    }

    /// 对外暴露候选目录（只读校验用）。
    ///
    /// @return 候选目录（可能不存在）
    @NotNull
    public static List<Path> candidates() {
        Set<Path> result = new LinkedHashSet<>();

        Path launcherDir = CommunityPaths.launcherDirectory();
        result.add(launcherDir.resolve(".hmcl"));
        // 官方安装目录通常与 stub 同级；这里只覆盖启动器所在目录与用户根两种常见形态。
        result.add(Path.of(System.getProperty("user.home", ".")).resolve(".hmcl"));

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            addWindowsUserRoots(result, System.getenv("APPDATA"), System.getProperty("user.home", "."));
        } else if (os.contains("mac") || os.contains("darwin")) {
            result.add(Path.of(System.getProperty("user.home", "."))
                    .resolve("Library").resolve("Application Support").resolve("hmcl"));
        } else {
            addUnixUserRoots(result, System.getenv("XDG_DATA_HOME"), System.getProperty("user.home", "."));
        }

        return List.copyOf(result);
    }

    /// 判断目录是否像官方数据根。
    ///
    /// @param dir 目录
    /// @return 是否命中标记文件
    static boolean looksLikeDataRoot(Path dir) {
        for (String marker : MARKERS) {
            if (Files.isRegularFile(dir.resolve(marker))) {
                return true;
            }
        }
        return false;
    }

    /// Windows 用户根候选。
    ///
    /// @param result   结果集合
    /// @param appdata  `%APPDATA%`，可为 `null`
    /// @param userHome 用户主目录
    private static void addWindowsUserRoots(Set<Path> result, String appdata, String userHome) {
        Path base = appdata == null || appdata.isBlank() ? Path.of(userHome) : Path.of(appdata);
        // 上游 Metadata 用 OperatingSystem.getWorkingDirectory("hmcl")，Windows 会加前缀点。
        result.add(base.resolve(".hmcl"));
        result.add(base.resolve(".HMCL"));
        // 兼容旧版/手工放置的非点目录。
        result.add(base.resolve("hmcl"));
        result.add(base.resolve("HMCL"));
    }

    /// Linux/BSD 用户根候选。
    ///
    /// @param result   结果集合
    /// @param xdgData  `$XDG_DATA_HOME`，可为 `null`
    /// @param userHome 用户主目录
    private static void addUnixUserRoots(Set<Path> result, String xdgData, String userHome) {
        Path home = Path.of(userHome);
        if (xdgData != null && !xdgData.isBlank()) {
            result.add(Path.of(xdgData).resolve("hmcl"));
        }
        result.add(home.resolve(".local").resolve("share").resolve("hmcl"));
    }

    /// 扫描结果文件路径。
    ///
    /// @return `<用户根>/state/community-import-scan.json`
    private static Path scanFile() {
        return CommunityPaths.userHome().resolve(SCAN_FILE);
    }

    /// 读取已有扫描结果（保持 `ignored`）。
    ///
    /// @return 是否已忽略
    private static boolean readIgnored() {
        try {
            return lastScan().ignored();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /// 写入扫描结果（原子替换）。
    ///
    /// @param result 结果
    /// @throws IOException 写入失败
    private static void write(ScanResult result) throws IOException {
        Path file = scanFile();
        Files.createDirectories(file.getParent());
        String json = org.jackhuang.hmcl.util.gson.JsonUtils.UGLY_GSON.toJson(java.util.Map.of(
                "detected", result.detected(),
                "ignored", result.ignored(),
                "scannedAt", result.scannedAt().toString(),
                "sources", result.sources().stream().map(Path::toString).toList()));
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, json);
        try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /// 读取扫描结果。
    ///
    /// @param file 结果文件
    /// @return 结果
    /// @throws IOException 读取失败
    @SuppressWarnings("unchecked")
    private static ScanResult read(Path file) throws IOException {
        var object = org.jackhuang.hmcl.util.gson.JsonUtils.fromJsonFile(file, com.google.gson.JsonObject.class);
        if (object == null) {
            return new ScanResult(false, List.of(), false, Instant.EPOCH);
        }
        List<Path> sources = new ArrayList<>();
        if (object.has("sources") && object.get("sources").isJsonArray()) {
            for (var element : object.getAsJsonArray("sources")) {
                if (element.isJsonPrimitive()) {
                    sources.add(Path.of(element.getAsString()));
                }
            }
        }
        Instant scannedAt = Instant.EPOCH;
        if (object.has("scannedAt") && object.get("scannedAt").isJsonPrimitive()) {
            try {
                scannedAt = Instant.parse(object.get("scannedAt").getAsString());
            } catch (RuntimeException ignored) {
            }
        }
        boolean detected = object.has("detected") && object.get("detected").isJsonPrimitive()
                && object.get("detected").getAsBoolean();
        boolean ignored = object.has("ignored") && object.get("ignored").isJsonPrimitive()
                && object.get("ignored").getAsBoolean();
        return new ScanResult(detected, List.copyOf(sources), ignored, scannedAt);
    }

    /// 官方数据检测结果。
    ///
    /// @param detected  是否检测到
    /// @param sources   检测到的数据根
    /// @param ignored   用户是否已忽略提示
    /// @param scannedAt 扫描时间（从未扫描为 `Instant.EPOCH`）
    public record ScanResult(boolean detected,
                             @NotNull List<Path> sources,
                             boolean ignored,
                             @NotNull Instant scannedAt) {
    }
}
