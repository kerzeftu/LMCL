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
 * Modified by LMCL team on 2026-11-01: live wallpaper-sampling frosted material.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.BoxBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Transform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 一块实时采样壁纸的 M3 磨砂表面。
///
/// 主路径由“五层不同半径的模糊 [ImageView] + 半透明表面色 + 圆角裁剪”组成。
/// 每层 [ImageView] 直接显示启动器壁纸图片 [Image]，视口按
/// `lmcl-launcher-background` 背景节点的场景几何实时换算，因此卡片滚动、窗口
/// 缩放、界面缩放与换壁纸时都会在下一次布局里立即跟手，不需要逐帧
/// [javafx.scene.Node#snapshot] 或 16 ms 快照重建。
///
/// 静态快照 [GlassSnapshot] 只作为回退：壁纸不是可采样的 `cover` 图片、背景
/// 节点暂时不存在、或者表面在场景外时使用；两者都不可用时显示统一的半透明
/// 表面色兜底。玻璃被关闭时 [GlassBackdrop] 不提供任何源，本节点同样进入兜底
/// 路径，由现有 CSS 统一隐藏。
///
/// 五层采样视口都会比卡片本身向四周外扩 [GlassLiveViewport#sampleMargin]
/// （远层 192 px 半径对应 290 px 场景外扩），保证卡片可见区域内部永远不是
/// [BoxBlur] 的透明边缘；外扩部分由本节点的圆角 [Rectangle] clip 裁掉。
/// 壁纸节点的 [org.jackhuang.hmcl.theme.LauncherBackground#opacity()] 是独立于
/// 图片透明度的节点透明度，实时层把它乘在图层组上，避免半透明壁纸下磨砂偏亮。
///
/// 1×1 小卡片会额外加 `lmcl-m3-glass-compact`，由 CSS 降低远景层、提高近景层，
/// 避免小面积被大半径模糊抹成一块纯色。
///
/// 每层只做高斯模糊，不串联任何附加效果；整块表面之上再覆盖一层半透明
/// 表面色 tint，只靠“五层模糊图层 + 半透明 tint”构成材质。
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

    /// 图层不可用原因，写入诊断日志。
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
    ///
    /// 实时模式下它们是壁纸图片的采样视图；快照回退模式下是静态快照的切片。
    private final ImageView[] layerViews;

    /// 每层只创建一次的模糊效果，实时模式下按局部缩放更新半径。
    private final BoxBlur[] layerBlurs;

    /// 实时图层组：不参与布局，单独设置整组透明度以叠加壁纸节点透明度。
    ///
    /// 使用 [Group] 而不是直接放在 [StackPane] 里，是为了让 CSS 设置在每层
    /// [ImageView] 上的 `-fx-opacity` 保持独立，同时能在不改变每层透明度的
    /// 前提下，把 [org.jackhuang.hmcl.theme.LauncherBackground#opacity()] 乘到
    /// 整组上。
    private final Group liveGroup = new Group();

    /// 没有图层时的统一半透明表面色。
    private final Region fallback = new Region();

    /// 半透明表面色。
    private final Region tint = new Region();

    /// 圆角裁剪。
    private final Rectangle clip = new Rectangle();

    /// 快照变化监听。
    private final InvalidationListener snapshotListener = observable -> update();

    /// 对单例属性的弱监听，避免侧栏重建后旧表面无法回收。
    private final InvalidationListener weakSnapshotListener = new WeakInvalidationListener(snapshotListener);

    /// 实时源变化监听。
    private final InvalidationListener liveListener = observable -> update();

    /// 对单例属性的弱监听，避免侧栏重建后旧表面无法回收。
    private final InvalidationListener weakLiveListener = new WeakInvalidationListener(liveListener);

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

    /// 是否已把弱监听注册到全局背景单例；离开场景时注销，避免全局属性
    /// 长期持有已废弃表面的监听包装。
    private boolean backdropListenersRegistered;

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
        layerBlurs = new BoxBlur[maxLayers()];
        for (int i = 0; i < layerViews.length; i++) {
            ImageView view = new ImageView();
            view.getStyleClass().addAll("lmcl-m3-glass-layer", "lmcl-m3-glass-layer-" + i);
            view.setPreserveRatio(false);
            view.setSmooth(true);
            view.setMouseTransparent(true);
            view.setManaged(false);
            view.setVisible(false);
            layerViews[i] = view;

            // 先按 1:1 屏幕半径创建；实际布局时再按局部缩放更新。
            double radius = GlassBackdrop.blurSceneRadiusAt(i);
            layerBlurs[i] = new BoxBlur(radius, radius, GlassBackdrop.iterationsAt(i));
        }
        liveGroup.getChildren().addAll(layerViews);
        liveGroup.setManaged(false);
        liveGroup.setMouseTransparent(true);

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
        getChildren().add(liveGroup);
        getChildren().add(tint);
        setVisible(false);

        widthProperty().addListener(geometryListener);
        heightProperty().addListener(geometryListener);
        sceneProperty().addListener(geometryListener);
        localToSceneTransformProperty().addListener(geometryListener);
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

    /// 只在表面上屏期间注册全局背景监听，离屏时注销。
    ///
    /// 快照与实时版本都来自 [GlassBackdrop] 单例；若一直持有监听，导航后废弃的
    /// 卡片会在全局属性上留下永远不会触发但也不会自动移除的包装。这里跟随场景
    /// 生命周期注册/注销，重新上屏时 [update] 会读取最新状态。
    private void updateBackdropListeners() {
        boolean inScene = getScene() != null;
        if (inScene == backdropListenersRegistered) {
            return;
        }
        backdropListenersRegistered = inScene;
        if (inScene) {
            GlassBackdrop.get().snapshotProperty().addListener(weakSnapshotListener);
            GlassBackdrop.get().liveRevisionProperty().addListener(weakLiveListener);
        } else {
            GlassBackdrop.get().snapshotProperty().removeListener(weakSnapshotListener);
            GlassBackdrop.get().liveRevisionProperty().removeListener(weakLiveListener);
        }
    }

    /// 根据当前实时源、快照与场景位置刷新图层。
    private void update() {
        updateBackdropListeners();

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

        Point2D origin = localToScene(0.0, 0.0);
        if (origin == null) {
            showFallback(FallbackReason.NO_TRANSFORM);
            return;
        }

        if (showLive(origin, width, height)) {
            return;
        }

        GlassSnapshot snapshot = GlassBackdrop.get().snapshotProperty().get();
        if (snapshot == null) {
            showFallback(FallbackReason.NO_SNAPSHOT);
            return;
        }

        if (showSnapshot(snapshot, origin, width, height)) {
            return;
        }

        showFallback(FallbackReason.OUT_OF_BOUNDS);
    }

    /// 用壁纸图片实时填充五层采样视图。
    ///
    /// @return 是否成功切换到实时模式
    private boolean showLive(@NotNull Point2D origin, double width, double height) {
        GlassBackdrop.LiveSource source = GlassBackdrop.get().liveSource();
        if (source == null) {
            return false;
        }

        Node backgroundNode = source.backgroundNode();
        if (backgroundNode.getScene() != getScene()) {
            return false;
        }

        Bounds nodeBounds = backgroundNode.getLayoutBounds();
        Point2D nodeOrigin = backgroundNode.localToScene(nodeBounds.getMinX(), nodeBounds.getMinY());
        Point2D nodeMax = backgroundNode.localToScene(nodeBounds.getMaxX(), nodeBounds.getMaxY());
        if (nodeOrigin == null || nodeMax == null) {
            return false;
        }
        // 背景节点的 localToScene 已包含 UI 缩放等变换，这里统一用场景尺寸参与
        // 映射；把卡片/节点局部尺寸直接当作场景尺寸会在非 1.0 缩放下错位。
        double nodeWidth = nodeMax.getX() - nodeOrigin.getX();
        double nodeHeight = nodeMax.getY() - nodeOrigin.getY();
        if (nodeWidth < 1.0 || nodeHeight < 1.0) {
            return false;
        }

        Point2D cardMax = localToScene(width, height);
        if (cardMax == null) {
            return false;
        }
        double cardSceneWidth = cardMax.getX() - origin.getX();
        double cardSceneHeight = cardMax.getY() - origin.getY();
        if (!(cardSceneWidth > 0.5) || !(cardSceneHeight > 0.5)) {
            return false;
        }

        Image image = source.image();
        Transform transform = getLocalToSceneTransform();
        double localScale = Math.hypot(transform.getMxx(), transform.getMyx());

        int visibleLayers = 0;
        for (int i = 0; i < layerViews.length; i++) {
            ImageView view = layerViews[i];
            double radius = GlassBackdrop.blurSceneRadiusAt(i);
            GlassLiveViewport.Mapping mapping = GlassLiveViewport.map(
                    origin.getX(), origin.getY(), cardSceneWidth, cardSceneHeight,
                    nodeOrigin.getX(), nodeOrigin.getY(), nodeWidth, nodeHeight,
                    image.getWidth(), image.getHeight(),
                    GlassLiveViewport.sampleMargin(radius));
            if (mapping == null) {
                view.setVisible(false);
                continue;
            }

            // 场景采样矩形换算到卡片局部坐标；缩放/DPI 由这次变换统一吸收。
            Point2D sampleMin = sceneToLocal(mapping.sceneMinX(), mapping.sceneMinY());
            Point2D sampleMax = sceneToLocal(
                    mapping.sceneMinX() + mapping.sceneWidth(),
                    mapping.sceneMinY() + mapping.sceneHeight());
            if (sampleMin == null || sampleMax == null) {
                view.setVisible(false);
                continue;
            }
            double fitWidth = sampleMax.getX() - sampleMin.getX();
            double fitHeight = sampleMax.getY() - sampleMin.getY();
            if (fitWidth <= 0.5 || fitHeight <= 0.5) {
                view.setVisible(false);
                continue;
            }

            BoxBlur blur = layerBlurs[i];
            double localRadius = GlassLiveViewport.localBlurRadius(radius, localScale);
            blur.setWidth(localRadius);
            blur.setHeight(localRadius);

            view.setImage(image);
            view.setViewport(mapping.imageViewport());
            view.setEffect(blur);
            view.setLayoutX(sampleMin.getX());
            view.setLayoutY(sampleMin.getY());
            view.setFitWidth(fitWidth);
            view.setFitHeight(fitHeight);
            view.setVisible(true);
            visibleLayers++;
        }

        if (visibleLayers == 0) {
            return false;
        }

        liveGroup.setOpacity(source.opacity());
        fallback.setVisible(false);
        tint.setVisible(true);
        setVisible(true);
        fallbackReason = FallbackReason.NONE;
        debug("live: " + width + "x" + height
                + " layers=" + visibleLayers
                + " node=" + nodeWidth + "x" + nodeHeight
                + " opacity=" + source.opacity());
        return true;
    }

    /// 用静态快照回退填充图层。
    ///
    /// @return 是否成功
    private boolean showSnapshot(
            @NotNull GlassSnapshot snapshot,
            @NotNull Point2D origin,
            double width,
            double height) {
        Point2D cardMax = localToScene(width, height);
        if (cardMax == null) {
            return false;
        }
        // GlassSnapshot 的记录坐标是场景坐标，必须传场景尺寸而不是局部布局尺寸，
        // 否则 UI 缩放不为 1 时回退图层会错位/缩小。
        double sceneWidth = cardMax.getX() - origin.getX();
        double sceneHeight = cardMax.getY() - origin.getY();
        Rectangle2D viewport = snapshot.viewport(origin.getX(), origin.getY(), sceneWidth, sceneHeight);
        if (viewport == null) {
            return false;
        }

        List<Image> images = snapshot.layers();
        for (int i = 0; i < layerViews.length; i++) {
            ImageView view = layerViews[i];
            if (i < images.size()) {
                view.setImage(images.get(i));
                view.setViewport(viewport);
                // 快照图层已包含模糊，不再叠加任何附加效果。
                view.setEffect(null);
                view.setLayoutX(0.0);
                view.setLayoutY(0.0);
                view.setFitWidth(width);
                view.setFitHeight(height);
                view.setVisible(true);
            } else {
                view.setImage(null);
                view.setVisible(false);
            }
        }
        liveGroup.setOpacity(1.0);
        fallback.setVisible(false);
        tint.setVisible(true);
        setVisible(true);
        fallbackReason = FallbackReason.NONE;
        debug("snapshot: " + width + "x" + height
                + " viewport=" + viewport
                + " layers=" + images.size());
        return true;
    }

    /// 显示统一兜底表面。
    private void showFallback(@NotNull FallbackReason reason) {
        for (ImageView view : layerViews) {
            view.setVisible(false);
        }
        liveGroup.setOpacity(1.0);
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
