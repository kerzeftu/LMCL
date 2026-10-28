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
 * Modified by LMCL team on 2026-10-29: multi-layer frosted material surface.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 一块采样多层壁纸快照的 M3 磨砂表面。
///
/// 结构为“五层不同半径的模糊图 + 半透明表面色 + 圆角裁剪”。它只读取
/// [GlassBackdrop] 的快照，不截取下层真实 UI，因此不会形成递归渲染。没有快照
/// 时显示统一的半透明表面色兜底，不再整块隐藏后露出另一层不透明卡片。
///
/// 1×1 小卡片会额外加 `lmcl-m3-glass-compact`，由 CSS 降低远景层、提高近景层，
/// 避免小面积被大半径模糊抹成一块纯色。
///
/// 本节点不处理鼠标事件，必须作为内容层的背景使用。线程：构造与属性监听都在
/// JavaFX 应用线程。
@NotNullByDefault
public final class GlassSurface extends StackPane {

    /// 表面用途；只影响诊断与 CSS 变量。
    public enum Kind {
        /// 主页卡片。
        CARD,
        /// 全局侧栏。
        SIDEBAR
    }

    /// 快照不可用原因，写入诊断日志。
    enum FallbackReason {
        NONE,
        HIDDEN,
        NO_SCENE,
        NO_SNAPSHOT,
        NO_TRANSFORM,
        OUT_OF_BOUNDS
    }

    /// 1×1 卡片的紧凑阈值：宽度或高度低于该值时改用近景权重。
    static final double COMPACT_WIDTH = 280.0;
    static final double COMPACT_HEIGHT = 150.0;

    /// 模糊图层视图，顺序与 [GlassSnapshot#layers()] 一致。
    private final ImageView[] layerViews;

    /// 没有快照时的统一半透明表面色。
    private final Region fallback = new Region();

    /// 半透明表面色。
    private final Region tint = new Region();

    /// 圆角裁剪。
    private final Rectangle clip = new Rectangle();

    /// 快照变化监听。
    private final InvalidationListener snapshotListener = observable -> update();

    /// 对单例属性的弱监听，避免侧栏重建后旧表面无法回收。
    private final InvalidationListener weakSnapshotListener = new WeakInvalidationListener(snapshotListener);

    /// 自身尺寸/场景/变换变化监听。
    private final InvalidationListener geometryListener = observable -> update();

    /// 是否输出玻璃诊断日志。
    private static final boolean DEBUG = Boolean.getBoolean("lmcl.visual.debug");

    /// 诊断名称。
    private @Nullable String debugName;

    /// 表面用途。
    private final Kind kind;

    /// 最近一次诊断状态，避免尺寸/位置未变时重复刷日志。
    private @Nullable String lastDebugState;

    /// 当前兜底原因。
    private FallbackReason fallbackReason = FallbackReason.HIDDEN;

    /// 创建一个直角卡片表面。
    public GlassSurface() {
        this(0.0, Kind.CARD);
    }

    /// 创建一个带圆角的卡片表面。
    ///
    /// @param arc 圆角直径（像素）
    public GlassSurface(double arc) {
        this(arc, Kind.CARD);
    }

