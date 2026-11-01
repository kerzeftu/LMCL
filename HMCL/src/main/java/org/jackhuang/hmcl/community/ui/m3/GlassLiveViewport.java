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
 * Modified by LMCL team on 2026-11-01: live frosted material viewport mapping.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.geometry.Rectangle2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// 实时磨砂的纯几何映射工具。
///
/// 启动器壁纸以 `cover`、居中、不重复的方式绘制在 `lmcl-launcher-background`
/// 节点中。本类只做三件与 JavaFX 节点无关的计算：把卡片场景矩形向四周外扩
/// 采样边（避免 [javafx.scene.effect.BoxBlur] 在卡片边缘采到透明像素）、裁到
/// 背景节点范围内、再按 `cover` 比例换算成壁纸图片像素视口。
///
/// 全程使用场景逻辑像素与图片像素，不读取任何节点状态，因此可以在无图形环境
/// 下单元测试；DPI/界面缩放由调用方在场景坐标与局部坐标之间转换，模糊半径则
/// 通过 [localBlurRadius] 从屏幕像素换算到卡片局部像素。
@NotNullByDefault
final class GlassLiveViewport {

    /// [javafx.scene.effect.BoxBlur] 的可见扩散约为模糊半径的 1.5 倍。
    private static final double SAMPLE_MARGIN_FACTOR = 1.5;

    /// 采样边在扩散量之外额外保留的余量（场景像素）。
    private static final double SAMPLE_MARGIN_BASE = 2.0;

    /// 最小可见采样尺寸（场景像素）；小于该值时视为没有可采样的壁纸。
    private static final double MIN_VISIBLE_SIZE = 0.5;

    /// 工具类，不允许实例化。
    private GlassLiveViewport() {
    }

    /// 一次实时采样映射的结果。
    ///
    /// [imageViewport] 是壁纸图片像素坐标中的视口；`scene*` 是实际要采样的
    /// 场景矩形（卡片矩形向外扩采样边后再裁到背景节点内），调用方需要用
    /// `sceneToLocal` 把它换算成卡片局部坐标。
    ///
    /// @param imageViewport 壁纸图片像素视口
    /// @param sceneMinX     采样矩形的场景 X
    /// @param sceneMinY     采样矩形的场景 Y
    /// @param sceneWidth    采样矩形的场景宽度
    /// @param sceneHeight   采样矩形的场景高度
    record Mapping(
            @NotNull Rectangle2D imageViewport,
            double sceneMinX,
            double sceneMinY,
            double sceneWidth,
            double sceneHeight) {

        /// 校验必有字段。
        Mapping {
            Objects.requireNonNull(imageViewport, "imageViewport");
        }
    }

    /// 返回一层模糊需要的采样外扩量（场景像素）。
    ///
    /// 远层半径 192 时返回 290，保证卡片可见区域内部永远不是 BoxBlur 的透明
    /// 边缘；外扩出去的部分由 [GlassSurface] 的圆角 clip 裁掉。
    ///
    /// @param sceneRadius 屏幕上的模糊半径
    /// @return 采样外扩量，至少 [SAMPLE_MARGIN_BASE]
    static double sampleMargin(double sceneRadius) {
        double radius = Double.isFinite(sceneRadius) ? Math.max(0.0, sceneRadius) : 0.0;
        return radius * SAMPLE_MARGIN_FACTOR + SAMPLE_MARGIN_BASE;
    }

    /// 把屏幕像素模糊半径换算到卡片局部坐标中的 BoxBlur 半径。
    ///
    /// 卡片可能位于缩放变换下（界面缩放/DPI 适配），JavaFX 的 effect 在节点
    /// 局部空间生效，因此需要除以局部到场景的缩放比例，保证屏幕上的模糊半径
    /// 仍是 192/128/80/48/24（v3.17.28 起）。
    ///
    /// @param sceneRadius        屏幕上的目标半径
    /// @param localToSceneScale  卡片局部到场景的缩放比例
    /// @return 局部半径，至少 1
    static double localBlurRadius(double sceneRadius, double localToSceneScale) {
        double scale = Double.isFinite(localToSceneScale) && localToSceneScale > 0.0
                ? localToSceneScale
                : 1.0;
        double radius = Double.isFinite(sceneRadius) ? Math.max(0.0, sceneRadius) : 0.0;
        return Math.max(1.0, radius / scale);
    }

