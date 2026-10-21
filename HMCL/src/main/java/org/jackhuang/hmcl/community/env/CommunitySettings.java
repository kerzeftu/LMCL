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
package org.jackhuang.hmcl.community.env;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.Metadata;
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

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 社区版共享设置（`<本地根>/config/community-settings.json`）的唯一读写入口。
///
/// 本类先交付 13/05 需要的最小字段（`updateSkippedVersion`），
/// 后续步骤（08/10/12/15/16/17）在自己的段落里追加键，**不得**另起一套读写逻辑。
/// 写入是懒加载 + 原子写；读取失败时退回内存默认值，不抛异常。
///
/// 注意：本类不是线程安全的 UI 状态，字段读写约定在 FX 线程或初始化线程，
/// 落盘方法 [CommunitySettings#save()] 自身同步。
@NotNullByDefault
public final class CommunitySettings {

    /// 相对本地根的配置文件路径。
    private static final String FILE_PATH = "config/community-settings.json";

    /// 全局实例，首次访问时创建。
    private static volatile @Nullable CommunitySettings instance;

    /// 配置文件路径。
    private final Path path;

    /// 内存中的根对象。
    private JsonObject root = new JsonObject();

    /// 是否已从磁盘加载。
    private boolean loaded;

    /// 使用指定路径构造（测试用）。
    ///
    /// @param path 配置文件路径
    public CommunitySettings(@NotNull Path path) {
        this.path = path;
    }

    /// 返回基于 `<本地根>/config/community-settings.json` 的全局实例。
    ///
    /// @return 全局实例
    @NotNull
    public static CommunitySettings get() {
        CommunitySettings local = instance;
        if (local == null) {
            synchronized (CommunitySettings.class) {
                local = instance;
                if (local == null) {
                    local = new CommunitySettings(Metadata.HMCL_LOCAL_HOME.resolve(FILE_PATH));
                    instance = local;
                }
            }
        }
        return local;
    }

    /// 返回配置文件路径。
    ///
    /// @return 路径
    @NotNull
    public Path path() {
        return path;
    }

    /// 返回“用户选择稍后提示”的版本号；从未提示过时返回 `null`。
    ///
    /// @return 版本号或 `null`
    @Nullable
    public synchronized String updateSkippedVersion() {
        ensureLoaded();
        return JsonUtils.getString(root, "updateSkippedVersion");
    }

    /// 记录“同一版本不再提示”；写入后立即落盘。
    ///
    /// @param version 版本号；`null` 表示清除
    public synchronized void setUpdateSkippedVersion(@Nullable String version) {
        ensureLoaded();
        if (version == null) {
            root.remove("updateSkippedVersion");
        } else {
            root.addProperty("updateSkippedVersion", version);
        }
        save();
    }

    /// 确保已从磁盘加载（只读失败时保持空对象）。
    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject object = JsonUtils.GSON.fromJson(json, JsonObject.class);
            if (object != null) {
                root = object;
            }
        } catch (Throwable e) {
            LOG.warning("Failed to load community settings " + path, e);
            root = new JsonObject();
        }
    }

    /// 原子写回磁盘；失败只记日志（设置丢失不应阻断启动）。
    public synchronized void save() {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, JsonUtils.GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.warning("Failed to save community settings " + path, e);
        }
    }
}
