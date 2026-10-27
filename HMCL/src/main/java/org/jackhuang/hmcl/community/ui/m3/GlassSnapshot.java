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
 * Modified by LMCL team on 2026-10-28: two-layer frosted material snapshot.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// 一张可供 M3 磨砂表面采样的双层壁纸快照。
///
/// 两层图来自同一张壁纸：`far` 用大半径高斯模糊铺底，`near` 用小半径模糊
/// 补充细节。两层共用同一套像素比例与场景原点，[GlassSurface] 只需要按自身
/// 位置裁出相同视口。
///
/// @param far     远景模糊图
/// @param near    近景模糊图
/// @param scale   快照像素 / 场景像素
/// @param originX 快照左上角对应的场景 X 坐标
/// @param originY 快照左上角对应的场景 Y 坐标
/// @param padding 快照四周额外渲染的像素数
@NotNullByDefault
public record GlassSnapshot(
        @NotNull Image far,
        @NotNull Image near,
        double scale,
        double originX,
        double originY,
        int padding) {

    /// 校验快照几何与两张图的尺寸。
    public GlassSnapshot {
        Objects.requireNonNull(far, "far");
        Objects.requireNonNull(near, "near");
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("scale must be positive: " + scale);
        }
        if (!Double.isFinite(originX) || !Double.isFinite(originY)) {
            throw new IllegalArgumentException("origin must be finite");
        }
        if (padding < 0) {
            throw new IllegalArgumentException("padding must be non-negative: " + padding);
        }
        if (far.getWidth() != near.getWidth() || far.getHeight() != near.getHeight()) {
            throw new IllegalArgumentException("far and near images must have the same size");
        }
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

        double imageWidth = far.getWidth();
        double imageHeight = far.getHeight();
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
