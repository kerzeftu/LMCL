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
package org.jackhuang.hmcl.community.config.section;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.config.SettingsSectionExporter;
import org.jackhuang.hmcl.community.memory.MemoryBaseSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

/// “内存”设置段：自动回收参数 + 启动前优化的全局默认值。
///
/// 运行态字段（`lastReclaimAt`/`totalReclaimedMiB`/`autoDisabled`/`disabledReason`）
/// 不导出；单实例的覆盖值也不导出。
@NotNullByDefault
public final class MemorySectionExporter implements SettingsSectionExporter {

    /// 段 id。
    public static final String ID = "memory";

    /// 启动前优化全局默认值字段名。
    public static final String KEY_LAUNCH_OPTIMIZATION = "memoryOptimizationOnLaunch";

    @Override
    public @NotNull String id() {
        return ID;
    }

    @Override
    public @NotNull String titleKey() {
        return "community.settings.transfer.section.memory";
    }

    @Override
    public @NotNull JsonObject exportNow() {
        MemoryBaseSettings settings = MemoryBaseSettings.load();
        JsonObject object = new JsonObject();
        object.addProperty("enabled", settings.enabled());
        object.addProperty("intervalMinutes", settings.intervalMinutes());
        object.addProperty("minReclaimMiB", settings.minReclaimMiB());
        object.addProperty(KEY_LAUNCH_OPTIMIZATION,
                SettingsManager.getDefaultGameSettingsPresetOrCreate().memoryOptimizationOnLaunchProperty().getValue());
        return object;
    }

    @Override
    public void apply(@NotNull JsonObject incoming) {
        MemoryBaseSettings settings = MemoryBaseSettings.load();
        JsonElement enabled = incoming.get("enabled");
        if (enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean()) {
            settings.setEnabled(enabled.getAsBoolean());
        }
        JsonElement interval = incoming.get("intervalMinutes");
        if (interval != null && interval.isJsonPrimitive() && interval.getAsJsonPrimitive().isNumber()) {
            settings.setIntervalMinutes(interval.getAsInt());
        }
        JsonElement minReclaim = incoming.get("minReclaimMiB");
        if (minReclaim != null && minReclaim.isJsonPrimitive() && minReclaim.getAsJsonPrimitive().isNumber()) {
            settings.setMinReclaimMiB(minReclaim.getAsInt());
        }
        settings.save();

        JsonElement launchOptimization = incoming.get(KEY_LAUNCH_OPTIMIZATION);
        if (launchOptimization != null && launchOptimization.isJsonPrimitive()
                && launchOptimization.getAsJsonPrimitive().isBoolean()) {
            SettingsManager.getDefaultGameSettingsPresetOrCreate()
                    .memoryOptimizationOnLaunchProperty().setValue(launchOptimization.getAsBoolean());
        }
    }
}
