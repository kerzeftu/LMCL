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
 * Modified by LMCL team on 2026-11-01: scroll-driven viewport edge fade band.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.beans.InvalidationListener;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.glavo.monetfx.ColorRole;
import org.jackhuang.hmcl.theme.Themes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 一条覆盖在滚动区视口边缘的渐隐带。
///
/// 渐隐带覆盖在滚动区上方（[Edge#TOP]）或下方（[Edge#BOTTOM]），显示「真实页面背景」
/// 与滚动内容之间的平滑过渡。整条带的像素只在几何/壁纸/主题变化时渲染一次：生成的是
/// 完整的 [FADE_HEIGHT] 行 alpha ramp，滚动时只通过 [ImageView#setFitHeight(double)] 把
/// 整条 ramp 压缩到当前需要的行数并重新定位，不再逐像素重建，因此滚动过程不会闪烁或
/// 抖动。过渡带渲染的不是纯色块，而是与卡片同质的材质（24 px 块平均 + 22% 表面色
/// tint，外缘同样为材质色），因此视觉上内容像渐隐进页面本身，而不是被一条色带
/// 盖住。
///
/// 过渡带只在滚动区确实还有隐藏内容时出现：起始位置不显示上边缘，滚到底部不显示
/// 下边缘；滚动距离为 0 时隐藏，1–72 px 按 1 px 量化成对应可见行数。渐隐带自身完全
/// 不可交互（`mouseTransparent`），任何无法可靠复现页面背景的场景（包括透明窗口）都会
/// 退化为隐藏，绝不遮挡内容。
///
/// 距离由调用方通过 [setDistance(double)] 传入（顶部为已滚过的距离，底部为剩余可滚
/// 距离），调用方还需监听滚动位置与滚动区尺寸。本类不读取 `ScrollPane`，以便复用和
/// 测试。
@NotNullByDefault
public final class GlassEdgeFade extends Region {

    /// 过渡带方向。
    public enum Edge {

        /// 覆盖在滚动区顶部的过渡带。
        TOP,

        /// 覆盖在滚动区底部的过渡带。
        BOTTOM
    }

    /// 过渡带的逻辑高度（像素），与 PRD 中的 72 px 过渡带一致。
    public static final double FADE_HEIGHT = 72.0;

    /// 滚动距离的量化步长（像素）：每 1 px 滚动都更新可见行数（只缩放图像，不重新渲染）。
    static final double DISTANCE_STEP = 1.0;

    /// 页面背景节点的尺寸容差（场景像素），用于判断滚动区左右边缘是否完整落在背景内。
    private static final double GEOMETRY_TOLERANCE = 0.5;

    /// 是否输出 `-Dlmcl.visual.debug` 调试日志。
    private static final boolean DEBUG = Boolean.getBoolean("lmcl.visual.debug");

    /// 本过渡带的方向，创建后不可变。
    private final Edge edge;

    /// 实际承载渐隐像素的图像视图；由本节点手工布局，不参与父容器的布局计算。
    private final ImageView imageView = new ImageView();

    /// 滚动区滚动几何变化时重建渐隐带。
    private final InvalidationListener geometryListener = observable -> update();

    /// 主题或窗口透明度变化时重建渐隐带。
    private final InvalidationListener themeListener = observable -> update();

    /// 调用方最近一次请求的滚动距离（未量化）。
    private double requestedDistance;

    /// 当前已按 [DISTANCE_STEP] 量化并应用的过渡带行数；小于等于 0 表示隐藏。
    private int appliedRows = -1;

    /// 资源是否已释放。
    private boolean disposed;

    /// 最近一次调试输出，避免布局和滚动事件刷屏。
    @Nullable
    private String lastDebugState;

