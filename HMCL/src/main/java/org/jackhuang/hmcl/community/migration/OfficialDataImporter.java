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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunityPaths;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/// 把官方 HMCL 的设置类文件导入 LMCL（用户显式触发；绝不自动执行）。
///
/// 规则见 `docs/steps/06-数据目录与用户迁移.md` §4.4：
/// - 只导入设置/状态/游戏目录/认证服务器；实例与游戏数据不复制；
/// - 私密数据（`private/`）永不读取；
/// - 账号元数据默认不导入，且总是剥掉 token 字段；
/// - 旧格式文件原样放到本地根，交给上游 `LegacyConfigMigrator` 在下次启动迁移；
/// - 先写临时文件、全部成功后再原子替换；失败清理，不留半份配置。
@NotNullByDefault
public final class OfficialDataImporter {

    /// 导入类别。
    public enum ImportItem {
        /// `config/launcher-settings.json`。
        SETTINGS,
        /// `config/game-directories.json`。
        GAME_DIRECTORIES,
        /// `config/game-settings.json`。
        GAME_SETTINGS,
        /// `config/authlib-injector-servers.json`。
        AUTH_SERVERS,
        /// `config/user-settings.json`。
        USER_SETTINGS,
        /// `state/user-state.json`。
        USER_STATE,
        /// 账号元数据（仍需 `includeAccounts=true`）。
        ACCOUNTS,
        /// 旧格式 `settings.json` / `hmcl.json` / `.hmcl.json`。
        LEGACY
    }

    /// 一条映射：源相对路径 → 目标根上的相对路径。
    ///
    /// @param source    源相对路径
    /// @param target    目标相对路径
    /// @param userRoot  目标是否在用户根（否则本地根）
    /// @param item      所属类别
    private record Mapping(String source, String target, boolean userRoot, ImportItem item) {
    }

    /// 全部映射。
    private static final List<Mapping> MAPPINGS = List.of(
            new Mapping("config/launcher-settings.json", "config/launcher-settings.json", false, ImportItem.SETTINGS),
            new Mapping("config/game-directories.json", "config/game-directories.json", false, ImportItem.GAME_DIRECTORIES),
            new Mapping("config/game-settings.json", "config/game-settings.json", false, ImportItem.GAME_SETTINGS),
            new Mapping("config/authlib-injector-servers.json", "config/authlib-injector-servers.json", false, ImportItem.AUTH_SERVERS),
            new Mapping("config/accounts.json", "config/accounts.json", false, ImportItem.ACCOUNTS),
            new Mapping("config/user-settings.json", "config/user-settings.json", true, ImportItem.USER_SETTINGS),
            new Mapping("state/user-state.json", "state/user-state.json", true, ImportItem.USER_STATE),
            new Mapping("config/user-accounts.json", "config/user-accounts.json", true, ImportItem.ACCOUNTS),
            new Mapping("settings.json", "settings.json", false, ImportItem.LEGACY),
            new Mapping("hmcl.json", "hmcl.json", false, ImportItem.LEGACY),
            new Mapping(".hmcl.json", ".hmcl.json", false, ImportItem.LEGACY));

    /// receipt 相对用户根的位置。
    public static final String RECEIPT_FILE = "state/community-import-receipt.json";

    /// 支持的 schema 主版本（上游当前全部为 1.x）。
    private static final int SUPPORTED_SCHEMA_MAJOR = 1;

    /// 独立日志。
    private static final Logger LOG = Logger.getLogger("lmcl.migration");

    /// 本类只提供静态方法。
    private OfficialDataImporter() {
    }

    /// 导入请求。
    ///
    /// @param sourceRoot      官方数据根（必须是 [`OfficialDataLocator#candidates()`] 之一）
    /// @param items           要导入的类别
    /// @param includeAccounts 是否导入账号元数据（私密数据永不导入）
    /// @param force           忽略 receipt 强制重导
    public record ImportRequest(@NotNull Path sourceRoot,
                                @NotNull Set<ImportItem> items,
                                boolean includeAccounts,
                                boolean force) {
        /// 便捷构造：除账号外的全部类别。
        ///
        /// @param sourceRoot 官方数据根
        /// @return 请求
        @NotNull
        public static ImportRequest defaults(@NotNull Path sourceRoot) {
            return new ImportRequest(sourceRoot, EnumSet.complementOf(EnumSet.of(ImportItem.ACCOUNTS)), false, false);
        }
    }

