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

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.concurrent.CompletionException;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 仪表盘错误卡片与统一日志出口。
///
/// 组件内部任何失败都收敛到这里：替换内容区为可读错误文案，
/// 不弹模态窗口、不让异常冒到 FX 线程，也不影响其它卡片。
@NotNullByDefault
public final class DashboardErrors {

    /// 本类只提供静态方法。
    private DashboardErrors() {
    }

    /// 用错误卡片替换容器内容。
    ///
    /// @param container 内容容器（通常是组件的 `contentBox`）
    /// @param error 原始异常
    public static void show(@NotNull VBox container, @NotNull Throwable error) {
        Throwable cause = unwrap(error);
        LOG.warning("Dashboard widget failed: " + cause, cause);
        show(container, i18n("community.dashboard.error.title") + "\n" + safeMessage(cause));
    }

    /// 用错误卡片替换容器内容。
    ///
    /// @param container 内容容器
    /// @param message 用户可读的错误说明
    public static void show(@NotNull VBox container, @NotNull String message) {
        Label label = new Label(message);
        label.getStyleClass().add("subtitle-label");
        label.setWrapText(true);
        container.getChildren().setAll(label);
    }

    /// 去掉 [CompletionException] 等包装层，拿到根因。
    ///
    /// @param error 原始异常
    /// @return 根因（不会为 `null`）
    @NotNull
    private static Throwable unwrap(@NotNull Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /// 返回异常消息；空消息时退回类名，保证卡片上不是空白。
    ///
    /// @param error 异常
    /// @return 可显示文本
    @NotNull
    private static String safeMessage(@NotNull Throwable error) {
        String message = error.getMessage();
        return message != null && !message.isBlank() ? message : error.getClass().getName();
    }
}