    /// 创建一条指定方向的过渡带。
    ///
    /// @param edge 过渡带方向
    public GlassEdgeFade(@NotNull Edge edge) {
        this.edge = edge;
        setVisible(false);
        setMouseTransparent(true);
        setPickOnBounds(false);
        setMinWidth(0.0);
        setMinHeight(0.0);
        setMaxSize(Double.MAX_VALUE, FADE_HEIGHT);
        setPrefHeight(FADE_HEIGHT);
        imageView.setManaged(false);
        imageView.setMouseTransparent(true);
        getChildren().add(imageView);

        GlassBackdrop.get().liveRevisionProperty().addListener(geometryListener);
        sceneProperty().addListener(geometryListener);
        widthProperty().addListener(geometryListener);
        heightProperty().addListener(geometryListener);
        Themes.windowTransparentProperty().addListener(themeListener);
        Themes.colorSchemeProperty().addListener(themeListener);
    }

    /// 返回本过渡带的方向。
    ///
    /// @return 过渡带方向
    public @NotNull Edge getEdge() {
        return edge;
    }

    /// 设置过渡带需要显示的滚动距离。
    ///
    /// 距离按 [DISTANCE_STEP] 向上量化成可见行数：0 表示完全隐藏，1 px 显示 1 行，
    /// 依此类推，最多显示整条过渡带。行数变化且已有图像时只调整图像视图的缩放与位置，
    /// 不重建图像；仅当还没有可用图像时才通过 [update()] 重建。传入负值、`NaN` 或
    /// 无穷值时按 0 处理。
    ///
    /// @param distance 顶部过渡带为已滚过的距离，底部过渡带为剩余可滚距离
    public void setDistance(double distance) {
        double sanitized = Double.isFinite(distance) ? Math.max(0.0, distance) : 0.0;
        requestedDistance = sanitized;

        int bandHeight = (int) Math.round(Math.min(FADE_HEIGHT, Math.max(0.0, getHeight())));
        int rows = quantizeRows(bandHeight, sanitized);
        if (rows == appliedRows) {
            return;
        }
        appliedRows = rows;
        if (rows > 0 && imageView.getImage() != null) {
            // 图像已是整条完整 ramp，滚动时只缩放可见高度，不重新渲染。
            applyRows();
            debug("scroll rows=" + rows + " band=" + bandHeight);
        } else {
            update();
        }
    }

    /// 按当前可见行数缩放并定位整条带图像，不重建像素。
    ///
    /// 图像始终是完整的 [FADE_HEIGHT] 行 ramp；[ImageView#setFitHeight(double)] 把整条
    /// ramp 压缩到已应用的 `appliedRows` 行，因此可见的渐隐距离跟随滚动距离，而不是
    /// 固定 72 px。
    private void applyRows() {
        double width = getWidth();
        double height = getHeight();
        int bandHeight = (int) Math.round(Math.min(FADE_HEIGHT, Math.max(0.0, height)));
        double rows = Math.min((double) bandHeight, Math.max(0.0, appliedRows));
        boolean visible = rows > 0.0 && imageView.getImage() != null;
        imageView.setFitWidth(Math.max(0.0, width));
        imageView.setFitHeight(rows);
        imageView.relocate(0.0, edge == Edge.TOP ? 0.0 : Math.max(0.0, height - rows));
        imageView.setVisible(visible);
        setVisible(visible);
        requestLayout();
    }

