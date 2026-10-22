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
package org.jackhuang.hmcl.community.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.community.CommunityMetadata;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// `.lmcl.json` 设置文件的读写与校验。
///
/// 顶层格式固定为 `format = "lmcl.settings"`、`version = 1`；`sections` 只允许
/// 已知的 5 个段（未知段在解析时忽略并记录警告）。文件最大 2 MiB。
@NotNullByDefault
public final class CommunitySettingsBundle {

    /// 顶层格式标识。
    public static final String FORMAT = "lmcl.settings";

    /// 当前文件版本。
    public static final int VERSION = 1;

    /// 文件后缀。
    public static final String FILE_SUFFIX = ".lmcl.json";

    /// 文件大小上限（2 MiB）。
    public static final long MAX_BYTES = 2L * 1024 * 1024;

    private final int version;
    private final String product;
    private final String productVersion;
    private final Instant exportedAt;
    private final Map<String, JsonObject> sections;
    private final List<String> warnings;

    private CommunitySettingsBundle(int version, @NotNull String product, @NotNull String productVersion,
                                    @NotNull Instant exportedAt, @NotNull Map<String, JsonObject> sections,
                                    @NotNull List<String> warnings) {
        this.version = version;
        this.product = product;
        this.productVersion = productVersion;
        this.exportedAt = exportedAt;
        this.sections = sections;
        this.warnings = warnings;
    }

    /// 创建导出包。
    ///
    /// @param sections 段 id → 段内容
    /// @return 设置包
    @NotNull
    public static CommunitySettingsBundle create(@NotNull Map<String, JsonObject> sections) {
        return new CommunitySettingsBundle(VERSION, CommunityMetadata.productName(), CommunityMetadata.version(),
                Instant.now(), Map.copyOf(sections), List.of());
    }

    /// 从磁盘读取并校验 `.lmcl.json`。
    ///
    /// @param path 文件路径
    /// @return 设置包
    /// @throws IOException 读取失败或超过大小上限
    /// @throws JsonParseException 格式不合法
    @NotNull
    public static CommunitySettingsBundle read(@NotNull Path path) throws IOException {
        long size = Files.size(path);
        if (size > MAX_BYTES) {
            throw new IOException("settings file is too large: " + size);
        }
        String text = Files.readString(path, StandardCharsets.UTF_8);
        return parse(text);
    }

    /// 解析并校验 JSON 文本。
    ///
    /// @param text JSON 文本
    /// @return 设置包
    /// @throws JsonParseException 格式不合法
    @NotNull
    public static CommunitySettingsBundle parse(@NotNull String text) {
        JsonElement rootElement = JsonUtils.GSON.fromJson(text, JsonElement.class);
        if (!(rootElement instanceof JsonObject root)) {
            throw new JsonParseException("root must be a JSON object");
        }
        String format = JsonUtils.getString(root, "format");
        if (!FORMAT.equals(format)) {
            throw new JsonParseException("unsupported format: " + format);
        }
        int version = JsonUtils.getInt(root, "version", -1);
        if (version < 1 || version > VERSION) {
            throw new JsonParseException("unsupported version: " + version);
        }
        JsonElement sectionsElement = root.get("sections");
        if (!(sectionsElement instanceof JsonObject sectionsObject)) {
            throw new JsonParseException("missing sections object");
        }

        Map<String, JsonObject> sections = new LinkedHashMap<>();
        List<String> warnings = new java.util.ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : sectionsObject.entrySet()) {
            if (!(entry.getValue() instanceof JsonObject section)) {
                warnings.add("ignored non-object section: " + entry.getKey());
                continue;
            }
            if (SettingsExportService.find(entry.getKey()) == null) {
                warnings.add("ignored unknown section: " + entry.getKey());
                continue;
            }
            sections.put(entry.getKey(), section.deepCopy());
        }
        if (sections.isEmpty()) {
            throw new JsonParseException("no supported sections");
        }

        String product = JsonUtils.getString(root, "product");
        String productVersion = JsonUtils.getString(root, "productVersion");
        Instant exportedAt;
        try {
            String value = JsonUtils.getString(root, "exportedAt");
            exportedAt = value == null ? Instant.EPOCH : Instant.parse(value);
        } catch (RuntimeException e) {
            exportedAt = Instant.EPOCH;
        }
        return new CommunitySettingsBundle(version,
                product == null ? "unknown" : product,
                productVersion == null ? "unknown" : productVersion,
                exportedAt, sections, List.copyOf(warnings));
    }

    /// 序列化为 `.lmcl.json` JSON。
    ///
    /// @return 根对象
    @NotNull
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", VERSION);
        root.addProperty("product", product);
        root.addProperty("productVersion", productVersion);
        root.addProperty("exportedAt", DateTimeFormatter.ISO_INSTANT.format(exportedAt));
        JsonObject sectionsObject = new JsonObject();
        for (Map.Entry<String, JsonObject> entry : sections.entrySet()) {
            sectionsObject.add(entry.getKey(), entry.getValue().deepCopy());
        }
        root.add("sections", sectionsObject);
        return root;
    }

    public int version() {
        return version;
    }

    /// @return 导出方产品名
    @NotNull
    public String product() {
        return product;
    }

    /// @return 导出方产品版本
    @NotNull
    public String productVersion() {
        return productVersion;
    }

    /// @return 导出时间；缺失时为 [Instant#EPOCH]
    @NotNull
    public Instant exportedAt() {
        return exportedAt;
    }

    /// @return 段 id → 内容（不可变）
    @NotNull
    @Unmodifiable
    public Map<String, JsonObject> sections() {
        return sections;
    }

    /// @return 解析时的警告（未知段/非对象段）
    @NotNull
    @Unmodifiable
    public List<String> warnings() {
        return warnings;
    }
}
