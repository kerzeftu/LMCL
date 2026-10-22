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

import javafx.event.EventHandler;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/// 把 `.lmcl.json` 拖入启动器窗口的处理器。
///
/// 只识别**单个**文件名以 `.lmcl.json` 结尾的本地文件；其它拖入继续交给
/// authlib-injector 等既有处理器。
@NotNullByDefault
public final class CommunityConfigDnD {

    private CommunityConfigDnD() {
    }

    /// 从拖拽板中取第一个设置文件。
    ///
    /// @param dragboard 拖拽板
    /// @return 文件路径；没有匹配文件时为空
    @NotNull
    public static Optional<Path> parseFileFromDragboard(@NotNull Dragboard dragboard) {
        for (File file : dragboard.getFiles()) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(CommunitySettingsBundle.FILE_SUFFIX)) {
                return Optional.of(file.toPath());
            }
        }
        return Optional.empty();
    }

    /// `DRAG_OVER` 处理器：有设置文件时接受复制。
    ///
    /// @return 处理器
    @NotNull
    public static EventHandler<DragEvent> dragOverHandler() {
        return event -> parseFileFromDragboard(event.getDragboard()).ifPresent(path -> {
            event.acceptTransferModes(TransferMode.COPY);
            event.consume();
        });
    }

    /// `DRAG_DROPPED` 处理器：回调设置文件路径。
    ///
    /// @param onFile 文件回调，在 FX 线程执行
    /// @return 处理器
    @NotNull
    public static EventHandler<DragEvent> dragDroppedHandler(@NotNull Consumer<Path> onFile) {
        return event -> parseFileFromDragboard(event.getDragboard()).ifPresent(path -> {
            event.setDropCompleted(true);
            event.consume();
            onFile.accept(path);
        });
    }
}
