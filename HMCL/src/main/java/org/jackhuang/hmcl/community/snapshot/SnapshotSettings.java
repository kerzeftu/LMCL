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

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

/// `community-settings.json` 的 `snapshots` 段。
///
/// 读取缺失/损坏字段时回退默认值，不抛异常；写入只改本段已识别的键，保留未知键。
@NotNullByDefault
public final class SnapshotSettings {

    /// 段落名。
    public static final String SECTION = "snapshots";

    /// 默认保留份数。
    public static final int DEFAULT_KEEP = 3;

    /// 默认每个作用域字节上限（2 GiB）。
    public static final long DEFAULT_MAX_BYTES = 2L * 1024 * 1024 * 1024;

    private final CommunitySettings settings;

    /// 使用指定设置存储构造（测试用）。
    ///
    /// @param settings 设置存储
    public SnapshotSettings(@NotNull CommunitySettings settings) {
        this.settings = settings;
    }

    /// 使用全局设置构造。
    ///
    /// @return 设置
    @NotNull
    public static SnapshotSettings load() {
        return new SnapshotSettings(CommunitySettings.get());
    }

    /// 返回设置存储。
    ///
    /// @return 设置存储
    @NotNull
    public CommunitySettings settings() {
        return settings;
    }

    /// 是否启用快照。
    ///
    /// @return 是否启用
    public boolean enabled() {
        return JsonUtils.getBoolean(read(), "enabled", true);
    }

    /// 成功启动后是否自动采集。
    ///
    /// @return 是否自动采集
    public boolean autoCaptureOnSuccess() {
        return JsonUtils.getBoolean(read(), "autoCaptureOnSuccess", true);
    }

    /// 每个作用域保留的非回滚前快照份数（1–10）。
    ///
    /// @return 保留份数
    public int keep() {
        return clamp(JsonUtils.getInt(read(), "keep", DEFAULT_KEEP), 1, 10);
    }

    /// 每个作用域的字节上限。
    ///
    /// @return 字节上限
    public long maxBytesPerScope() {
        return Math.max(16L * 1024 * 1024, readLong("maxBytesPerScope", DEFAULT_MAX_BYTES));
    }

    /// 是否包含资源包。
    ///
    /// @return 是否包含
    public boolean includeResourcePacks() {
        return JsonUtils.getBoolean(read(), "includeResourcePacks", false);
    }

    /// 是否包含数据包。
    ///
    /// @return 是否包含
    public boolean includeDatapacks() {
        return JsonUtils.getBoolean(read(), "includeDatapacks", false);
    }

    /// hash 缓存条目上限。
    ///
    /// @return 条目上限
    public int hashCacheMaxEntries() {
        return clamp(JsonUtils.getInt(read(), "hashCacheMaxEntries", 5000), 100, 50000);
    }

    /// 修改启用开关。
    ///
    /// @param value 是否启用
    public void setEnabled(boolean value) {
        write(object -> object.addProperty("enabled", value));
    }

    /// 修改自动采集开关。
    ///
    /// @param value 是否自动采集
    public void setAutoCaptureOnSuccess(boolean value) {
        write(object -> object.addProperty("autoCaptureOnSuccess", value));
    }

    /// 修改保留份数。
    ///
    /// @param value 保留份数（会夹取到 1–10）
    public void setKeep(int value) {
        write(object -> object.addProperty("keep", clamp(value, 1, 10)));
    }

    /// 修改每作用域字节上限。
    ///
    /// @param value 字节上限
    public void setMaxBytesPerScope(long value) {
        write(object -> object.addProperty("maxBytesPerScope", Math.max(16L * 1024 * 1024, value)));
    }

    /// 修改是否包含资源包。
    ///
    /// @param value 是否包含
    public void setIncludeResourcePacks(boolean value) {
        write(object -> object.addProperty("includeResourcePacks", value));
    }

    /// 修改是否包含数据包。
    ///
    /// @param value 是否包含
    public void setIncludeDatapacks(boolean value) {
        write(object -> object.addProperty("includeDatapacks", value));
    }

    /// 读取段落副本。
    ///
    /// @return 段落
    @NotNull
    private JsonObject read() {
        try {
            return settings.section(SECTION);
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    /// 写入段落，保留未知键。
    ///
    /// @param mutator 修改器
    private void write(@NotNull java.util.function.Consumer<JsonObject> mutator) {
        JsonObject object = read();
        mutator.accept(object);
        settings.putSection(SECTION, object);
    }

    /// 读取 long，兼容字符串/数字；缺失或非法时返回默认值。
    ///
    /// @param key 键
    /// @param defaultValue 默认值
    /// @return 值
    private long readLong(@NotNull String key, long defaultValue) {
        try {
            JsonObject object = read();
            if (object.has(key)) {
                return object.get(key).getAsLong();
            }
        } catch (RuntimeException ignored) {
        }
        return defaultValue;
    }

    /// 夹取整数。
    ///
    /// @param value 值
    /// @param min 最小值
    /// @param max 最大值
    /// @return 夹取后的值
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
