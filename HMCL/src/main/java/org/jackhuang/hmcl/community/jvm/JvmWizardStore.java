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
package org.jackhuang.hmcl.community.jvm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/// JVM 参数向导的实例状态（`community-settings.json` 的 `jvmWizard` 段落）。
///
/// 结构：
/// ```json
/// { "jvmWizard": { "<instanceId>": {
///     "mode": "g1", "heapMb": 4096,
///     "generated": "-Xms4096m -Xmx4096m",
///     "generatedAt": "2026-10-21T13:50:00Z"
/// } } }
/// ```
///
/// `generated` 是“用户手改判定”的依据：打开向导时当前 `jvmOptions`
/// 与它不一致，就说明用户手动改过，向导切到自定义且不覆盖。
@NotNullByDefault
public final class JvmWizardStore {

    /// 设置段名。
    public static final String SECTION = "jvmWizard";

    /// 档位字段。
    private static final String KEY_MODE = "mode";

    /// 堆大小字段。
    private static final String KEY_HEAP_MB = "heapMb";

    /// 生成串字段。
    private static final String KEY_GENERATED = "generated";

    /// 生成时间字段。
    private static final String KEY_GENERATED_AT = "generatedAt";

    /// 默认堆大小（MiB）。
    public static final int DEFAULT_HEAP_MB = 4096;

    private JvmWizardStore() {
    }

    /// 一个实例的向导状态。
    ///
    /// @param mode 档位
    /// @param heapMb 堆大小（MiB）
    /// @param generated 向导写入的参数串
    /// @param generatedAt 生成时间（ISO-8601），可能为 `null`（旧数据/手写）
    public record State(
            @NotNull JvmPreset mode,
            int heapMb,
            @NotNull String generated,
            @Nullable String generatedAt
    ) {
    }

    /// 读取全局设置里的实例状态。
    ///
    /// @param instanceId 实例 id
    /// @return 状态；不存在或损坏时 `null`
    @Nullable
    public static State load(@NotNull String instanceId) {
        return load(CommunitySettings.get(), instanceId);
    }

    /// 读取指定设置对象的实例状态（测试用）。
    ///
    /// @param settings 设置对象
    /// @param instanceId 实例 id
    /// @return 状态；不存在或损坏时 `null`
    @Nullable
    public static State load(@NotNull CommunitySettings settings, @NotNull String instanceId) {
        JsonObject section = settings.section(SECTION);
        if (!(section.get(instanceId) instanceof JsonObject entry)) {
            return null;
        }
        try {
            JvmPreset mode = JvmPreset.of(getString(entry, KEY_MODE));
            int heapMb = entry.has(KEY_HEAP_MB) && entry.get(KEY_HEAP_MB).isJsonPrimitive()
                    ? entry.get(KEY_HEAP_MB).getAsInt()
                    : DEFAULT_HEAP_MB;
            String generated = getString(entry, KEY_GENERATED);
            if (generated == null) {
                return null;
            }
            return new State(mode, heapMb, generated, getString(entry, KEY_GENERATED_AT));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /// 保存全局设置里的实例状态。
    ///
    /// @param instanceId 实例 id
    /// @param state 状态
    public static void save(@NotNull String instanceId, @NotNull State state) {
        save(CommunitySettings.get(), instanceId, state);
    }

    /// 保存指定设置对象的实例状态（测试用）。
    ///
    /// @param settings 设置对象
    /// @param instanceId 实例 id
    /// @param state 状态
    public static void save(@NotNull CommunitySettings settings, @NotNull String instanceId, @NotNull State state) {
        JsonObject section = settings.section(SECTION);
        JsonObject entry = new JsonObject();
        entry.addProperty(KEY_MODE, state.mode().storageValue());
        entry.addProperty(KEY_HEAP_MB, state.heapMb());
        entry.addProperty(KEY_GENERATED, state.generated());
        String generatedAt = state.generatedAt() != null ? state.generatedAt() : Instant.now().toString();
        entry.addProperty(KEY_GENERATED_AT, generatedAt);
        section.add(instanceId, entry);
        settings.putSection(SECTION, section);
    }

    /// 删除全局设置里的实例状态。
    ///
    /// @param instanceId 实例 id
    public static void remove(@NotNull String instanceId) {
        remove(CommunitySettings.get(), instanceId);
    }

    /// 删除指定设置对象的实例状态。
    ///
    /// @param settings 设置对象
    /// @param instanceId 实例 id
    public static void remove(@NotNull CommunitySettings settings, @NotNull String instanceId) {
        JsonObject section = settings.section(SECTION);
        if (section.remove(instanceId) == null) {
            return;
        }
        settings.putSection(SECTION, section);
    }

    /// 清理已不存在的实例条目（实例被删除/重命名后调用）。
    ///
    /// @param existingIds 当前存在的实例 id 集合
    public static void prune(@NotNull Collection<String> existingIds) {
        prune(CommunitySettings.get(), existingIds);
    }

    /// 清理指定设置对象里已不存在的实例条目（测试用）。
    ///
    /// @param settings 设置对象
    /// @param existingIds 当前存在的实例 id 集合
    public static void prune(@NotNull CommunitySettings settings, @NotNull Collection<String> existingIds) {
        JsonObject section = settings.section(SECTION);
        boolean changed = false;
        for (String key : section.keySet().toArray(String[]::new)) {
            if (!existingIds.contains(key)) {
                section.remove(key);
                changed = true;
            }
        }
        if (changed) {
            settings.putSection(SECTION, section);
        }
    }

    /// 读取字符串字段。
    ///
    /// @param object JSON 对象
    /// @param key 字段名
    /// @return 字符串；缺失或非字符串时 `null`
    @Nullable
    private static String getString(@NotNull JsonObject object, @NotNull String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : null;
    }

    /// 返回段落内全部状态（调试/统计用）。
    ///
    /// @param settings 设置对象
    /// @return 实例 id → 状态的只读视图
    @NotNull
    public static Map<String, State> loadAll(@NotNull CommunitySettings settings) {
        JsonObject section = settings.section(SECTION);
        Map<String, State> result = new java.util.LinkedHashMap<>();
        for (String key : section.keySet()) {
            State state = load(settings, key);
            if (state != null) {
                result.put(key, state);
            }
        }
        return Map.copyOf(result);
    }
}