    /// 创建一个带圆角的表面。
    ///
    /// @param arc  圆角直径（像素）
    /// @param kind 表面用途
    public GlassSurface(double arc, @NotNull Kind kind) {
        this.kind = kind;
        getStyleClass().add("lmcl-m3-glass-surface");
        if (kind == Kind.SIDEBAR) {
            getStyleClass().add("lmcl-m3-glass-sidebar");
        }
        setMouseTransparent(true);
        setPickOnBounds(false);
        setMinSize(0.0, 0.0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        layerViews = new ImageView[maxLayers()];
        for (int i = 0; i < layerViews.length; i++) {
            ImageView view = new ImageView();
            view.getStyleClass().addAll("lmcl-m3-glass-layer", "lmcl-m3-glass-layer-" + i);
            view.setPreserveRatio(false);
            view.setSmooth(true);
            view.setMouseTransparent(true);
            view.setVisible(false);
            layerViews[i] = view;
        }

        fallback.getStyleClass().add("lmcl-m3-glass-fallback");
        fallback.setMouseTransparent(true);
        fallback.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        fallback.setVisible(false);

        tint.getStyleClass().add("lmcl-m3-glass-tint");
        tint.setMouseTransparent(true);
        tint.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        tint.setVisible(false);

        clip.setArcWidth(Math.max(0.0, arc));
        clip.setArcHeight(Math.max(0.0, arc));
        setClip(clip);

        getChildren().add(fallback);
        getChildren().addAll(layerViews);
        getChildren().add(tint);
        setVisible(false);

        widthProperty().addListener(geometryListener);
        heightProperty().addListener(geometryListener);
        sceneProperty().addListener(geometryListener);
        localToSceneTransformProperty().addListener(geometryListener);
        GlassBackdrop.get().snapshotProperty().addListener(weakSnapshotListener);
        update();
    }

    /// 模糊图层数量。
    ///
    /// @return 图层数量
    static int maxLayers() {
        return GlassBackdrop.layerCount();
    }

    /// 是否为 1×1 小卡片尺寸。
    ///
    /// @param width  宽度
    /// @param height 高度
    /// @return 是否使用紧凑层权重
    static boolean isCompact(double width, double height) {
        return width < COMPACT_WIDTH || height < COMPACT_HEIGHT;
    }

    /// 设置诊断名称；`null` 表示使用用途名。
    ///
    /// @param name 诊断名称
    public void setDebugName(@Nullable String name) {
        this.debugName = name;
        update();
    }

    /// 根据当前快照与场景位置刷新多层切片。
    private void update() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 1.0 || height <= 1.0) {
            setVisible(false);
            fallbackReason = FallbackReason.HIDDEN;
            debug("hidden: size " + width + "x" + height);
            return;
        }

        clip.setWidth(width);
        clip.setHeight(height);
        refreshCompactClass(width, height);

        if (getScene() == null) {
            showFallback(FallbackReason.NO_SCENE);
            return;
        }

        GlassSnapshot snapshot = GlassBackdrop.get().snapshotProperty().get();
        if (snapshot == null) {
            showFallback(FallbackReason.NO_SNAPSHOT);
            return;
        }

        Point2D origin = localToScene(0.0, 0.0);
        if (origin == null) {
            showFallback(FallbackReason.NO_TRANSFORM);
            return;
        }

        Rectangle2D viewport = snapshot.viewport(origin.getX(), origin.getY(), width, height);
        if (viewport == null) {
            showFallback(FallbackReason.OUT_OF_BOUNDS);
            return;
        }

        List<Image> images = snapshot.layers();
        for (int i = 0; i < layerViews.length; i++) {
            ImageView view = layerViews[i];
            if (i < images.size()) {
                view.setImage(images.get(i));
                view.setViewport(viewport);
                view.setFitWidth(width);
                view.setFitHeight(height);
                view.setVisible(true);
            } else {
                view.setImage(null);
                view.setVisible(false);
            }
        }
        fallback.setVisible(false);
        tint.setVisible(true);
        setVisible(true);
        fallbackReason = FallbackReason.NONE;
        debug("shown: " + width + "x" + height
                + " viewport=" + viewport
                + " layers=" + images.size());
    }

    /// 显示统一兜底表面。
    private void showFallback(@NotNull FallbackReason reason) {
        for (ImageView view : layerViews) {
            view.setVisible(false);
        }
        fallback.setVisible(true);
        tint.setVisible(true);
        setVisible(true);
        fallbackReason = reason;
        debug("fallback: " + reason);
    }

    /// 按尺寸给 1×1 卡片加/去紧凑类；侧栏不参与。
    private void refreshCompactClass(double width, double height) {
        boolean compact = kind == Kind.CARD && isCompact(width, height);
        if (compact) {
            if (!getStyleClass().contains("lmcl-m3-glass-compact")) {
                getStyleClass().add("lmcl-m3-glass-compact");
            }
        } else {
            getStyleClass().remove("lmcl-m3-glass-compact");
        }
    }

    /// 输出诊断信息；仅在 `-Dlmcl.visual.debug=true` 时生效。
    private void debug(@NotNull String message) {
        if (!DEBUG) {
            return;
        }
        String state = label() + "|" + (int) getWidth() + "x" + (int) getHeight()
                + "|" + fallbackReason + "|" + message;
        if (state.equals(lastDebugState)) {
            return;
        }
        lastDebugState = state;
        LOG.info("[glass-material] " + label() + " " + message
                + " compact=" + getStyleClass().contains("lmcl-m3-glass-compact")
                + " fallback=" + fallbackReason);
    }

    /// 返回诊断标签。
    @NotNull
    private String label() {
        String name = debugName;
        return name != null ? name : kind.name().toLowerCase();
    }
}
