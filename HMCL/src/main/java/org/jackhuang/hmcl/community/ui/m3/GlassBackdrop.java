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
 * Modified by LMCL team on 2026-10-29: multi-layer frosted material backdrop.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
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
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import org.glavo.monetfx.ColorRole;
import org.jackhuang.hmcl.theme.LauncherBackground;
import org.jackhuang.hmcl.theme.Themes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 从当前启动器壁纸生成多层低分辨率模糊快照，供 M3 磨砂表面采样。
///
/// `DecoratorSkin` 的真实背景节点带有 `lmcl-launcher-background` 样式类，这里优先
/// 直接快照该节点，确保壁纸的标题栏偏移、窗口缩放和透明度与真实背景完全一致；
/// 节点尚未挂载时才回退到根据 [Themes#backgroundProperty()] 中的 [LauncherBackground]
/// 在离屏画布上重绘壁纸。随后用五个不同半径的 [BoxBlur] 生成五张图：
/// 96/64/40/24/12 像素，由远及近。各层叠加后形成有层次、有细节的多层磨砂，
/// 而不是一张大模糊图加色块。
///
/// 快照以 M3 宿主为坐标原点，只覆盖内容区；[GlassSurface] 会按自身在场景中的
/// 位置裁剪对应区域。没有可绘制壁纸（纯色主题也会绘制）时返回 `null`，调用方
/// 回退到 CSS 的半透明表面色。
///
/// 线程：构造、[attach] 与 [detach] 必须在 JavaFX 应用线程调用。
@NotNullByDefault
public final class GlassBackdrop {

    /// [DecoratorSkin] 真实背景节点的样式类。
    private static final String BACKGROUND_NODE_STYLE = "lmcl-launcher-background";

    /// 快照最大宽度；五层模糊需要更多像素，但不宜按窗口原始尺寸生成。
    private static final double MAX_SNAPSHOT_WIDTH = 960.0;

    /// 快照最大高度。
    private static final double MAX_SNAPSHOT_HEIGHT = 640.0;

    /// 各层模糊在屏幕上的目标半径，从远到近。
    private static final double[] BLUR_RADII_SCENE = {96.0, 64.0, 40.0, 24.0, 12.0};

    /// 各层 BoxBlur 迭代次数；远景多迭代消除色带，近景少迭代保留细节。
    private static final int[] BLUR_ITERATIONS = {3, 3, 2, 2, 2};

    /// 单例。
    private static final GlassBackdrop INSTANCE = new GlassBackdrop();

    /// 当前多层快照；`null` 表示没有可用壁纸或玻璃关闭。
    private final ReadOnlyObjectWrapper<GlassSnapshot> snapshot = new ReadOnlyObjectWrapper<>();

    /// 壁纸变化监听。
    private final InvalidationListener backgroundListener = observable -> schedule();

    /// 宿主尺寸变化监听。
    private final InvalidationListener sizeListener = observable -> schedule();

    /// 宿主场景变化监听：窗口显示/隐藏后重新采样。
    private final InvalidationListener sceneListener = observable -> schedule();

    /// 真实背景节点尺寸/位置变化监听：标题栏出现或高度变化时立即重新对齐。
    private final InvalidationListener backgroundNodeListener = observable -> schedule();

    /// 当前监听加载进度的壁纸图像。
    private @Nullable Image watchedImage;

    /// 壁纸图像加载完成/失败监听。
    private final InvalidationListener imageListener = observable -> {
        Image watched = watchedImage;
        if (watched != null && (watched.isError() || watched.getWidth() > 0.0)) {
            debug("wallpaper image ready: " + describeImage(watched));
            schedule();
        }
    };

    /// 当前宿主；`null` 表示没有采样源。
    private @Nullable Region host;

    /// [DecoratorSkin] 的真实背景节点；尚未挂载时为 `null`。
    private @Nullable Node backgroundNode;

    /// 是否已经注册当前宿主的监听；玻璃开关变化后需要重新挂载。
    private boolean attached;

