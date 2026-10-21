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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// `community-settings.json` 中 `preflight` 段的只读视图（带安全缺省值）。
///
/// 缺失或非法的字段一律回退默认值；本类不缓存，每次 [PreflightConfig#load()] 重新读取，
/// 保证设置面板保存后立即生效。
@NotNullByDefault
public final class PreflightConfig {

    /// 默认最小可用磁盘（MiB）。
    public static final int DEFAULT_MIN_DISK_MB = 500;

    /// 默认警告磁盘（MiB）。
    public static final int DEFAULT_WARN_DISK_MB = 2048;

    /// 默认内存占物理内存比例上限。
    public static final double DEFAULT_MAX_MEMORY_RATIO = 0.9;

    /// 检查分组键（与 `checks` 段一致）。
    private static final List<String> CHECK_KEYS = List.of("java", "memory", "disk", "mods", "deps");

    /// 是否启用体检。
    private final boolean enabled;

    /// 分组开关。
    private final Map<String, Boolean> checks;

    /// 最小可用磁盘（MiB）。
    private final int minDiskMb;

    /// 警告磁盘（MiB）。
    private final int warnDiskMb;

    /// 内存比例上限。
    private final double maxMemoryRatio;

    /// 按实例静音的检查 id。
    private final Map<String, Set<String>> muted;

    /// 使用全部默认值构造。
    public PreflightConfig() {
        this(true, Map.of(), DEFAULT_MIN_DISK_MB, DEFAULT_WARN_DISK_MB, DEFAULT_MAX_MEMORY_RATIO, Map.of());
    }

    /// 完整构造（测试与 [PreflightConfig#parse(JsonObject)] 使用）。
    ///
    /// @param enabled 是否启用
    /// @param checks 分组开关
    /// @param minDiskMb 最小可用磁盘
    /// @param warnDiskMb 警告磁盘
    /// @param maxMemoryRatio 内存比例上限
    /// @param muted 静音表
    public PreflightConfig(
            boolean enabled,
            @NotNull Map<String, Boolean> checks,
            int minDiskMb,
            int warnDiskMb,
            double maxMemoryRatio,
            @NotNull Map<String, Set<String>> muted) {
        this.enabled = enabled;
        this.checks = Map.copyOf(checks);
        this.minDiskMb = minDiskMb;
        this.warnDiskMb = warnDiskMb;
        this.maxMemoryRatio = maxMemoryRatio;
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        muted.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        this.muted = Map.copyOf(copy);
    }

    /// 从共享设置读取配置。
    ///
    /// @return 配置
    @NotNull
    public static PreflightConfig load() {
        try {
            return parse(CommunitySettings.get().section("preflight"));
        } catch (Throwable e) {
            LOG.warning("Failed to read preflight settings, using defaults", e);
            return new PreflightConfig();
        }
    }

    /// 解析 `preflight` 段。
    ///
    /// @param section 段落对象
    /// @return 配置
    @NotNull
    public static PreflightConfig parse(@NotNull JsonObject section) {
        boolean enabled = !section.has("enabled") || section.get("enabled").getAsBoolean();

        Map<String, Boolean> checks = new LinkedHashMap<>();
        for (String key : CHECK_KEYS) {
            checks.put(key, true);
        }
        if (section.get("checks") instanceof JsonObject checksObject) {
            for (String key : CHECK_KEYS) {
                if (checksObject.has(key)) {
                    try {
                        checks.put(key, checksObject.get(key).getAsBoolean());
                    } catch (RuntimeException e) {
                        LOG.warning("Invalid preflight check flag: " + key, e);
                    }
                }
            }
        }

        int minDiskMb = DEFAULT_MIN_DISK_MB;
        int warnDiskMb = DEFAULT_WARN_DISK_MB;
        double maxMemoryRatio = DEFAULT_MAX_MEMORY_RATIO;
        if (section.get("thresholds") instanceof JsonObject thresholds) {
            minDiskMb = readInt(thresholds, "minDiskMb", DEFAULT_MIN_DISK_MB, 0, Integer.MAX_VALUE);
            warnDiskMb = readInt(thresholds, "warnDiskMb", DEFAULT_WARN_DISK_MB, 0, Integer.MAX_VALUE);
            if (thresholds.has("maxMemoryRatio")) {
                try {
                    double value = thresholds.get("maxMemoryRatio").getAsDouble();
                    if (value > 0.1 && value <= 1.0) {
                        maxMemoryRatio = value;
                    }
                } catch (RuntimeException e) {
                    LOG.warning("Invalid preflight maxMemoryRatio", e);
                }
            }
        }

        Map<String, Set<String>> muted = new LinkedHashMap<>();
        if (section.get("muted") instanceof JsonObject mutedObject) {
            for (String instanceId : mutedObject.keySet()) {
                Set<String> ids = new LinkedHashSet<>();
                JsonElement element = mutedObject.get(instanceId);
                if (element instanceof JsonArray array) {
                    for (JsonElement id : array) {
                        ids.add(id.getAsString());
                    }
                }
                if (!ids.isEmpty()) {
                    muted.put(instanceId, ids);
                }
            }
        }

        return new PreflightConfig(enabled, checks, minDiskMb, warnDiskMb, maxMemoryRatio, muted);
    }

