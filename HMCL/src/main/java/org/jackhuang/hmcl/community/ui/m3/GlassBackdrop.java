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
 * Modified by LMCL team on 2026-10-23: wallpaper snapshot source for M3 glass.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.geometry.Point2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BoxBlur;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.util.Duration;
import org.jackhuang.hmcl.theme.LauncherBackground;
import org.jackhuang.hmcl.theme.Themes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 从当前启动器壁纸生成一张低分辨率模糊快照，供 M3 玻璃表面采样。
///
/// `DecoratorSkin` 的背景节点是私有的，社区版无法直接读取；这里改为根据
/// [Themes#backgroundProperty()] 中的 [LauncherBackground] 在离屏画布上重绘壁纸，
/// 再用 [BoxBlur] 生成模糊图。这样既不侵入 `DecoratorSkin`，也能跟随主题切换。
///
/// 快照以 M3 宿主为坐标原点，只覆盖内容区；[GlassSurface] 会按自身在场景中的
/// 位置裁剪对应区域。没有可绘制壁纸（纯色主题也会绘制）时返回 `null`，调用方
/// 回退到 CSS 的半透明表面色。
///
/// 线程：构造、[attach] 与 [detach] 必须在 JavaFX 应用线程调用。
@NotNullByDefault
public final class GlassBackdrop {

    /// 快照最大宽度，降低模糊与内存开销。
    private static final double MAX_SNAPSHOT_WIDTH = 640.0;

    /// 快照最大高度。
    private static final double MAX_SNAPSHOT_HEIGHT = 400.0;

    /// 单例。
    private static final GlassBackdrop INSTANCE = new GlassBackdrop();

    /// 模糊后的壁纸快照。
    private final ReadOnlyObjectWrapper<Image> image = new ReadOnlyObjectWrapper<>();

    /// 快照对应的场景 X 原点。
    private final ReadOnlyDoubleWrapper originX = new ReadOnlyDoubleWrapper();

    /// 快照对应的场景 Y 原点。
    private final ReadOnlyDoubleWrapper originY = new ReadOnlyDoubleWrapper();

    /// 快照像素 / 场景像素。
    private final ReadOnlyDoubleWrapper scale = new ReadOnlyDoubleWrapper(1.0);

    /// 壁纸变化监听。
    private final InvalidationListener backgroundListener = observable -> schedule();

    /// 宿主尺寸变化监听。
    private final InvalidationListener sizeListener = observable -> schedule();

    /// 当前宿主；`null` 表示没有采样源。
    private @Nullable Region host;

    /// 合并窗口拖动/缩放期间的连续刷新。
    private final PauseTransition refreshDelay = new PauseTransition(Duration.millis(160));

    /// 工具类，不允许实例化。
    private GlassBackdrop() {
        refreshDelay.setOnFinished(event -> refresh());
    }

    /// 返回单例。
    ///
    /// @return 单例
    public static GlassBackdrop get() {
        return INSTANCE;
    }

    /// 模糊壁纸快照属性；`null` 表示当前没有可用壁纸。
    ///
    /// @return 只读属性
    public ReadOnlyObjectProperty<Image> imageProperty() {
        return image.getReadOnlyProperty();
    }

    /// 快照对应的场景 X 原点。
    ///
    /// @return 只读属性
    public ReadOnlyDoubleProperty originXProperty() {
        return originX.getReadOnlyProperty();
    }

    /// 快照对应的场景 Y 原点。
    ///
    /// @return 只读属性
    public ReadOnlyDoubleProperty originYProperty() {
        return originY.getReadOnlyProperty();
    }

    /// 快照像素与场景像素的比例。
    ///
    /// @return 只读属性
    public ReadOnlyDoubleProperty scaleProperty() {
        return scale.getReadOnlyProperty();
    }

    /// 绑定一个 M3 宿主作为坐标原点与尺寸来源。
    ///
    /// 重复绑定同一宿主无副作用；换宿主会先解除旧监听。
    ///
    /// @param host M3 宿主
    public void attach(@NotNull Region host) {
        if (this.host == host) {
            return;
        }
        detach();
        this.host = host;
        if (!M3Settings.isGlassEnabled()) {
            clear();
            return;
        }

        host.widthProperty().addListener(sizeListener);
        host.heightProperty().addListener(sizeListener);
        Themes.backgroundProperty().addListener(backgroundListener);
        schedule();
    }

    /// 解除当前宿主并清空快照；重复调用无副作用。
    public void detach() {
        Region host = this.host;
        if (host != null) {
            host.widthProperty().removeListener(sizeListener);
            host.heightProperty().removeListener(sizeListener);
        }
        Themes.backgroundProperty().removeListener(backgroundListener);
        refreshDelay.stop();
        this.host = null;
        clear();
    }