    /// 是否输出玻璃诊断日志（`-Dlmcl.visual.debug=true`）。
    private static final boolean DEBUG = Boolean.getBoolean("lmcl.visual.debug");

    /// 合并窗口拖动/缩放期间的连续刷新。
    private final PauseTransition refreshDelay = new PauseTransition(Duration.millis(16));

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

    /// 当前多层壁纸快照；`null` 表示当前没有可用快照。
    ///
    /// @return 只读属性
    public ReadOnlyObjectProperty<GlassSnapshot> snapshotProperty() {
        return snapshot.getReadOnlyProperty();
    }

    /// 绑定一个 M3 宿主作为坐标原点与尺寸来源。
    ///
    /// 重复绑定同一宿主无副作用；换宿主会先解除旧监听。
    ///
    /// @param host M3 宿主
    public void attach(@NotNull Region host) {
        if (this.host == host && attached) {
            if (!M3Settings.isGlassEnabled()) {
                detach();
            }
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
        host.sceneProperty().addListener(sceneListener);
        Themes.backgroundProperty().addListener(backgroundListener);
        bindBackgroundNode(host);
        attached = true;
        debug("attach host=" + host.getClass().getSimpleName()
                + " size=" + host.getWidth() + "x" + host.getHeight()
                + " layers=" + layerCount());
        schedule();
    }

    /// 解除当前宿主并清空快照；重复调用无副作用。
    public void detach() {
        Region host = this.host;
        if (host != null) {
            host.widthProperty().removeListener(sizeListener);
            host.heightProperty().removeListener(sizeListener);
            host.sceneProperty().removeListener(sceneListener);
        }
        Themes.backgroundProperty().removeListener(backgroundListener);
        unbindBackgroundNode();
        unwatchImage();
        refreshDelay.stop();
        this.host = null;
        attached = false;
        clear();
    }

    /// 查找并监听 `DecoratorSkin` 的真实背景节点。
    ///
    /// 节点在皮肤构造时创建，通常 `attach` 时已经可以查找；如果还没有，下一次
    /// 刷新时会再次尝试。
    private void bindBackgroundNode(@NotNull Region host) {
        if (backgroundNode != null) {
            return;
        }
        Node node = host.lookup("." + BACKGROUND_NODE_STYLE);
        if (node == null) {
            return;
        }
        backgroundNode = node;
        node.layoutBoundsProperty().addListener(backgroundNodeListener);
        node.localToSceneTransformProperty().addListener(backgroundNodeListener);
        debug("bind background node=" + node.getClass().getSimpleName()
                + " size=" + node.getLayoutBounds().getWidth() + "x" + node.getLayoutBounds().getHeight());
    }

    /// 解除真实背景节点监听。
    private void unbindBackgroundNode() {
        Node node = backgroundNode;
        if (node == null) {
            return;
        }
        node.layoutBoundsProperty().removeListener(backgroundNodeListener);
        node.localToSceneTransformProperty().removeListener(backgroundNodeListener);
        backgroundNode = null;
    }

    /// 清空快照。
    private void clear() {
        snapshot.set(null);
    }

    /// 合并窗口拖动/缩放期间的多次刷新请求。
    private void schedule() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::schedule);
            return;
        }
        // 拖动/缩放期间事件可能每帧都来；已经在等待时不再重置计时器，
        // 保证最迟 16ms 后一定会用最新尺寸刷新，而不是等事件停止才刷新。
        if (refreshDelay.getStatus() != Animation.Status.RUNNING) {
            refreshDelay.playFromStart();
        }
    }

    /// 重新生成多层模糊壁纸。
    private void refresh() {
        Region host = this.host;
        if (host == null || !M3Settings.isGlassEnabled()) {
            clear();
            return;
        }

        double width = host.getWidth();
        double height = host.getHeight();
        if (width < 8.0 || height < 8.0) {
            debug("skip refresh: host too small " + width + "x" + height);
            return;
        }

        if (host.getScene() == null) {
            debug("skip refresh: host is not in a scene");
            return;
        }

        Point2D origin = host.localToScene(0.0, 0.0);
        if (origin == null) {
            debug("skip refresh: host has no scene transform");
            return;
        }

        LauncherBackground background = Themes.backgroundProperty().get();
        watchImage(background);
        bindBackgroundNode(host);

        Snapshot rendered;
        try {
            rendered = render(host, backgroundNode, background, width, height);
        } catch (RuntimeException e) {
            LOG.warning("Failed to capture M3 glass backdrop", e);
            clear();
            return;
        }

        if (rendered == null) {
            debug("snapshot unavailable: host=" + width + "x" + height
                    + ", images=" + background.background().getImages().size()
                    + ", fills=" + background.background().getFills().size());
            clear();
            return;
        }

        double padding = rendered.padding() / rendered.scale();
        GlassSnapshot next = new GlassSnapshot(
                rendered.layers(),
                rendered.scale(),
                origin.getX() - padding,
                origin.getY() - padding,
                rendered.padding());
        snapshot.set(next);
        Image first = rendered.layers().get(0);
        debug("snapshot ready: host=" + width + "x" + height
                + ", layers=" + rendered.layers().size()
                + ", far=" + first.getWidth() + "x" + first.getHeight()
                + ", scale=" + rendered.scale()
                + ", padding=" + rendered.padding()
                + ", origin=" + next.originX() + "," + next.originY());
    }

    /// 监听背景图片的异步加载；加载完成或失败后重新生成快照。
    private void watchImage(@NotNull LauncherBackground background) {
        Image next = firstImage(background.background());
        if (next == watchedImage) {
            return;
        }
        unwatchImage();
        watchedImage = next;
        if (next != null) {
            next.progressProperty().addListener(imageListener);
            next.widthProperty().addListener(imageListener);
            next.errorProperty().addListener(imageListener);
            debug("watch wallpaper image: " + describeImage(next));
        }
    }

    /// 解除壁纸图片监听。
    private void unwatchImage() {
        Image watched = watchedImage;
        if (watched != null) {
            watched.progressProperty().removeListener(imageListener);
            watched.widthProperty().removeListener(imageListener);
            watched.errorProperty().removeListener(imageListener);
        }
        watchedImage = null;
    }

    /// 返回背景中的第一张图片，可能尚未加载完成。
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

    /// 输出诊断信息；仅在 `-Dlmcl.visual.debug=true` 时生效。
    private static void debug(@NotNull String message) {
        if (DEBUG) {
            LOG.info("[glass] " + message);
        }
    }

    /// 描述图片加载状态，便于诊断异步壁纸。
    @NotNull
    private static String describeImage(@NotNull Image image) {
        return image.getWidth() + "x" + image.getHeight()
                + " progress=" + image.getProgress()
                + " error=" + image.isError();
    }

    /// 计算屏幕半径换算到快照像素后的模糊半径。
    ///
    /// @param sceneRadius 屏幕上的目标半径
    /// @param scale       快照像素 / 场景像素
    /// @return 像素半径，至少 1
    static double blurRadius(double sceneRadius, double scale) {
        return Math.max(1.0, sceneRadius * scale);
    }

    /// 模糊图层数量。
    ///
    /// @return 图层数量
    static int layerCount() {
        return BLUR_RADII_SCENE.length;
    }

    /// 指定图层在屏幕坐标中的模糊半径。
    ///
    /// @param layer 图层序号
    /// @return 屏幕半径
    static double blurSceneRadiusAt(int layer) {
        return BLUR_RADII_SCENE[layer];
    }

    /// 指定图层的 BoxBlur 迭代次数。
    ///
    /// @param layer 图层序号
    /// @return 迭代次数
    static int iterationsAt(int layer) {
        return BLUR_ITERATIONS[layer];
    }

    /// 根据快照比例计算画布四周需要的 padding。
    ///
    /// @param scale 快照像素 / 场景像素
    /// @return 像素 padding
    static int paddingFor(double scale) {
        double farBlur = blurRadius(BLUR_RADII_SCENE[0], scale);
        return (int) Math.ceil(farBlur * 1.5) + 2;
    }

    /// 渲染并生成一张多层壁纸快照。
    ///
    /// 优先直接快照 `DecoratorSkin` 的真实背景节点，这样标题栏高度、背景节点的
    /// `BOTTOM_CENTER` 对齐、透明度与窗口缩放都与真实画面一致；只有节点不可用
    /// 时才回退到手动重绘壁纸。
    ///
    /// @param host            M3 宿主
    /// @param backgroundNode  真实背景节点，可能为 `null`
    /// @param source          当前壁纸
    /// @param width           宿主宽度
    /// @param height          宿主高度
    /// @return 快照；没有可绘制内容时返回 `null`
    @Nullable
    private static Snapshot render(
            @NotNull Region host,
            @Nullable Node backgroundNode,
            @NotNull LauncherBackground source,
            double width,
            double height) {
        double snapshotScale = Math.min(1.0, Math.min(MAX_SNAPSHOT_WIDTH / width, MAX_SNAPSHOT_HEIGHT / height));
        int pixelWidth = Math.max(2, (int) Math.ceil(width * snapshotScale));
        int pixelHeight = Math.max(2, (int) Math.ceil(height * snapshotScale));

        int padding = paddingFor(snapshotScale);
        int canvasWidth = pixelWidth + padding * 2;
        int canvasHeight = pixelHeight + padding * 2;

        // 画面向四周多渲染 padding 像素，模糊后中心区域没有透明边；采样原点
        // 相应左/上移 padding / scale，玻璃切片仍与场景坐标对齐。
        Canvas canvas = new Canvas(canvasWidth, canvasHeight);
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        graphics.setImageSmoothing(true);

        // 非透明窗口的 wrapper 是 SURFACE_CONTAINER；先铺这层，再叠加可能带
        // 透明度的真实背景节点，避免标题栏/壁纸透明处被模糊成黑色。
        if (!Themes.windowTransparentProperty().get()) {
            graphics.setFill(Themes.getColorScheme().getColor(ColorRole.SURFACE_CONTAINER));
            graphics.fillRect(0.0, 0.0, canvasWidth, canvasHeight);
        }

        boolean painted = paintBackgroundNode(
                graphics, host, backgroundNode, snapshotScale, padding, canvasWidth, canvasHeight);
        if (!painted) {
            graphics.save();
            graphics.translate(padding, padding);
            graphics.setGlobalAlpha(source.opacity());
            painted = paintImages(graphics, source.background(), pixelWidth, pixelHeight)
                    || paintFill(graphics, source.background(), pixelWidth, pixelHeight);
            graphics.restore();
        }

        if (!painted) {
            return null;
        }

        List<Image> layers = new ArrayList<>(BLUR_RADII_SCENE.length);
        for (int i = 0; i < BLUR_RADII_SCENE.length; i++) {
            layers.add(blur(canvas,
                    blurRadius(BLUR_RADII_SCENE[i], snapshotScale),
                    BLUR_ITERATIONS[i],
                    canvasWidth,
                    canvasHeight));
        }
        return new Snapshot(List.copyOf(layers), snapshotScale, padding);
    }

    /// 将真实背景节点按场景位置绘制到 padding 画布中。
    ///
    /// 背景节点由 `DecoratorSkin` 以 `BOTTOM_CENTER` 对齐，并可能在标题栏非透明时
    /// 比宿主矮；直接快照节点并按 `localToScene` 偏移绘制，才能保证玻璃采样与
    /// 画面中的壁纸严格对齐。
    ///
    /// @return 是否绘制成功
    private static boolean paintBackgroundNode(
            @NotNull GraphicsContext graphics,
            @NotNull Region host,
            @Nullable Node node,
            double scale,
            int padding,
            int canvasWidth,
            int canvasHeight) {
        if (node == null || node.getScene() == null) {
            return false;
        }
        if (!node.isVisible() || node.getOpacity() <= 0.0) {
            return false;
        }

        Bounds bounds = node.getLayoutBounds();
        double nodeWidth = bounds.getWidth();
        double nodeHeight = bounds.getHeight();
        if (nodeWidth < 1.0 || nodeHeight < 1.0) {
            return false;
        }

        Point2D hostOrigin = host.localToScene(0.0, 0.0);
        Point2D nodeOrigin = node.localToScene(0.0, 0.0);
        if (hostOrigin == null || nodeOrigin == null) {
            return false;
        }

        int imageWidth = Math.max(1, (int) Math.ceil(nodeWidth * scale));
        int imageHeight = Math.max(1, (int) Math.ceil(nodeHeight * scale));

        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        parameters.setTransform(new Scale(scale, scale));
        WritableImage image = new WritableImage(imageWidth, imageHeight);
        try {
            node.snapshot(parameters, image);
        } catch (RuntimeException e) {
            debug("background node snapshot failed: " + e);
            return false;
        }

        double x = padding + (nodeOrigin.getX() - hostOrigin.getX()) * scale;
        double y = padding + (nodeOrigin.getY() - hostOrigin.getY()) * scale;
        graphics.drawImage(image, x, y, imageWidth, imageHeight);
        extendEdges(graphics, image, x, y, imageWidth, imageHeight, padding, canvasWidth, canvasHeight);
        return true;
    }

    /// 把图片边缘拉伸到画布四周，避免 BoxBlur 在 padding 区域产生透明暗边。
    ///
    /// 只在背景节点紧贴宿主边缘时向 padding 外延伸；如果节点因为标题栏而整体
    /// 下移，节点上方的区域应保留主题表面色，不能把壁纸顶边拉伸进标题栏。
    private static void extendEdges(
            @NotNull GraphicsContext graphics,
            @NotNull Image image,
            double x,
            double y,
            double width,
            double height,
            int padding,
            int canvasWidth,
            int canvasHeight) {
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();
        if (x > 0.0 && x <= padding) {
            graphics.drawImage(image, 0.0, 0.0, 1.0, imageHeight, 0.0, y, x, height);
        }
        if (x + width < canvasWidth && x + width >= canvasWidth - padding) {
            graphics.drawImage(image, imageWidth - 1.0, 0.0, 1.0, imageHeight,
                    x + width, y, canvasWidth - (x + width), height);
        }
        if (y > 0.0 && y <= padding) {
            graphics.drawImage(image, 0.0, 0.0, imageWidth, 1.0, x, 0.0, width, y);
        }
        if (y + height < canvasHeight && y + height >= canvasHeight - padding) {
            graphics.drawImage(image, 0.0, imageHeight - 1.0, imageWidth, 1.0,
                    x, y + height, width, canvasHeight - (y + height));
        }
    }

    /// 对画布应用一次高斯模糊并生成图片。
    ///
    /// @param canvas     已绘制壁纸的画布
    /// @param radius     模糊半径（像素）
    /// @param iterations BoxBlur 迭代次数
    /// @param width      输出宽度
    /// @param height     输出高度
    /// @return 模糊图
    @NotNull
    private static Image blur(@NotNull Canvas canvas, double radius, int iterations, int width, int height) {
        canvas.setEffect(new BoxBlur(Math.max(1.0, radius), Math.max(1.0, radius), Math.max(1, iterations)));
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        WritableImage output = new WritableImage(width, height);
        canvas.snapshot(parameters, output);
        return output;
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

    /// 一张多层快照的原始渲染结果。
    ///
    /// @param layers  模糊图层
    /// @param scale   像素 / 场景像素
    /// @param padding 四周额外渲染的像素数
    private record Snapshot(List<Image> layers, double scale, int padding) {
    }
}
