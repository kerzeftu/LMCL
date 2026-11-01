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
 * Modified by LMCL team on 2026-11-01: materialized viewport edge fade band image generation.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// 组件区视口边缘过渡带的纯图像生成器。
///
/// 主页两列 `ScrollPane` 的内容会在视口上下边界被硬裁切。本类生成一张
/// `width × bandHeight` 的整条过渡带图片：壁纸按 `cover` 采样并与表面色合成出
/// 背景底图，再整条按 24 px 网格块平均、双线性上采样得到与卡片毛玻璃同质的
/// 「材质」背景（并混入 22% 表面色 tint）。整条带（包括靠视口边缘的外缘行）
/// 一律使用材质色，不保留清晰壁纸采样，避免深色主题下外缘比带身更亮而形成一条
/// 亮边；图片 alpha 从视口边缘的完全不透明按 `(1 - t)²` 降到内侧的完全透明。
/// 把它叠在滚动内容之上时，内容会在靠边的一小段距离内逐渐溶解进与卡片同质的
/// 背景，不再出现硬边或色带。
///
/// 图片的 RGB 与页面背景逐点一致：`imageViewport` 给出过渡带覆盖的壁纸图片
/// 像素矩形（由 [GlassLiveViewport#map] 按 `cover` 比例算出），本类只在该矩形
/// 内做双线性采样，因此不会引入采样偏移或横向压缩。
///
/// 本类只使用 [WritableImage]、[PixelReader]、[PixelWriter]、[Color] 与
/// [Rectangle2D]，不创建 `Canvas`/`Scene`/`Stage`，也不启动 Toolkit，因此可以
/// 在无显示环境下做像素级单元测试。
///
/// 返回整条带（不再按滚动距离裁剪），调用方通过缩放整张图片得到当前可见高度。
/// 参数无效、壁纸没有可读像素或图片尚未加载完成时返回 `null`；`surfaceColor` 为
/// 空（透明窗口）时无法复现页面真实背景，同样返回 `null`，调用方应隐藏过渡带。
@NotNullByDefault
final class GlassEdgeFadeImage {

    /// 材质化块平均的网格边长（像素）：与卡片毛玻璃的模糊尺度对应。
    static final int MATERIAL_BLOCK_SIZE = 24;

    /// 材质层与表面色的混合比例：对齐卡片 `surface-container-low` 的 22% tint。
    static final double MATERIAL_TINT = 0.22;

    /// 工具类，不允许实例化。
    private GlassEdgeFadeImage() {
    }

    /// 计算边缘过渡带某一行的 alpha 值。
    ///
    /// 曲线为 `(1 - t)²`：`t` 在视口边缘处为 0、在过渡带内侧为 1，因此边缘行
    /// 完全不透明（255），内侧行完全透明（0），中间单调过渡。上边缘的
    /// `t = row / (height - 1)`，下边缘的 `t = (height - 1 - row) / (height - 1)`。
    /// 相比 `1 - smoothstep(t)`，新曲线在边缘附近衰减更快，避免前 ~20 px 近似
    /// 实色而形成色带。
    ///
    /// @param row     过渡带内的行号（0 起，0 表示图片顶部）
    /// @param height  过渡带总行数；小于等于 1 时整条带只有边缘一行，返回 255
    /// @param topEdge 是否位于视口上边缘；`false` 表示下边缘
    /// @return 0..255 的 alpha；行号越界时按最近的端行处理
    static int rampAlpha(int row, int height, boolean topEdge) {
        if (height <= 1) {
            return 255;
        }
        int clampedRow = Math.max(0, Math.min(height - 1, row));
        double position = topEdge
                ? (double) clampedRow / (height - 1)
                : (double) (height - 1 - clampedRow) / (height - 1);
        double remaining = 1.0 - position;
        long alpha = Math.round(255.0 * remaining * remaining);
        return (int) Math.max(0L, Math.min(255L, alpha));
    }