    /// 把卡片场景矩形映射到壁纸图片像素视口。
    ///
    /// 卡片矩形先向四周外扩 [sampleMargin]，再与背景节点矩形求交；卡片完全
    /// 落在背景节点外时返回 `null`（调用方回退到静态快照或半透明表面色）。
    /// 背景节点内壁纸按 `cover` 居中绘制，因此映射时使用
    /// `scale = max(nodeWidth / imageWidth, nodeHeight / imageHeight)`。
    ///
    /// @param cardSceneX      卡片场景 X
    /// @param cardSceneY      卡片场景 Y
    /// @param cardSceneWidth  卡片场景宽度
    /// @param cardSceneHeight 卡片场景高度
    /// @param nodeSceneX      背景节点场景 X
    /// @param nodeSceneY      背景节点场景 Y
    /// @param nodeWidth       背景节点宽度
    /// @param nodeHeight      背景节点高度
    /// @param imageWidth      壁纸图片像素宽度
    /// @param imageHeight     壁纸图片像素高度
    /// @param margin          采样外扩量，见 [sampleMargin]
    /// @return 映射结果；几何无效、卡片完全离开背景节点或图片尺寸无效时返回 `null`
    @Nullable
    static Mapping map(
            double cardSceneX,
            double cardSceneY,
            double cardSceneWidth,
            double cardSceneHeight,
            double nodeSceneX,
            double nodeSceneY,
            double nodeWidth,
            double nodeHeight,
            double imageWidth,
            double imageHeight,
            double margin) {
        if (!areFinite(cardSceneX, cardSceneY, cardSceneWidth, cardSceneHeight,
                nodeSceneX, nodeSceneY, nodeWidth, nodeHeight,
                imageWidth, imageHeight, margin)) {
            return null;
        }
        if (cardSceneWidth <= MIN_VISIBLE_SIZE || cardSceneHeight <= MIN_VISIBLE_SIZE
                || nodeWidth < 1.0 || nodeHeight < 1.0
                || imageWidth < 1.0 || imageHeight < 1.0
                || margin < 0.0) {
            return null;
        }

        // 卡片必须与背景节点真正相交；完全移出背景节点时没有可采样的壁纸。
        double clipMinX = Math.max(cardSceneX, nodeSceneX);
        double clipMinY = Math.max(cardSceneY, nodeSceneY);
        double clipMaxX = Math.min(cardSceneX + cardSceneWidth, nodeSceneX + nodeWidth);
        double clipMaxY = Math.min(cardSceneY + cardSceneHeight, nodeSceneY + nodeHeight);
        if (clipMaxX - clipMinX <= MIN_VISIBLE_SIZE || clipMaxY - clipMinY <= MIN_VISIBLE_SIZE) {
            return null;
        }

        // 外扩采样边，但绝不超出背景节点；超出部分没有壁纸数据。
        double sampleMinX = Math.max(cardSceneX - margin, nodeSceneX);
        double sampleMinY = Math.max(cardSceneY - margin, nodeSceneY);
        double sampleMaxX = Math.min(cardSceneX + cardSceneWidth + margin, nodeSceneX + nodeWidth);
        double sampleMaxY = Math.min(cardSceneY + cardSceneHeight + margin, nodeSceneY + nodeHeight);
        double sampleWidth = sampleMaxX - sampleMinX;
        double sampleHeight = sampleMaxY - sampleMinY;
        if (sampleWidth <= MIN_VISIBLE_SIZE || sampleHeight <= MIN_VISIBLE_SIZE) {
            return null;
        }

        // cover：取较大的缩放比例，居中裁剪，与 Themes.createBackgroundWithOpacity 一致。
        double scale = Math.max(nodeWidth / imageWidth, nodeHeight / imageHeight);
        if (!Double.isFinite(scale) || scale <= 0.0) {
            return null;
        }
        double offsetX = (nodeWidth - imageWidth * scale) / 2.0;
        double offsetY = (nodeHeight - imageHeight * scale) / 2.0;

        double imageX = (sampleMinX - nodeSceneX - offsetX) / scale;
        double imageY = (sampleMinY - nodeSceneY - offsetY) / scale;
        double viewportWidth = sampleWidth / scale;
        double viewportHeight = sampleHeight / scale;

        // 采样矩形已在节点内，理论上不会越界；这里仍钳制浮点误差。
        imageX = clamp(imageX, 0.0, imageWidth);
        imageY = clamp(imageY, 0.0, imageHeight);
        viewportWidth = Math.min(viewportWidth, imageWidth - imageX);
        viewportHeight = Math.min(viewportHeight, imageHeight - imageY);
        if (viewportWidth <= MIN_VISIBLE_SIZE || viewportHeight <= MIN_VISIBLE_SIZE) {
            return null;
        }

        return new Mapping(
                new Rectangle2D(imageX, imageY, viewportWidth, viewportHeight),
                sampleMinX,
                sampleMinY,
                sampleWidth,
                sampleHeight);
    }

    /// 校验所有数值均为有限值。
    private static boolean areFinite(double @NotNull ... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    /// 限制数值范围。
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
