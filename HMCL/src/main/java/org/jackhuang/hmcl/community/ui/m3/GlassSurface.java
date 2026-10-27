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
 * Modified by LMCL team on 2026-10-23: M3 glass surface.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 一块采样壁纸快照的 M3 玻璃表面。
///
/// 结构为“模糊壁纸切片 + 半透明表面色 + 圆角裁剪”。它只读取
/// [GlassBackdrop] 的快照，不截取下层真实 UI，因此不会形成递归渲染；没有快照时
/// 自动隐藏，由宿主自己的 CSS 表面色兜底。
///
/// 本节点不处理鼠标事件，必须作为内容层的背景使用。线程：构造与属性监听都在
/// JavaFX 应用线程。
@NotNullByDefault
public final class GlassSurface extends StackPane {

    /// 模糊壁纸切片。
    private final ImageView imageView = new ImageView();

    /// 半透明表面色。
    private final Region tint = new Region();

    /// 顶部高光，模拟亚克力的边缘反射。
    private final Region sheen = new Region();

    /// 细颗粒磨砂层。
    private final Region noise = new Region();

    /// 96×96 的可重复磨砂颗粒纹理，进程内只生成一次。
    private static final Image NOISE_TEXTURE = createNoiseTexture();

    /// 圆角裁剪。
    private final Rectangle clip = new Rectangle();

    /// 快照变化监听。
    private final InvalidationListener imageListener = observable -> update();

    /// 对单例属性的弱监听，避免侧栏重建后旧表面无法回收。
    private final InvalidationListener weakImageListener = new WeakInvalidationListener(imageListener);

    /// 自身尺寸变化监听。
    private final InvalidationListener geometryListener = observable -> update();

    /// 是否输出玻璃诊断日志。
    private static final boolean DEBUG = Boolean.getBoolean("lmcl.visual.debug");

    /// 创建一个直角玻璃表面。
    public GlassSurface() {
        this(0.0);
    }

    /// 创建一个带圆角的玻璃表面。
    ///
    /// @param arc 圆角直径（像素）
    public GlassSurface(double arc) {
        getStyleClass().add("lmcl-m3-glass-surface");
        setMouseTransparent(true);
        setPickOnBounds(false);
        setMinSize(0.0, 0.0);

        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);
        imageView.setMouseTransparent(true);
        imageView.setVisible(false);

        tint.getStyleClass().add("lmcl-m3-glass-tint");
        tint.setMouseTransparent(true);
        tint.setVisible(false);

        sheen.getStyleClass().add("lmcl-m3-glass-sheen");
        sheen.setMouseTransparent(true);
        sheen.setBackground(new Background(new BackgroundFill(
                new LinearGradient(
                        0.0, 0.0, 0.0, 1.0,
                        true, CycleMethod.NO_CYCLE,
                        new Stop(0.0, Color.rgb(255, 255, 255, 0.14)),
                        new Stop(0.38, Color.TRANSPARENT)),
                CornerRadii.EMPTY, Insets.EMPTY)));

        noise.getStyleClass().add("lmcl-m3-glass-noise");
        noise.setMouseTransparent(true);
        noise.setBlendMode(BlendMode.OVERLAY);
        noise.setOpacity(0.55);
        noise.setBackground(new Background(new BackgroundImage(
                NOISE_TEXTURE,
                BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                BackgroundPosition.DEFAULT,
                new BackgroundSize(
                        NOISE_TEXTURE.getWidth(), NOISE_TEXTURE.getHeight(),
                        false, false, false, false))));

        clip.setArcWidth(Math.max(0.0, arc));
        clip.setArcHeight(Math.max(0.0, arc));
        setClip(clip);

        getChildren().addAll(imageView, tint, sheen, noise);
        setVisible(false);

        widthProperty().addListener(geometryListener);
        heightProperty().addListener(geometryListener);
        sceneProperty().addListener(geometryListener);
        localToSceneTransformProperty().addListener(geometryListener);
        GlassBackdrop.get().imageProperty().addListener(weakImageListener);
        GlassBackdrop.get().scaleProperty().addListener(weakImageListener);
        GlassBackdrop.get().originXProperty().addListener(weakImageListener);
        GlassBackdrop.get().originYProperty().addListener(weakImageListener);
        update();
    }

    /// 根据当前快照与场景位置刷新切片。
    private void update() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 1.0 || height <= 1.0) {
            setVisible(false);
            debug("hidden: size " + width + "x" + height);
            return;
        }

        clip.setWidth(width);
        clip.setHeight(height);

        if (getScene() == null) {
            setVisible(false);
            debug("hidden: no scene");
            return;
        }

        @Nullable Image image = GlassBackdrop.get().imageProperty().get();
        if (image == null || image.isError() || image.getWidth() <= 0.0 || image.getHeight() <= 0.0) {
            setVisible(false);
            debug("hidden: no snapshot");
            return;
        }

        Point2D origin = localToScene(0.0, 0.0);
        if (origin == null) {
            setVisible(false);
            debug("hidden: no scene transform");
            return;
        }

        double scale = GlassBackdrop.get().scaleProperty().get();
        double imageX = (origin.getX() - GlassBackdrop.get().originXProperty().get()) * scale;
        double imageY = (origin.getY() - GlassBackdrop.get().originYProperty().get()) * scale;
        double imageWidth = width * scale;
        double imageHeight = height * scale;

        if (!setViewport(image, imageX, imageY, imageWidth, imageHeight)) {
            setVisible(false);
            debug("hidden: viewport out of snapshot");
            return;
        }

        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setVisible(true);
        tint.setVisible(true);
        setVisible(true);
        debug("shown: " + width + "x" + height
                + " viewport=" + imageView.getViewport()
                + " snapshot=" + image.getWidth() + "x" + image.getHeight());
    }

    /// 生成一张低对比度噪点纹理，叠在模糊壁纸上形成磨砂亚克力的颗粒感。
    ///
    /// 使用固定种子保证每次启动的颗粒分布一致，避免切换主题时纹理跳动。
    ///
    /// @return 可重复的噪点纹理
    private static Image createNoiseTexture() {
        int size = 96;
        WritableImage image = new WritableImage(size, size);
        PixelWriter writer = image.getPixelWriter();
        Random random = new Random(0x4C4D434C5F4E4F49L);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double gray = 0.35 + random.nextDouble() * 0.65;
                double alpha = 0.05 + random.nextDouble() * 0.09;
                writer.setColor(x, y, new Color(gray, gray, gray, alpha));
            }
        }
        return image;
    }

    /// 输出诊断信息；仅在 `-Dlmcl.visual.debug=true` 时生效。
    private static void debug(@NotNull String message) {
        if (DEBUG) {
            LOG.info("[glass-surface] " + message);
        }
    }

    /// 设置一个不会越出快照边界的视口。
    ///
    /// @return 视口是否有效
    private boolean setViewport(
            @NotNull Image image,
            double x,
            double y,
            double width,
            double height) {
        double clampedX = clamp(x, 0.0, Math.max(0.0, image.getWidth() - 1.0));
        double clampedY = clamp(y, 0.0, Math.max(0.0, image.getHeight() - 1.0));
        double clampedWidth = Math.min(width, image.getWidth() - clampedX);
        double clampedHeight = Math.min(height, image.getHeight() - clampedY);
        if (clampedWidth <= 1.0 || clampedHeight <= 1.0) {
            return false;
        }

        imageView.setViewport(new Rectangle2D(clampedX, clampedY, clampedWidth, clampedHeight));
        return true;
    }

    /// 限制数值范围。
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