    /// 生成一条完整的视口边缘过渡带图片。
    ///
    /// 采样：输出第 `column` 列、第 `row` 行对应壁纸图片坐标
    /// `(viewport.minX + (column + 0.5) / width * viewport.width,
    /// viewport.minY + (row + 0.5) / bandHeight * viewport.height)`，在四个相邻
    /// 像素间做双线性插值（越界坐标钳制到图片边缘），再按
    /// `a = srcAlpha / 255 * opacity`、`rgb = src * a + surface * (1 - a)` 与
    /// 表面色合成，得到背景底图 RGB。
    ///
    /// 材质化：背景底图按 [MATERIAL_BLOCK_SIZE] 的网格做块平均，再以块中心为
    /// 节点双线性上采样铺满整条带，最后按 [MATERIAL_TINT] 混入表面色；这一步模拟
    /// 卡片的模糊与 tint，使过渡带与内容同质，而不是一条清晰的壁纸色带。
    /// 整条带逐行（含视口边缘行）都使用材质色，不保留清晰壁纸外缘，上、下边缘
    /// 同样适用，因此深色主题下不会出现比带身更亮的外缘亮边。
    ///
    /// alpha：整条带使用 [rampAlpha] 的完整 ramp（边缘 255、内侧 0），不再按滚动
    /// 距离裁剪，调用方可以缩放整张图片得到任意可见高度。
    ///
    /// @param wallpaper     壁纸图片，必须已加载且可读
    /// @param imageViewport 过渡带覆盖的壁纸图片像素矩形
    /// @param opacity       背景节点透明度，钳制到 `[0, 1]`
    /// @param surfaceColor  表面色；`null` 表示透明窗口，直接返回 `null`
    /// @param width         输出宽度（像素），必须大于 0
    /// @param bandHeight    输出高度（像素），必须大于 0
    /// @param topEdge       是否位于视口上边缘
    /// @return 过渡带图片；参数无效、壁纸不可读或 `surfaceColor` 为空时返回 `null`
    @Nullable
    static WritableImage render(
            Image wallpaper,
            Rectangle2D imageViewport,
            double opacity,
            @Nullable Color surfaceColor,
            int width,
            int bandHeight,
            boolean topEdge) {
        Objects.requireNonNull(wallpaper, "wallpaper");
        Objects.requireNonNull(imageViewport, "imageViewport");
        if (surfaceColor == null) {
            // 透明窗口背后是桌面，无法复现页面的真实背景，直接隐藏过渡带。
            return null;
        }
        if (width <= 0 || bandHeight <= 0 || !Double.isFinite(opacity)) {
            return null;
        }
        double opacityFactor = Math.max(0.0, Math.min(1.0, opacity));
        if (!isUsableViewport(imageViewport)) {
            return null;
        }

        int imageWidth = (int) Math.floor(wallpaper.getWidth());
        int imageHeight = (int) Math.floor(wallpaper.getHeight());
        if (imageWidth < 1 || imageHeight < 1) {
            return null;
        }
        @Nullable PixelReader reader = wallpaper.getPixelReader();
        if (reader == null) {
            return null;
        }

        int surfaceRed = clampByte(surfaceColor.getRed() * 255.0);
        int surfaceGreen = clampByte(surfaceColor.getGreen() * 255.0);
        int surfaceBlue = clampByte(surfaceColor.getBlue() * 255.0);

        // 第一步：按 cover 采样并与表面色合成，得到整条带的背景底图。
        int[] sampled = new int[width * bandHeight];
        double viewportMinX = imageViewport.getMinX();
        double viewportMinY = imageViewport.getMinY();
        double scaleX = imageViewport.getWidth() / width;
        double scaleY = imageViewport.getHeight() / bandHeight;
        for (int row = 0; row < bandHeight; row++) {
            double imageY = viewportMinY + (row + 0.5) * scaleY;
            int offset = row * width;
            for (int column = 0; column < width; column++) {
                double imageX = viewportMinX + (column + 0.5) * scaleX;
                int argb = sampleBilinear(reader, imageWidth, imageHeight, imageX, imageY);
                double alphaFactor = (argb >>> 24) / 255.0 * opacityFactor;
                int red = blendChannel((argb >> 16) & 0xFF, surfaceRed, alphaFactor);
                int green = blendChannel((argb >> 8) & 0xFF, surfaceGreen, alphaFactor);
                int blue = blendChannel(argb & 0xFF, surfaceBlue, alphaFactor);
                sampled[offset + column] = pack(red, green, blue);
            }
        }

        // 第二步：整条带材质化，得到与卡片毛玻璃同质的 RGB。
        int[] material = materialize(sampled, width, bandHeight, surfaceRed, surfaceGreen, surfaceBlue);

        // 第三步：逐行写入材质色与整条带的 alpha ramp；外缘行同样是材质色。
        WritableImage output = new WritableImage(width, bandHeight);
        PixelWriter writer = output.getPixelWriter();
        for (int row = 0; row < bandHeight; row++) {
            int alpha = rampAlpha(row, bandHeight, topEdge);
            int offset = row * width;
            for (int column = 0; column < width; column++) {
                writer.setArgb(column, row, (alpha << 24) | material[offset + column]);
            }
        }
        return output;
    }