    /// 释放监听器与图像资源。
    ///
    /// 释放后再次调用不会产生副作用，本节点也不应再被复用。
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        GlassBackdrop.get().liveRevisionProperty().removeListener(geometryListener);
        sceneProperty().removeListener(geometryListener);
        widthProperty().removeListener(geometryListener);
        heightProperty().removeListener(geometryListener);
        Themes.windowTransparentProperty().removeListener(themeListener);
        Themes.colorSchemeProperty().removeListener(themeListener);
        imageView.setImage(null);
        setVisible(false);
    }

    /// 手工把图像视图放在过渡带靠边的一侧，并拉伸到当前宽度。
    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double rows = Math.max(0.0, imageView.getFitHeight());
        double y = edge == Edge.TOP ? 0.0 : Math.max(0.0, getHeight() - rows);
        imageView.relocate(0.0, y);
        imageView.setFitWidth(Math.max(0.0, width));
    }

    /// 按当前节点几何、页面背景与主题重建或隐藏整条渐隐带。
    ///
    /// 只在几何/壁纸/主题变化或还没有可用图像时调用；滚动本身只通过 [applyRows()]
    /// 更新可见行数，不会重建像素。
    private void update() {
        if (disposed) {
            return;
        }

        double width = getWidth();
        double height = getHeight();
        int bandHeight = (int) Math.round(Math.min(FADE_HEIGHT, Math.max(0.0, height)));
        int rows = quantizeRows(bandHeight, requestedDistance);
        appliedRows = rows;
        if (width < 1.0 || bandHeight < 1 || rows <= 0) {
            hide("no-distance");
            return;
        }

        Scene scene = getScene();
        if (scene == null) {
            hide("no-scene");
            return;
        }

        @Nullable GlassBackdrop.LiveSource source = GlassBackdrop.get().liveSource();
        if (source == null) {
            hide("no-live-source");
            return;
        }

        Node backgroundNode = source.backgroundNode();
        if (backgroundNode.getScene() != scene) {
            hide("background-scene-mismatch");
            return;
        }

        Point2D bandMin = localToScene(0.0, 0.0);
        Point2D bandMax = localToScene(width, height);
        if (bandMin == null || bandMax == null) {
            hide("no-band-transform");
            return;
        }
        double bandSceneWidth = bandMax.getX() - bandMin.getX();
        double bandSceneHeight = bandMax.getY() - bandMin.getY();

        Bounds nodeBounds = backgroundNode.getLayoutBounds();
        Point2D nodeMin = backgroundNode.localToScene(nodeBounds.getMinX(), nodeBounds.getMinY());
        Point2D nodeMax = backgroundNode.localToScene(nodeBounds.getMaxX(), nodeBounds.getMaxY());
        if (nodeMin == null || nodeMax == null) {
            hide("no-background-transform");
            return;
        }
        double nodeWidth = nodeMax.getX() - nodeMin.getX();
        double nodeHeight = nodeMax.getY() - nodeMin.getY();
        if (nodeWidth < 1.0 || nodeHeight < 1.0) {
            hide("background-size");
            return;
        }

        Image image = source.image();
        @Nullable GlassLiveViewport.Mapping mapping = GlassLiveViewport.map(
                bandMin.getX(), bandMin.getY(), bandSceneWidth, bandSceneHeight,
                nodeMin.getX(), nodeMin.getY(), nodeWidth, nodeHeight,
                image.getWidth(), image.getHeight(), 0.0);
        if (mapping == null) {
            hide("no-mapping");
            return;
        }
        if (!matchesBand(mapping, bandMin, bandSceneWidth, bandSceneHeight)) {
            hide("mapping-cropped");
            return;
        }

        // 非透明窗口里页面背景是 SURFACE_CONTAINER 的纯色；透明窗口无法复现页面真实
        // 背景，surfaceColor 传 null，渲染返回 null 并隐藏过渡带。
        @Nullable Color surfaceColor = Themes.windowTransparentProperty().get()
                ? null
                : Themes.getColorScheme().getColor(ColorRole.SURFACE_CONTAINER);

        int pixelWidth = (int) Math.round(width);
        @Nullable WritableImage rendered = GlassEdgeFadeImage.render(image, mapping.imageViewport(), source.opacity(),
                surfaceColor, pixelWidth, bandHeight, edge == Edge.TOP);
        if (rendered == null) {
            hide("background-not-reproducible");
            return;
        }

        // 整条带只渲染一次；可见高度由 applyRows() 通过 fitHeight 缩放。
        imageView.setImage(rendered);
        applyRows();
        debug("rendered " + pixelWidth + "x" + bandHeight + " rows=" + rows
                + " distance=" + requestedDistance);
    }

    /// 隐藏渐隐带并记录原因。
    ///
    /// @param reason 隐藏原因，仅在调试模式下输出
    private void hide(@NotNull String reason) {
        if (imageView.getImage() != null) {
            imageView.setImage(null);
        }
        imageView.setVisible(false);
        setVisible(false);
        requestLayout();
        debug("hidden " + reason);
    }

    /// 判断映射返回的场景矩形是否与过渡带节点矩形一致，不一致说明采样带被背景节点裁剪。
    ///
    /// @param mapping 背景映射
    /// @param bandMin 过渡带左上角场景坐标
    /// @param bandWidth 过渡带场景宽度
    /// @param bandHeight 过渡带场景高度
    /// @return 未被裁剪时返回 `true`
    private static boolean matchesBand(
            @NotNull GlassLiveViewport.Mapping mapping,
            @NotNull Point2D bandMin,
            double bandWidth,
            double bandHeight) {
        return Math.abs(mapping.sceneMinX() - bandMin.getX()) <= GEOMETRY_TOLERANCE
                && Math.abs(mapping.sceneMinY() - bandMin.getY()) <= GEOMETRY_TOLERANCE
                && Math.abs(mapping.sceneWidth() - bandWidth) <= GEOMETRY_TOLERANCE
                && Math.abs(mapping.sceneHeight() - bandHeight) <= GEOMETRY_TOLERANCE;
    }

    /// 输出去重后的调试日志。
    ///
    /// @param message 状态描述
    private void debug(@NotNull String message) {
        if (!DEBUG) {
            return;
        }
        String state = edge + " " + message;
        if (state.equals(lastDebugState)) {
            return;
        }
        lastDebugState = state;
        LOG.info("[glass-edge] " + state);
    }

    /// 计算滚动距离。
    ///
    /// 结果等于 `clamp(vvalue, 0, 1) * max(0, contentHeight - viewportHeight)`：
    /// `vvalue` 为 0 时返回 0，为 1 时返回可滚动高度；内容不足一屏、参数非有限值
    /// 时返回 0，负尺寸按 0 处理。
    ///
    /// @param vvalue 滚动条位置，正常范围 `[0, 1]`
    /// @param contentHeight 滚动内容高度（像素）
    /// @param viewportHeight 滚动视口高度（像素）
    /// @return 已滚动的像素距离，非负
    public static double scrollDistance(double vvalue, double contentHeight, double viewportHeight) {
        if (!Double.isFinite(vvalue) || !Double.isFinite(contentHeight) || !Double.isFinite(viewportHeight)) {
            return 0.0;
        }
        double value = Math.max(0.0, Math.min(1.0, vvalue));
        double content = Math.max(0.0, contentHeight);
        double viewport = Math.max(0.0, viewportHeight);
        return value * Math.max(0.0, content - viewport);
    }

    /// 把滚动距离量化为过渡带可见的行数。
    ///
    /// 行数按 [DISTANCE_STEP]（1 px）向上取整，并裁剪到 `[0, bandHeight]`；距离小于等于
    /// 0、非有限值或过渡带高度非正时返回 0。
    ///
    /// @param bandHeight 过渡带高度（像素行）
    /// @param distance 滚动距离（像素）
    /// @return 需要渲染的行数，范围 `[0, bandHeight]`
    static int quantizeRows(int bandHeight, double distance) {
        if (bandHeight <= 0 || !Double.isFinite(distance) || distance <= 0.0) {
            return 0;
        }
        double quantized = Math.ceil(distance / DISTANCE_STEP) * DISTANCE_STEP;
        if (quantized >= bandHeight) {
            return bandHeight;
        }
        return (int) quantized;
    }
}