    /// 清空快照。
    private void clear() {
        image.set(null);
        originX.set(0.0);
        originY.set(0.0);
        scale.set(1.0);
    }

    /// 合并窗口拖动/缩放期间的多次刷新请求。
    private void schedule() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::schedule);
            return;
        }
        refreshDelay.playFromStart();
    }

    /// 重新生成模糊壁纸。
    private void refresh() {
        Region host = this.host;
        if (host == null || !M3Settings.isGlassEnabled()) {
            clear();
            return;
        }

        double width = host.getWidth();
        double height = host.getHeight();
        if (width < 8.0 || height < 8.0) {
            return;
        }

        Point2D origin = host.localToScene(0.0, 0.0);
        if (origin == null) {
            return;
        }

        LauncherBackground background = Themes.backgroundProperty().get();
        Snapshot snapshot;
        try {
            snapshot = render(background, width, height);
        } catch (RuntimeException e) {
            LOG.warning("Failed to capture M3 glass backdrop", e);
            clear();
            return;
        }

        if (snapshot == null) {
            clear();
            return;
        }

        double padding = snapshot.padding() / snapshot.scale();
        originX.set(origin.getX() - padding);
        originY.set(origin.getY() - padding);
        scale.set(snapshot.scale());
        image.set(snapshot.image());
    }

    /// 渲染并模糊一张壁纸快照。
    ///
    /// @param source 当前壁纸
    /// @param width  宿主宽度
    /// @param height 宿主高度
    /// @return 快照；没有可绘制内容时返回 `null`
    @Nullable
    private static Snapshot render(@NotNull LauncherBackground source, double width, double height) {
        double snapshotScale = Math.min(1.0, Math.min(MAX_SNAPSHOT_WIDTH / width, MAX_SNAPSHOT_HEIGHT / height));
        int pixelWidth = Math.max(2, (int) Math.ceil(width * snapshotScale));
        int pixelHeight = Math.max(2, (int) Math.ceil(height * snapshotScale));

        double blur = Math.max(6.0, Math.min(18.0, pixelWidth / 18.0));
        int padding = (int) Math.ceil(blur) + 2;
        int canvasWidth = pixelWidth + padding * 2;
        int canvasHeight = pixelHeight + padding * 2;

        // 画面向四周多渲染 padding 像素，模糊后中心区域没有透明边；采样原点
        // 相应左/上移 padding / scale，玻璃切片仍与场景坐标对齐。
        Canvas canvas = new Canvas(canvasWidth, canvasHeight);
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setImageSmoothing(true);

        if (!paintImages(graphics, source.background(), canvasWidth, canvasHeight)
                && !paintFill(graphics, source.background(), canvasWidth, canvasHeight)) {
            return null;
        }

        canvas.setEffect(new BoxBlur(blur, blur, 2));

        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        WritableImage output = new WritableImage(canvasWidth, canvasHeight);
        canvas.snapshot(parameters, output);
        return new Snapshot(output, snapshotScale, padding);
    }

    /// 用第一张背景图片按 cover 方式铺满画布。
    ///
    /// @return 是否绘制成功
    private static boolean paintImages(
            @NotNull GraphicsContext graphics,
            @NotNull Background background,
            double width,
            double height) {
        for (BackgroundImage backgroundImage : background.getImages()) {
            Image image = backgroundImage.getImage();
            if (image == null || image.isError() || image.getWidth() <= 0.0 || image.getHeight() <= 0.0) {
                continue;
            }

            double imageScale = Math.max(width / image.getWidth(), height / image.getHeight());
            double drawWidth = image.getWidth() * imageScale;
            double drawHeight = image.getHeight() * imageScale;
            graphics.drawImage(image,
                    (width - drawWidth) / 2.0,
                    (height - drawHeight) / 2.0,
                    drawWidth,
                    drawHeight);
            return true;
        }
        return false;
    }

    /// 用第一个背景填充色/渐变铺满画布。
    ///
    /// @return 是否绘制成功
    private static boolean paintFill(
            @NotNull GraphicsContext graphics,
            @NotNull Background background,
            double width,
            double height) {
        for (BackgroundFill fill : background.getFills()) {
            Paint paint = fill.getFill();
            if (paint == null) {
                continue;
            }
            graphics.setFill(paint);
            graphics.fillRect(0.0, 0.0, width, height);
            return true;
        }
        return false;
    }

    /// 一张快照及其像素比例。
    ///
    /// @param image   模糊图
    /// @param scale   像素 / 场景像素
    /// @param padding 四周额外渲染的像素数
    private record Snapshot(Image image, double scale, int padding) {
    }
}