    /// 由背景底图生成材质层：块平均后双线性上采样，并混入表面色 tint。
    ///
    /// 块网格覆盖整条 `width × bandHeight`，最后一个块行/列可能不足
    /// [MATERIAL_BLOCK_SIZE]，仍按格内实际像素数求均值。上采样以块中心为节点做
    /// 双线性插值，边缘钳制到最近的块中心。
    ///
    /// @param sampled      背景底图 RGB（行优先，`0xRRGGBB`），长度 `width * bandHeight`
    /// @param width        数组宽度
    /// @param bandHeight   数组高度
    /// @param surfaceRed   表面色红通道（0..255）
    /// @param surfaceGreen 表面色绿通道（0..255）
    /// @param surfaceBlue  表面色蓝通道（0..255）
    /// @return 材质层 RGB（行优先，`0xRRGGBB`），尺寸与 `sampled` 相同
    private static int[] materialize(
            int[] sampled,
            int width,
            int bandHeight,
            int surfaceRed,
            int surfaceGreen,
            int surfaceBlue) {
        int blocksX = (width + MATERIAL_BLOCK_SIZE - 1) / MATERIAL_BLOCK_SIZE;
        int blocksY = (bandHeight + MATERIAL_BLOCK_SIZE - 1) / MATERIAL_BLOCK_SIZE;
        int[] blockMeans = new int[blocksX * blocksY];
        for (int blockY = 0; blockY < blocksY; blockY++) {
            int minRow = blockY * MATERIAL_BLOCK_SIZE;
            int maxRow = Math.min(bandHeight, minRow + MATERIAL_BLOCK_SIZE);
            for (int blockX = 0; blockX < blocksX; blockX++) {
                int minColumn = blockX * MATERIAL_BLOCK_SIZE;
                int maxColumn = Math.min(width, minColumn + MATERIAL_BLOCK_SIZE);
                long red = 0L;
                long green = 0L;
                long blue = 0L;
                long count = 0L;
                for (int row = minRow; row < maxRow; row++) {
                    int offset = row * width;
                    for (int column = minColumn; column < maxColumn; column++) {
                        int rgb = sampled[offset + column];
                        red += (rgb >> 16) & 0xFF;
                        green += (rgb >> 8) & 0xFF;
                        blue += rgb & 0xFF;
                        count++;
                    }
                }
                blockMeans[blockY * blocksX + blockX] = pack(
                        (int) Math.round((double) red / count),
                        (int) Math.round((double) green / count),
                        (int) Math.round((double) blue / count));
            }
        }

        double lastBlockX = blocksX - 1.0;
        double lastBlockY = blocksY - 1.0;
        double materialWeight = 1.0 - MATERIAL_TINT;
        int[] material = new int[sampled.length];
        for (int row = 0; row < bandHeight; row++) {
            // 块中心在输出像素坐标中的位置为 (block + 0.5) * MATERIAL_BLOCK_SIZE，
            // 因此像素中心 (row + 0.5) 对应的块坐标为如下换算。
            double blockY = (row + 0.5) / MATERIAL_BLOCK_SIZE - 0.5;
            double clampedY = Math.max(0.0, Math.min(lastBlockY, blockY));
            int y0 = (int) clampedY;
            int y1 = Math.min(y0 + 1, blocksY - 1);
            double fractionY = clampedY - y0;
            int offset = row * width;
            for (int column = 0; column < width; column++) {
                double blockX = (column + 0.5) / MATERIAL_BLOCK_SIZE - 0.5;
                double clampedX = Math.max(0.0, Math.min(lastBlockX, blockX));
                int x0 = (int) clampedX;
                int x1 = Math.min(x0 + 1, blocksX - 1);
                double fractionX = clampedX - x0;

                int c00 = blockMeans[y0 * blocksX + x0];
                int c10 = blockMeans[y0 * blocksX + x1];
                int c01 = blockMeans[y1 * blocksX + x0];
                int c11 = blockMeans[y1 * blocksX + x1];
                double weight00 = (1.0 - fractionX) * (1.0 - fractionY);
                double weight10 = fractionX * (1.0 - fractionY);
                double weight01 = (1.0 - fractionX) * fractionY;
                double weight11 = fractionX * fractionY;

                int red = blendChannel(
                        interpolateChannel(c00, c10, c01, c11, 16, weight00, weight10, weight01, weight11),
                        surfaceRed, materialWeight);
                int green = blendChannel(
                        interpolateChannel(c00, c10, c01, c11, 8, weight00, weight10, weight01, weight11),
                        surfaceGreen, materialWeight);
                int blue = blendChannel(
                        interpolateChannel(c00, c10, c01, c11, 0, weight00, weight10, weight01, weight11),
                        surfaceBlue, materialWeight);
                material[offset + column] = pack(red, green, blue);
            }
        }
        return material;
    }

