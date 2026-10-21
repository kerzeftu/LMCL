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
package org.jackhuang.hmcl.community.preflight;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 轻量的模组元数据扫描器，只服务于启动前体检（不替代 [org.jackhuang.hmcl.addon.mod.ModManager]）。
///
/// 支持：
/// - Fabric：`fabric.mod.json` 的 `id` 与 `depends`（只取键名，不做版本范围判断）；
/// - Forge/NeoForge：`META-INF/mods.toml` / `META-INF/neoforge.mods.toml` 的 `[[mods]]` 与
///   `[[dependencies.<modid>]]`（只取 `mandatory = true`）。
///
/// 解析失败的 jar 只计入 `unparseable`，绝不猜测 modid。
@NotNullByDefault
public final class ModScanner {

    /// 单个实例最多扫描的 jar 数量；超过时退化为“只按文件名”模式。
    public static final int MAX_SCAN_FILES = 500;

    /// 解析失败率超过该比例时，依赖检查整体跳过。
    public static final double MAX_UNPARSEABLE_RATIO = 0.1;

    /// 缓存 TTL（毫秒）。
    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;

    /// 缓存条目上限。
    private static final int CACHE_LIMIT = 32;

    /// 内置 modid（不是真实需要安装的模组）。
    private static final Set<String> BUILTIN_IDS = Set.of(
            "minecraft", "java", "fabricloader", "fabric", "forge", "neoforge", "neoforged");

    /// 可执行模组扩展名。
    private static final Set<String> MOD_EXTENSIONS = Set.of("jar", "zip", "litemod");

    /// 扫描结果缓存。
    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private ModScanner() {
    }

    /// 一个可识别的模组。
    ///
    /// @param id 模组 id
    /// @param fileName 文件名
    /// @param dependencies 它声明的必需前置 id（已过滤内置 id）
    public record ModInfo(@NotNull String id, @NotNull String fileName, @NotNull List<String> dependencies) {
    }

    /// 一次扫描的结果。
    ///
    /// @param mods 识别成功的模组
    /// @param total 参与扫描的文件总数
    /// @param unparseable 无法识别元数据的文件数
    /// @param tooMany 是否因超过 [ModScanner#MAX_SCAN_FILES] 而截断
    public record ScanResult(
            @NotNull List<ModInfo> mods,
            int total,
            int unparseable,
            boolean tooMany) {

        /// 解析失败率（0–1）。
        ///
        /// @return 比例
        public double unparseableRatio() {
            return total == 0 ? 0 : (double) unparseable / total;
        }
    }

    /// 扫描实例的 `mods/` 目录（带 10 分钟内存缓存）。
    ///
    /// @param modsDirectory `mods/` 目录
    /// @return 结果
    @NotNull
    public static ScanResult scanCached(@NotNull Path modsDirectory) {
        String key = cacheKey(modsDirectory);
        long now = System.currentTimeMillis();
        CacheEntry entry = CACHE.get(key);
        if (entry != null && now - entry.createdAt < CACHE_TTL_MILLIS) {
            return entry.result;
        }
        ScanResult result = scan(modsDirectory);
        putCache(key, new CacheEntry(result, now));
        return result;
    }

    /// 扫描目录（不读缓存）。
    ///
    /// @param modsDirectory `mods/` 目录
    /// @return 结果
    @NotNull
    public static ScanResult scan(@NotNull Path modsDirectory) {
        List<Path> files = new ArrayList<>();
        boolean tooMany = false;
        try (var stream = Files.list(modsDirectory)) {
            List<Path> all = stream
                    .filter(Files::isRegularFile)
                    .filter(ModScanner::isEnabledModFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
            tooMany = all.size() > MAX_SCAN_FILES;
            files.addAll(all.subList(0, Math.min(all.size(), MAX_SCAN_FILES)));
        } catch (IOException e) {
            LOG.warning("Failed to list mods directory " + modsDirectory, e);
            return new ScanResult(List.of(), 0, 0, false);
        }

        List<ModInfo> mods = new ArrayList<>();
        int unparseable = 0;
        for (Path file : files) {
            try {
                ModInfo info = readMod(file);
                if (info == null) {
                    unparseable++;
                } else {
                    mods.add(info);
                }
            } catch (Throwable e) {
                unparseable++;
                LOG.warning("Failed to read mod metadata from " + file, e);
            }
        }
        return new ScanResult(List.copyOf(mods), files.size(), unparseable, tooMany);
    }

    /// 清空缓存（测试用）。
    public static void clearCache() {
        CACHE.clear();
    }

    /// 是否为启用状态的模组文件。
    private static boolean isEnabledModFile(@NotNull Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".disabled") || name.endsWith(".old") || name.startsWith(".")) {
            return false;
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 && MOD_EXTENSIONS.contains(name.substring(dot + 1));
    }

