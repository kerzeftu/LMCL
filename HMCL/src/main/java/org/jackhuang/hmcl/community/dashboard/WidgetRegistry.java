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
package org.jackhuang.hmcl.community.dashboard;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 组件注册表：`ServiceLoader` 加载、校验、去重。
///
/// 隔离原则：单个 provider 抛异常只记 WARNING，不影响其它 provider；
/// 非法描述（id 不合法、跨度越界、`order < 0`）跳过并 WARNING；
/// 重复 id 先加载者生效，后者忽略并 WARNING。
@NotNullByDefault
public final class WidgetRegistry {

    /// 已登记的 provider 与描述，按 id 去重、保持加载顺序。
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /// 使用给定条目构造注册表（测试用）。
    ///
    /// @param entries 条目
    private WidgetRegistry(@NotNull Map<String, Entry> entries) {
        this.entries.putAll(entries);
    }

    /// 通过 `ServiceLoader` 加载全部 provider。
    ///
    /// @return 注册表（可能为空）
    @NotNull
    public static WidgetRegistry load() {
        Map<String, Entry> loaded = new LinkedHashMap<>();
        // 用本模块的 classloader：context classloader 在打包/插件环境下不可靠。
        ServiceLoader<WidgetProvider> serviceLoader =
                ServiceLoader.load(WidgetProvider.class, WidgetRegistry.class.getClassLoader());

        Iterator<WidgetProvider> iterator = serviceLoader.iterator();
        while (true) {
            WidgetProvider provider;
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                provider = iterator.next();
            } catch (ServiceConfigurationError e) {
                LOG.warning("Failed to load dashboard widget provider", e);
                break;
            }

            try {
                List<DashboardWidgetDescriptor> descriptors = provider.descriptors();
                if (descriptors == null) {
                    LOG.warning("Dashboard widget provider returned null descriptor list: "
                            + provider.getClass().getName());
                    continue;
                }
                for (DashboardWidgetDescriptor descriptor : descriptors) {
                    if (descriptor == null || !descriptor.isValid()) {
                        LOG.warning("Ignored invalid dashboard widget descriptor: " + descriptor);
                        continue;
                    }
                    if (loaded.containsKey(descriptor.id())) {
                        LOG.warning("Ignored duplicate dashboard widget id: " + descriptor.id());
                        continue;
                    }
                    loaded.put(descriptor.id(), new Entry(descriptor, provider));
                }
            } catch (Throwable e) {
                LOG.warning("Failed to load dashboard widget provider " + provider.getClass().getName(), e);
            }
        }

        return new WidgetRegistry(loaded);
    }

    /// 返回全部描述（加载顺序）。
    ///
    /// @return 不可变列表
    @NotNull
    @Unmodifiable
    public List<DashboardWidgetDescriptor> all() {
        List<DashboardWidgetDescriptor> result = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            result.add(entry.descriptor());
        }
        return List.copyOf(result);
    }

    /// 按 id 查描述。
    ///
    /// @param id 组件 id
    /// @return 描述；未登记时返回 `null`
    @Nullable
    public DashboardWidgetDescriptor byId(@NotNull String id) {
        Entry entry = entries.get(id);
        return entry != null ? entry.descriptor() : null;
    }

    /// 判断 id 是否已登记。
    ///
    /// @param id 组件 id
    /// @return 已登记返回 `true`
    public boolean isRegistered(@NotNull String id) {
        return entries.containsKey(id);
    }

    /// 是否没有任何已登记的组件。
    ///
    /// @return 空注册表返回 `true`
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /// 按 id 创建组件实例；未登记或构造抛异常时返回 `null`（框架隔离）。
    ///
    /// @param id 组件 id
    /// @param context 组件上下文
    /// @return 组件实例或 `null`
    @Nullable
    public DashboardWidget create(@NotNull String id, @NotNull DashboardContext context) {
        Entry entry = entries.get(id);
        if (entry == null) {
            return null;
        }
        try {
            return entry.provider().create(entry.descriptor(), context);
        } catch (Throwable e) {
            LOG.warning("Failed to create dashboard widget " + id, e);
            return null;
        }
    }

    /// 一条登记记录。
    ///
    /// @param descriptor 组件描述
    /// @param provider 组件工厂
    private record Entry(@NotNull DashboardWidgetDescriptor descriptor, @NotNull WidgetProvider provider) {
    }
}