    /// 把三个 8 位通道打包成 `0xRRGGBB`。
    ///
    /// @param red   红通道（0..255）
    /// @param green 绿通道（0..255）
    /// @param blue  蓝通道（0..255）
    /// @return 打包后的 RGB
    private static int pack(int red, int green, int blue) {
        return (red << 16) | (green << 8) | blue;
    }

    /// 校验采样矩形可用于插值。
    ///
    /// @param viewport 壁纸图片像素矩形
    /// @return 坐标有限且宽高为正时返回 `true`
    private static boolean isUsableViewport(Rectangle2D viewport) {
        return Double.isFinite(viewport.getMinX())
                && Double.isFinite(viewport.getMinY())
                && Double.isFinite(viewport.getWidth())
                && Double.isFinite(viewport.getHeight())
                && viewport.getWidth() > 0.0
                && viewport.getHeight() > 0.0;
    }

    /// 在壁纸图片上做双线性采样。
    ///
    /// 图片像素 `i` 覆盖连续坐标 `[i, i + 1)`，中心为 `i + 0.5`；采样坐标
    /// 先减去 0.5 换算成像素中心坐标，再取相邻四像素加权。坐标超出图片时
    /// 钳制到边缘像素，避免 `cover` 映射的浮点误差读到图片外。
    ///
    /// @param reader      壁纸像素读取器
    /// @param imageWidth  壁纸宽度（至少 1）
    /// @param imageHeight 壁纸高度（至少 1）
    /// @param imageX      图片像素坐标 X
    /// @param imageY      图片像素坐标 Y
    /// @return 非预乘 ARGB
    private static int sampleBilinear(
            PixelReader reader,
            int imageWidth,
            int imageHeight,
            double imageX,
            double imageY) {
        double pixelX = Math.max(0.0, Math.min(imageWidth - 1.0, imageX - 0.5));
        double pixelY = Math.max(0.0, Math.min(imageHeight - 1.0, imageY - 0.5));
        int x0 = (int) Math.floor(pixelX);
        int y0 = (int) Math.floor(pixelY);
        int x1 = Math.min(x0 + 1, imageWidth - 1);
        int y1 = Math.min(y0 + 1, imageHeight - 1);
        double fractionX = pixelX - x0;
        double fractionY = pixelY - y0;

        int c00 = reader.getArgb(x0, y0);
        int c10 = reader.getArgb(x1, y0);
        int c01 = reader.getArgb(x0, y1);
        int c11 = reader.getArgb(x1, y1);

        double weight00 = (1.0 - fractionX) * (1.0 - fractionY);
        double weight10 = fractionX * (1.0 - fractionY);
        double weight01 = (1.0 - fractionX) * fractionY;
        double weight11 = fractionX * fractionY;

        int alpha = interpolateChannel(c00, c10, c01, c11, 24, weight00, weight10, weight01, weight11);
        int red = interpolateChannel(c00, c10, c01, c11, 16, weight00, weight10, weight01, weight11);
        int green = interpolateChannel(c00, c10, c01, c11, 8, weight00, weight10, weight01, weight11);
        int blue = interpolateChannel(c00, c10, c01, c11, 0, weight00, weight10, weight01, weight11);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /// 按四个相邻像素的权重插值一个 8 位通道。
    ///
    /// @param c00      左上像素的 ARGB
    /// @param c10      右上像素的 ARGB
    /// @param c01      左下像素的 ARGB
    /// @param c11      右下像素的 ARGB
    /// @param shift    通道位移（24/16/8/0）
    /// @param weight00 左上权重
    /// @param weight10 右上权重
    /// @param weight01 左下权重
    /// @param weight11 右下权重
    /// @return 0..255 的插值结果
    private static int interpolateChannel(
            int c00,
            int c10,
            int c01,
            int c11,
            int shift,
            double weight00,
            double weight10,
            double weight01,
            double weight11) {
        double value = ((c00 >>> shift) & 0xFF) * weight00
                + ((c10 >>> shift) & 0xFF) * weight10
                + ((c01 >>> shift) & 0xFF) * weight01
                + ((c11 >>> shift) & 0xFF) * weight11;
        return clampByte(value);
    }

    /// 把源色与表面色按 alpha 合成到 0..255。
    ///
    /// @param source  源通道（0..255）
    /// @param surface 表面通道（0..255）
    /// @param alpha   源色权重，`[0, 1]`
    /// @return 合成后的通道（0..255）
    private static int blendChannel(int source, int surface, double alpha) {
        return clampByte(source * alpha + surface * (1.0 - alpha));
    }

    /// 四舍五入并钳制到 `[0, 255]`。
    ///
    /// @param value 任意浮点通道值
    /// @return 0..255 的字节值
    private static int clampByte(double value) {
        long rounded = Math.round(value);
        return (int) Math.max(0L, Math.min(255L, rounded));
    }
}
