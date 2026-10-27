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
 *
 * Modified by LMCL team on 2026-10-28: wallpaper-based Material 3 dynamic colors.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.glavo.monetfx.ColorScheme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.theme.LauncherBackground;
import org.jackhuang.hmcl.theme.Themes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/// 从当前启动器壁纸生成 Material 3 动态取色方案。
///
/// 上游 `Themes` 的颜色方案只有在“主题色跟随背景”时才由壁纸驱动；默认主题
/// 使用固定的种子色。这里按 MD3 的动态取色规则直接从壁纸图片生成方案，供位于
/// M3 宿主之外的标题栏使用；纯色背景则退回当前 `Themes` 方案。
///
/// 图片可能在异步加载，生成过程放在 IO 线程，结果回到 JavaFX 线程后再发布；
/// 过期结果通过代数号丢弃。线程：构造与属性监听都在 JavaFX 应用线程。
@NotNullByDefault
public final class M3DynamicColors {

    /// 单例；标题栏等壳层节点共享同一份结果。
    private static final M3DynamicColors INSTANCE = new M3DynamicColors();

    /// 当前动态取色方案。
    private final ReadOnlyObjectWrapper<ColorScheme> scheme = new ReadOnlyObjectWrapper<>();

    /// 壁纸、主题色或图片加载状态变化时重新生成。
    private final InvalidationListener refreshListener = observable -> refresh();

    /// 最近一次观察加载状态的图片。
    private @Nullable Image watchedImage;

    /// 代数号，避免旧图片的异步结果覆盖新壁纸。
    private int generation;

    /// 单例构造。
    private M3DynamicColors() {
        Themes.backgroundProperty().addListener(refreshListener);
        Themes.colorSchemeProperty().addListener(refreshListener);
        refresh();
    }

    /// 返回共享实例。
    ///
    /// @return 动态取色单例
    public static M3DynamicColors get() {
        return INSTANCE;
    }

    /// 当前动态方案属性；壁纸变化后异步更新。
    ///
    /// @return 只读属性
    public ReadOnlyObjectProperty<ColorScheme> schemeProperty() {
        return scheme.getReadOnlyProperty();
    }

    /// 当前动态方案。
    ///
    /// @return 当前方案；初始化完成前为 `Themes.getColorScheme()`
    public ColorScheme getScheme() {
        ColorScheme current = scheme.get();
        return current != null ? current : Themes.getColorScheme();
    }

    /// 重新计算方案。
    private void refresh() {
        int current = ++generation;
        LauncherBackground background = Themes.backgroundProperty().get();
        if (background == null) {
            scheme.set(Themes.getColorScheme());
            return;
        }
        Image image = firstImage(background.background());
        if (image != null && !image.isError() && image.getWidth() > 0.0 && image.getProgress() >= 1.0) {
            watchImage(null);
            applyWallpaper(image, current);
            return;
        }

        watchImage(image);
        Paint fill = firstFill(background.background());
        if (fill instanceof Color color) {
            scheme.set(ColorScheme.newBuilder(Themes.getColorScheme())
                    .setPrimaryColorSeed(color)
                    .build());
        } else {
            scheme.set(Themes.getColorScheme());
        }
    }

    /// 在 IO 线程从壁纸生成方案，避免大图取色阻塞 UI。
    private void applyWallpaper(@NotNull Image image, int current) {
        ColorScheme base = Themes.getColorScheme();
        CompletableFuture
                .supplyAsync(() -> ColorScheme.newBuilder(base).setWallpaper(image).build(), Schedulers.io())
                .thenAcceptAsync(computed -> {
                    if (current == generation) {
                        scheme.set(computed);
                    }
                }, Schedulers.javafx())
                .exceptionally(throwable -> {
                    // 取色失败时保持当前方案，不影响标题栏显示。
                    return null;
                });
    }

    /// 观察异步图片的加载完成；重复观察同一图片无副作用。
    private void watchImage(@Nullable Image image) {
        if (image == watchedImage) {
            return;
        }
        if (watchedImage != null) {
            watchedImage.progressProperty().removeListener(refreshListener);
            watchedImage.widthProperty().removeListener(refreshListener);
            watchedImage.errorProperty().removeListener(refreshListener);
        }
        watchedImage = image;
        if (image != null) {
            image.progressProperty().addListener(refreshListener);
            image.widthProperty().addListener(refreshListener);
            image.errorProperty().addListener(refreshListener);
        }
    }

    /// 返回背景中的第一张图片。
    @Nullable
    private static Image firstImage(@NotNull Background background) {
        for (BackgroundImage backgroundImage : background.getImages()) {
            Image image = backgroundImage.getImage();
            if (image != null) {
                return image;
            }
        }
        return null;
    }

    /// 返回背景中的第一个纯色填充。
    @Nullable
    private static Paint firstFill(@NotNull Background background) {
        for (BackgroundFill fill : background.getFills()) {
            Paint paint = fill.getFill();
            if (paint != null) {
                return paint;
            }
        }
        return null;
    }
}