    /// 导入结果。
    ///
    /// @param copied      成功写入的文件数
    /// @param skipped     跳过的文件数
    /// @param warnings    警告（含“已导入过”“需要重启”）
    /// @param receiptPath receipt 路径
    public record ImportResult(int copied, int skipped, @NotNull List<String> warnings,
                               @NotNull Path receiptPath) {
    }

    /// 执行导入。调用方应在 IO 线程运行（会做磁盘 IO）。
    ///
    /// @param request 请求
    /// @return 结果
    /// @throws IOException 写入失败（已尽力回滚）
    @NotNull
    public static ImportResult importFrom(@NotNull ImportRequest request) throws IOException {
        Path source = validateSource(request.sourceRoot());
        return importInto(request, source, CommunityPaths.userHome(), CommunityPaths.localHome());
    }

    /// 可测试的导入实现（不校验来源，不读系统属性）。
    ///
    /// @param request   请求
    /// @param source    官方数据根
    /// @param userRoot  目标用户根
    /// @param localRoot 目标本地根
    /// @return 结果
    /// @throws IOException 写入失败
    @NotNull
    static ImportResult importInto(@NotNull ImportRequest request,
                                   @NotNull Path source,
                                   @NotNull Path userRoot,
                                   @NotNull Path localRoot) throws IOException {
        Path receiptPath = userRoot.resolve(RECEIPT_FILE);

        if (!request.force() && isAlreadyImported(receiptPath, source)) {
            return new ImportResult(0, 0, List.of("already imported"), receiptPath);
        }

        List<String> warnings = new ArrayList<>();
        List<Mapping> selected = MAPPINGS.stream()
                .filter(mapping -> request.items().contains(mapping.item()))
                .filter(mapping -> mapping.item() != ImportItem.ACCOUNTS || request.includeAccounts())
                .toList();

        List<Path> temps = new ArrayList<>();
        List<Path> targets = new ArrayList<>();
        List<Path> backups = new ArrayList<>();
        List<String> importedNames = new ArrayList<>();
        int skipped = 0;

        try {
            for (Mapping mapping : selected) {
                Path from = source.resolve(mapping.source());
                if (!Files.isRegularFile(from)) {
                    skipped++;
                    continue;
                }
                byte[] raw = Files.readAllBytes(from);
                JsonObject object = parseObject(raw);
                if (object == null) {
                    skipped++;
                    warnings.add("not a json object: " + mapping.source());
                    continue;
                }
                if (!isSupportedSchema(object)) {
                    skipped++;
                    warnings.add("schema too new: " + mapping.source());
                    continue;
                }
                sanitize(object, mapping);
                Path to = (mapping.userRoot() ? userRoot : localRoot).resolve(mapping.target());
                Files.createDirectories(to.getParent());
                Path temp = to.resolveSibling(to.getFileName() + ".tmp-" + ProcessHandle.current().pid());
                Files.writeString(temp, JsonUtils.UGLY_GSON.toJson(object), StandardCharsets.UTF_8);
                temps.add(temp);
                targets.add(to);
                importedNames.add(mapping.target());
                if (mapping.item() == ImportItem.LEGACY) {
                    warnings.add("legacy format imported; restart required: " + mapping.target());
                }
            }

            // 提交阶段：先备份已有目标，再逐个原子替换；任一失败则回滚。
            try {
                for (Path to : targets) {
                    if (Files.exists(to)) {
                        Path backup = to.resolveSibling(to.getFileName() + ".bak-" + ProcessHandle.current().pid());
                        Files.copy(to, backup, StandardCopyOption.REPLACE_EXISTING);
                        backups.add(backup);
                    }
                }
                for (int i = 0; i < targets.size(); i++) {
                    move(temps.get(i), targets.get(i));
                }
                for (Path backup : backups) {
                    Files.deleteIfExists(backup);
                }
            } catch (IOException e) {
                for (Path backup : backups) {
                    String name = backup.getFileName().toString();
                    Path original = backup.resolveSibling(name.substring(0, name.indexOf(".bak-")));
                    try {
                        Files.move(backup, original, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {
                    }
                }
                throw e;
            }

            writeReceipt(receiptPath, source, importedNames);
            return new ImportResult(importedNames.size(), skipped, List.copyOf(warnings), receiptPath);
        } finally {
            for (Path temp : temps) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /// 校验来源必须是检测器给出的候选目录之一。
    ///
    /// @param root 请求的来源
    /// @return 规范化后的来源
    /// @throws IOException 不是已知来源
    private static Path validateSource(Path root) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        boolean known = OfficialDataLocator.candidates().stream()
                .map(path -> path.toAbsolutePath().normalize())
                .anyMatch(normalized::equals);
        if (!known) {
            throw new IOException("unknown HMCL data root: " + normalized);
        }
        return normalized;
    }

    /// 读为 JsonObject；不是对象返回 `null`。
    ///
    /// @param raw 原始字节
    /// @return 对象或 `null`
    private static @Nullable JsonObject parseObject(byte[] raw) {
        try {
            JsonElement element = JsonUtils.GSON.fromJson(new String(raw, StandardCharsets.UTF_8), JsonElement.class);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /// schema 主版本不超过支持范围时返回 `true`。
    ///
    /// @param object 设置文件
    /// @return 是否可接受
    private static boolean isSupportedSchema(JsonObject object) {
        if (!object.has("schema")) {
            return true;
        }
        String version = schemaVersion(object.get("schema"));
        if (version == null) {
            return true;
        }
        try {
            int major = Integer.parseInt(version.split("\\.")[0]);
            return major <= SUPPORTED_SCHEMA_MAJOR;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /// 从 schema 字段提取版本字符串（`"1.0.0"` 或 `{"version":"1.0.0"}`）。
    ///
    /// @param element schema 字段
    /// @return 版本字符串或 `null`
    private static @Nullable String schemaVersion(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonObject() && element.getAsJsonObject().has("version")) {
            JsonElement version = element.getAsJsonObject().get("version");
            return version.isJsonPrimitive() ? version.getAsString() : null;
        }
        return null;
    }

    /// 按类别净化：账号去 token、去掉失效 `javaDir`。
    ///
    /// @param object  文件内容
    /// @param mapping 映射
    private static void sanitize(JsonObject object, Mapping mapping) {
        if (mapping.item() == ImportItem.ACCOUNTS) {
            stripTokens(object);
        }
        if (object.has("javaDir") && object.get("javaDir").isJsonPrimitive()) {
            String value = object.get("javaDir").getAsString();
            if (!value.isBlank() && !Files.exists(Path.of(value))) {
                object.remove("javaDir");
            }
        }
    }

    /// 递归删除账号 token 字段。
    ///
    /// @param element JSON 节点
    private static void stripTokens(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : List.copyOf(object.keySet())) {
                if (key.toLowerCase(java.util.Locale.ROOT).contains("token")) {
                    object.remove(key);
                } else {
                    stripTokens(object.get(key));
                }
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement child : array) {
                stripTokens(child);
            }
        }
    }

    /// 原子移动，失败时回退到普通替换。
    ///
    /// @param from 源
    /// @param to   目标
    /// @throws IOException 移动失败
    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /// 是否已有同一来源的成功 receipt。
    ///
    /// @param receiptPath receipt 路径
    /// @param source      来源
    /// @return 是否已导入
    private static boolean isAlreadyImported(Path receiptPath, Path source) {
        if (!Files.isRegularFile(receiptPath)) {
            return false;
        }
        try {
            JsonObject object = JsonUtils.fromJsonFile(receiptPath, JsonObject.class);
            return object != null && object.has("source")
                    && Path.of(object.get("source").getAsString()).toAbsolutePath().normalize().equals(source);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /// 写 receipt。
    ///
    /// @param receiptPath receipt 路径
    /// @param source      来源
    /// @param files       已导入文件
    /// @throws IOException 写入失败
    private static void writeReceipt(Path receiptPath, Path source, List<String> files) throws IOException {
        Files.createDirectories(receiptPath.getParent());
        JsonObject object = new JsonObject();
        object.addProperty("source", source.toString());
        object.addProperty("importedAt", Instant.now().toString());
        object.addProperty("version", 1);
        JsonArray array = new JsonArray();
        files.forEach(array::add);
        object.add("files", array);
        Path tmp = receiptPath.resolveSibling(receiptPath.getFileName() + ".tmp");
        Files.writeString(tmp, JsonUtils.UGLY_GSON.toJson(object), StandardCharsets.UTF_8);
        move(tmp, receiptPath);
        LOG.log(Level.INFO, "Imported HMCL data from {0} ({1} files)", new Object[]{source, files.size()});
    }

    /// 便捷方法：除账号外全部导入。
    ///
    /// @param sourceRoot 官方数据根
    /// @return 结果
    /// @throws IOException 写入失败
    @NotNull
    public static ImportResult importDefaults(@NotNull Path sourceRoot) throws IOException {
        return importFrom(ImportRequest.defaults(sourceRoot));
    }
}