    /// 读取整数并进行夹取。
    private static int readInt(@NotNull JsonObject object, @NotNull String key, int defaultValue, int min, int max) {
        if (!object.has(key)) {
            return defaultValue;
        }
        try {
            int value = object.get(key).getAsInt();
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException e) {
            LOG.warning("Invalid preflight setting: " + key, e);
            return defaultValue;
        }
    }

    /// 是否启用体检。
    ///
    /// @return 是否启用
    public boolean enabled() {
        return enabled;
    }

    /// 指定分组是否启用。
    ///
    /// @param group 分组键（java/memory/disk/mods/deps）
    /// @return 是否启用
    public boolean isCheckEnabled(@NotNull String group) {
        return checks.getOrDefault(group, true);
    }

    /// 返回最小可用磁盘（MiB）。
    ///
    /// @return MiB
    public int minDiskMb() {
        return minDiskMb;
    }

    /// 返回警告磁盘（MiB）。
    ///
    /// @return MiB
    public int warnDiskMb() {
        return warnDiskMb;
    }

    /// 返回内存比例上限。
    ///
    /// @return 比例
    public double maxMemoryRatio() {
        return maxMemoryRatio;
    }

    /// 该实例的该检查是否已被静音。
    ///
    /// @param versionId 实例 id
    /// @param checkId 检查 id
    /// @return 是否静音
    public boolean isMuted(@NotNull String versionId, @NotNull String checkId) {
        Set<String> ids = muted.get(versionId);
        return ids != null && ids.contains(checkId);
    }

    /// 返回该实例被静音的检查 id。
    ///
    /// @param versionId 实例 id
    /// @return 只读集合
    @NotNull
    public Set<String> mutedChecks(@NotNull String versionId) {
        Set<String> ids = muted.get(versionId);
        return ids == null ? Set.of() : ids;
    }

    /// 把“不再提示本实例”写回共享设置；写入失败只记录日志。
    ///
    /// @param versionId 实例 id
    /// @param checkIds 要静音的检查 id
    public static void mute(@NotNull String versionId, @NotNull List<String> checkIds) {
        try {
            JsonObject section = CommunitySettings.get().section("preflight");
            JsonObject mutedObject;
            if (section.get("muted") instanceof JsonObject existing) {
                mutedObject = existing;
            } else {
                mutedObject = new JsonObject();
                section.add("muted", mutedObject);
            }
            JsonArray array = new JsonArray();
            for (String id : checkIds) {
                array.add(id);
            }
            mutedObject.add(versionId, array);
            CommunitySettings.get().putSection("preflight", section);
        } catch (Throwable e) {
            LOG.warning("Failed to persist muted preflight checks", e);
        }
    }

    /// 返回需要展示的检查 id（过滤静音项）。
    ///
    /// @param report 报告
    /// @return 过滤后的检查结果
    @NotNull
    public List<CheckResult> visibleChecks(@NotNull PreflightReport report) {
        List<CheckResult> visible = new ArrayList<>();
        for (CheckResult check : report.checks()) {
            if (!isMuted(report.versionId(), check.id())) {
                visible.add(check);
            }
        }
        return visible;
    }
}
