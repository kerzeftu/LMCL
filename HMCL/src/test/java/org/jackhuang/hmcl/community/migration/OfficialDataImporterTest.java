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

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 导入器测试：映射、净化、幂等、跳过规则。
@NotNullByDefault
public final class OfficialDataImporterTest {

    /// 正常导入设置文件，写入目标本地根并生成 receipt。
    @Test
    public void importsSettingsAndWritesReceipt(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("official");
        Path local = temp.resolve("local");
        Path user = temp.resolve("user");
        write(source, "config/launcher-settings.json", "{\"schema\":{\"version\":\"1.0.0\"},\"language\":\"zh_CN\"}");

        OfficialDataImporter.ImportResult result = OfficialDataImporter.importInto(
                OfficialDataImporter.ImportRequest.defaults(source), source, user, local);

        assertEquals(1, result.copied());
        assertTrue(Files.isRegularFile(local.resolve("config/launcher-settings.json")));
        assertTrue(Files.isRegularFile(user.resolve(OfficialDataImporter.RECEIPT_FILE)));
        String text = Files.readString(local.resolve("config/launcher-settings.json"));
        assertTrue(text.contains("zh_CN"));
    }

    /// 高于支持版本的文件被跳过并给出 warning。
    @Test
    public void skipsNewerSchema(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("official");
        Path local = temp.resolve("local");
        Path user = temp.resolve("user");
        write(source, "config/launcher-settings.json", "{\"schema\":{\"version\":\"2.0.0\"}}");

        OfficialDataImporter.ImportResult result = OfficialDataImporter.importInto(
                OfficialDataImporter.ImportRequest.defaults(source), source, user, local);

        assertEquals(0, result.copied());
        assertTrue(result.skipped() >= 1);
        assertTrue(result.warnings().stream().anyMatch(s -> s.contains("schema too new")));
        assertFalse(Files.exists(local.resolve("config/launcher-settings.json")));
    }

    /// 账号元数据导入时剥掉 token 字段；私密数据永不读取。
    @Test
    public void stripsTokensAndNeverReadsPrivateData(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("official");
        Path local = temp.resolve("local");
        Path user = temp.resolve("user");
        write(source, "config/accounts.json",
                "{\"schema\":{\"version\":\"1.0.0\"},\"accounts\":[{\"id\":\"a\",\"accessToken\":\"secret\",\"clientToken\":\"c\"}]}");
        write(source, "private/account-private-data.json", "{\"accessToken\":\"secret\"}");

        OfficialDataImporter.ImportResult result = OfficialDataImporter.importInto(
                new OfficialDataImporter.ImportRequest(source,
                        EnumSet.of(OfficialDataImporter.ImportItem.ACCOUNTS), true, false),
                source, user, local);

        assertEquals(1, result.copied());
        String text = Files.readString(local.resolve("config/accounts.json"));
        assertFalse(text.contains("accessToken"));
        assertFalse(text.contains("secret"));
        assertFalse(Files.exists(local.resolve("private")));
    }

    /// 同一来源第二次导入返回 already imported，不覆盖。
    @Test
    public void isIdempotentByReceipt(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("official");
        Path local = temp.resolve("local");
        Path user = temp.resolve("user");
        write(source, "config/launcher-settings.json", "{\"schema\":{\"version\":\"1.0.0\"},\"language\":\"en\"}");
        OfficialDataImporter.ImportRequest request = OfficialDataImporter.ImportRequest.defaults(source);

        OfficialDataImporter.importInto(request, source, user, local);
        Files.writeString(local.resolve("config/launcher-settings.json"), "{\"language\":\"zh\"}");
        OfficialDataImporter.ImportResult second = OfficialDataImporter.importInto(request, source, user, local);

        assertEquals(0, second.copied());
        assertTrue(second.warnings().contains("already imported"));
        assertEquals("{\"language\":\"zh\"}",
                Files.readString(local.resolve("config/launcher-settings.json")));
    }

    /// 旧格式文件原样落到本地根，并提示需要重启。
    @Test
    public void importsLegacyFileWithRestartWarning(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("official");
        Path local = temp.resolve("local");
        Path user = temp.resolve("user");
        write(source, "settings.json", "{\"versionListSource\":\"bmclapi\"}");

        OfficialDataImporter.ImportResult result = OfficialDataImporter.importInto(
                OfficialDataImporter.ImportRequest.defaults(source), source, user, local);

        assertEquals(1, result.copied());
        assertTrue(Files.isRegularFile(local.resolve("settings.json")));
        assertTrue(result.warnings().stream().anyMatch(s -> s.contains("restart required")));
    }

    /// 缺失 `javaDir` 指向的目录时删除该字段。
    @Test
    public void removesMissingJavaDir(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("official");
        Path local = temp.resolve("local");
        Path user = temp.resolve("user");
        write(source, "config/launcher-settings.json",
                "{\"schema\":{\"version\":\"1.0.0\"},\"javaDir\":\"" + temp.resolve("nope") + "\"}");

        OfficialDataImporter.importInto(OfficialDataImporter.ImportRequest.defaults(source), source, user, local);

        JsonObject object = JsonUtils.fromJsonFile(local.resolve("config/launcher-settings.json"), JsonObject.class);
        assertNotNull(object);
        assertFalse(object.has("javaDir"));
    }

    /// 写测试文件。
    ///
    /// @param root     源根
    /// @param relative 相对路径
    /// @param content  内容
    /// @throws Exception IO 异常
    private static void write(Path root, String relative, @Nullable String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content == null ? "" : content);
    }
}
