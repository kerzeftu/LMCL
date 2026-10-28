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
 * Modified by LMCL team on 2026-10-29: multi-layer frosted material snapshot.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/// 一张可供 M3 磨砂表面采样的多尺度壁纸快照。
///
/// 所有层都从同一张壁纸生成，只是高斯模糊半径不同：第 0 层半径最大，用于
/// 环境色与大面积明暗；最后一层半径最小，用于保留卡片尺寸较小时的局部细节。
/// 各层共用同一套像素比例与场景原点，[GlassSurface] 只需要按自身位置裁出
/// 相同视口。
///
/// @param layers  模糊图层，至少 1 张，所有图层尺寸必须一致
/// @param scale   快照像素 / 场景像素
/// @param originX 快照左上角对应的场景 X 坐标
/// @param originY 快照左上角对应的场景 Y 坐标
/// @param padding 快照四周额外渲染的像素数
@NotNullByDefault
public record GlassSnapshot(
        @NotNull List<Image> layers,
        double scale,
        double originX,
        double originY,
        int padding) {

    /// 校验快照几何与所有图层的尺寸。
    public GlassSnapshot {
        Objects.requireNonNull(layers, "layers");
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("layers must not be empty");
        }
        List<Image> copy = List.copyOf(layers);
        Image first = copy.get(0);
        for (Image layer : copy) {
            if (layer.getWidth() != first.getWidth() || layer.getHeight() != first.getHeight()) {
                throw new IllegalArgumentException("all layers must have the same size");
            }
        }
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("scale must be positive: " + scale);
        }
        if (!Double.isFinite(originX) || !Double.isFinite(originY)) {
            throw new IllegalArgumentException("origin must be finite");
        }
        if (padding < 0) {
            throw new IllegalArgumentException("padding must be non-negative: " + padding);
        }
        layers = copy;
    }

    /// 图层数量。
    ///
    /// @return 图层数量
    public int layerCount() {
        return layers.size();
    }

    /// 返回指定图层。
    ///
    /// @param index 图层序号
    /// @return 图层
    public @NotNull Image layer(int index) {
        return layers.get(index);
    }

    /// 快照四周额外渲染的场景像素数。
    ///
    /// @return 场景像素数
    public double scenePadding() {
        return padding / scale;
    }

    /// 计算一个场景矩形在当前快照中的视口。
    ///
    /// 视口会被钳制到快照内部；完全落在图片外或小于 1 像素时返回 `null`。
    ///
    /// @param sceneX 场景 X
    /// @param sceneY 场景 Y
    /// @param width  场景宽度
    /// @param height 场景高度
    /// @return 视口；无效时返回 `null`
    @Nullable
    public Rectangle2D viewport(double sceneX, double sceneY, double width, double height) {
        if (!Double.isFinite(sceneX) || !Double.isFinite(sceneY)
                || !Double.isFinite(width) || !Double.isFinite(height)
                || width <= 1.0 || height <= 1.0) {
            return null;
        }

        Image first = layers.get(0);
        double imageWidth = first.getWidth();
        double imageHeight = first.getHeight();
        double x = (sceneX - originX) * scale;
        double y = (sceneY - originY) * scale;
        double viewportWidth = width * scale;
        double viewportHeight = height * scale;

        // 完全落在快照外的表面没有可采样的像素；部分越界则钳制到边缘，
        // 避免滚动/窗口缩放时整块表面变回不透明兜底。
        if (x + viewportWidth <= 0.0 || y + viewportHeight <= 0.0
                || x >= imageWidth || y >= imageHeight) {
            return null;
        }

        double clampedX = clamp(x, 0.0, Math.max(0.0, imageWidth - 1.0));
        double clampedY = clamp(y, 0.0, Math.max(0.0, imageHeight - 1.0));
        double clampedWidth = Math.min(viewportWidth, imageWidth - clampedX);
        double clampedHeight = Math.min(viewportHeight, imageHeight - clampedY);
        if (clampedWidth <= 1.0 || clampedHeight <= 1.0) {
            return null;
        }
        return new Rectangle2D(clampedX, clampedY, clampedWidth, clampedHeight);
    }

    /// 计算场景原点对应的快照像素原点。
    ///
    /// @return 像素坐标
    @NotNull
    public Point2D imageOrigin() {
        return new Point2D(originX * scale, originY * scale);
    }

    /// 限制数值范围。
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
