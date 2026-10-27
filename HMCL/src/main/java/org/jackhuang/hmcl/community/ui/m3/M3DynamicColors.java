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
import javafx.collections.SetChangeListener;
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
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.ThemeColorType;
import org.jackhuang.hmcl.theme.LauncherBackground;
import org.jackhuang.hmcl.theme.Themes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/// 生成 Material 3 动态取色方案，作为 M3 界面与标题栏的统一颜色来源。
///
/// 只有用户在外观设置中选择“跟随壁纸（莫奈）”并且该选择覆盖主题包时，才从
/// 当前壁纸图片生成完整动态方案；其他情况（默认、自定义强调色、跟随系统、
/// 主题包默认）直接采用上游 `Themes.getColorScheme()`，保证 M3 界面与上游
/// 主题设置一致。纯色背景在壁纸不可用时用背景色作种子色。
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

    /// 主题色覆盖项增删时重新生成。
    private final SetChangeListener<String> overrideListener = change -> {
        if (LauncherSettings.THEME_APPEARANCE_COLOR.equals(change.getElementAdded())
                || LauncherSettings.THEME_APPEARANCE_COLOR.equals(change.getElementRemoved())) {
            refresh();
        }
    };

    /// 最近一次观察加载状态的图片。
    private @Nullable Image watchedImage;

    /// 代数号，避免旧图片的异步结果覆盖新壁纸。
    private int generation;

    /// 单例构造。
    private M3DynamicColors() {
        Themes.backgroundProperty().addListener(refreshListener);
        Themes.colorSchemeProperty().addListener(refreshListener);

        LauncherSettings settings = SettingsManager.settings();
        settings.themeColorTypeProperty().addListener(refreshListener);
        settings.customThemeColorProperty().addListener(refreshListener);
        settings.getThemeAppearanceOverrides().addListener(overrideListener);
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

    /// 当前取色方案。
    ///
    /// @return 当前方案；初始化完成前或未启用莫奈取色时为 `Themes.getColorScheme()`
    public ColorScheme getScheme() {
        ColorScheme current = scheme.get();
        return current != null ? current : Themes.getColorScheme();
    }

    /// 重新计算方案。
    ///
    /// 只有“跟随壁纸”覆盖生效时才做壁纸取色；其余模式直接跟随 `Themes`，
    /// 这样自定义强调色、跟随系统和主题包配色都会同步到 M3 界面。
    private void refresh() {
        int current = ++generation;
        boolean wallpaperAccent = isWallpaperAccent();
        if (!wallpaperAccent) {
            watchImage(null);
            scheme.set(Themes.getColorScheme());
            return;
        }

        LauncherBackground background = Themes.backgroundProperty().get();
        if (background == null) {
            watchImage(null);
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

    /// 判断当前是否应使用壁纸莫奈取色。
    ///
    /// 必须同时满足：主题色覆盖已启用、覆盖类型为“跟随壁纸”。
    private static boolean isWallpaperAccent() {
        LauncherSettings settings = SettingsManager.settings();
        if (!settings.getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_COLOR)) {
            return false;
        }
        return Objects.requireNonNullElse(settings.themeColorTypeProperty().get(), ThemeColorType.DEFAULT)
                == ThemeColorType.BACKGROUND;
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