    /// 读取单个模组的元数据；无法识别时返回 `null`。
    private static @Nullable ModInfo readMod(@NotNull Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry fabricEntry = zip.getEntry("fabric.mod.json");
            if (fabricEntry != null) {
                JsonObject object = JsonUtils.GSON.fromJson(readText(zip, fabricEntry), JsonObject.class);
                if (object != null && object.has("id")) {
                    String id = object.get("id").getAsString();
                    Set<String> dependencies = new LinkedHashSet<>();
                    if (object.get("depends") instanceof JsonObject depends) {
                        dependencies.addAll(depends.keySet());
                    }
                    return new ModInfo(id, file.getFileName().toString(), filterDependencies(dependencies));
                }
                return null;
            }

            ZipEntry tomlEntry = zip.getEntry("META-INF/mods.toml");
            if (tomlEntry == null) {
                tomlEntry = zip.getEntry("META-INF/neoforge.mods.toml");
            }
            if (tomlEntry != null) {
                TomlParseResult toml = Toml.parse(readText(zip, tomlEntry));
                if (toml.hasErrors()) {
                    return null;
                }
                TomlArray modsArray = toml.getArray("mods");
                if (modsArray == null || modsArray.size() == 0) {
                    return null;
                }
                if (!(modsArray.get(0) instanceof TomlTable firstMod)) {
                    return null;
                }
                String modId = firstMod.getString("modId");
                if (modId == null) {
                    return null;
                }
                return new ModInfo(modId, file.getFileName().toString(), readForgeDependencies(toml, modId));
            }
        }
        return null;
    }

    /// 读取 Forge 依赖声明（仅 `mandatory = true`）。
    private static @NotNull List<String> readForgeDependencies(@NotNull TomlParseResult toml, @NotNull String modId) {
        Set<String> dependencies = new LinkedHashSet<>();
        TomlArray array = toml.getArray("dependencies." + modId);
        if (array == null) {
            array = toml.getArray("dependencies");
        }
        if (array != null) {
            for (int i = 0; i < array.size(); i++) {
                if (array.get(i) instanceof TomlTable table) {
                    Boolean mandatory = table.getBoolean("mandatory");
                    String dependencyId = table.getString("modId");
                    if (dependencyId != null && Boolean.TRUE.equals(mandatory)) {
                        dependencies.add(dependencyId);
                    }
                }
            }
        }
        return filterDependencies(dependencies);
    }

    /// 过滤内置 id 并返回不可变列表。
    private static @NotNull List<String> filterDependencies(@NotNull Set<String> dependencies) {
        List<String> result = new ArrayList<>();
        for (String dependency : dependencies) {
            String normalized = dependency.toLowerCase(Locale.ROOT);
            if (!BUILTIN_IDS.contains(normalized)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    /// 读取 zip 条目文本。
    private static @NotNull String readText(@NotNull ZipFile zip, @NotNull ZipEntry entry) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /// 计算缓存键：目录绝对路径 + 文件列表哈希 + 目录 mtime。
    private static @NotNull String cacheKey(@NotNull Path modsDirectory) {
        Path absolute = modsDirectory.toAbsolutePath().normalize();
        StringBuilder builder = new StringBuilder(absolute.toString());
        try (var stream = Files.list(absolute)) {
            List<String> names = new ArrayList<>();
            stream.forEach(path -> names.add(path.getFileName().toString()));
            names.sort(String::compareTo);
            for (String name : names) {
                builder.append('|').append(name);
            }
        } catch (IOException e) {
            builder.append("|unreadable");
        }
        try {
            builder.append('|').append(Files.getLastModifiedTime(absolute).toMillis());
        } catch (IOException ignored) {
            // 忽略：键仍包含文件列表。
        }
        return builder.toString();
    }

    /// 写入缓存，超过上限时清理最旧条目。
    private static void putCache(@NotNull String key, @NotNull CacheEntry entry) {
        if (CACHE.size() >= CACHE_LIMIT) {
            String oldestKey = null;
            long oldest = Long.MAX_VALUE;
            for (Map.Entry<String, CacheEntry> it : CACHE.entrySet()) {
                if (it.getValue().createdAt < oldest) {
                    oldest = it.getValue().createdAt;
                    oldestKey = it.getKey();
                }
            }
            if (oldestKey != null) {
                CACHE.remove(oldestKey);
            }
        }
        CACHE.put(key, entry);
    }

    /// 缓存条目。
    private record CacheEntry(@NotNull ScanResult result, long createdAt) {
    }

    /// 计算缺失的必需依赖。
    ///
    /// @param result 扫描结果
    /// @return modid → 依赖它的模组文件名列表
    @NotNull
    public static Map<String, List<String>> missingDependencies(@NotNull ScanResult result) {
        Set<String> present = new HashSet<>();
        for (ModInfo mod : result.mods()) {
            present.add(mod.id().toLowerCase(Locale.ROOT));
        }
        Map<String, List<String>> missing = new LinkedHashMap<>();
        for (ModInfo mod : result.mods()) {
            for (String dependency : mod.dependencies()) {
                if (!present.contains(dependency)) {
                    missing.computeIfAbsent(dependency, key -> new ArrayList<>()).add(mod.fileName());
                }
            }
        }
        return missing;
    }
}
