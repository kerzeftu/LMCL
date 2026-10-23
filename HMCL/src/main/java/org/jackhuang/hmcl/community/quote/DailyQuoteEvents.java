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
package org.jackhuang.hmcl.community.quote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/// 每日一言设置的进程内变更通知。
///
/// 设置页保存后触发；主页监听并重新读取配置。监听器异常会被隔离，
/// 不影响其它监听器。所有回调都在触发线程执行，UI 监听器需自行切回 FX 线程。
@NotNullByDefault
public final class DailyQuoteEvents {

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private DailyQuoteEvents() {
    }

    /// 注册监听器。
    ///
    /// @param listener 监听器
    public static void addListener(@NotNull Runnable listener) {
        LISTENERS.add(listener);
    }

    /// 注销监听器。
    ///
    /// @param listener 监听器
    public static void removeListener(@NotNull Runnable listener) {
        LISTENERS.remove(listener);
    }

    /// 通知所有监听器。
    public static void fireChanged() {
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (Throwable e) {
                // 监听器失败不应影响设置保存或其它监听器。
                org.jackhuang.hmcl.util.logging.Logger.LOG.warning("Daily quote listener failed", e);
            }
        }
    }
}
