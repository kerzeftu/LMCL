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
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [GlassEdgeFadeImage] 的 headless 像素级测试。
///
/// 所有用例只构造 [WritableImage] 与 [Rectangle2D]，不创建 `Scene`/`Stage`、
/// 不启动 Toolkit，因此可以在无显示环境下运行。
public final class GlassEdgeFadeImageTest {

    /// 上边缘 ramp：边缘 255、正中间 64、末行 0，且逐行单调不增。
    @Test
    public void rampAlphaFollowsSquaredCurve() {
        assertEquals(255, GlassEdgeFadeImage.rampAlpha(0, 3, true));
        assertEquals(64, GlassEdgeFadeImage.rampAlpha(1, 3, true));
        assertEquals(0, GlassEdgeFadeImage.rampAlpha(2, 3, true));

        int previous = 256;
        for (int row = 0; row < 72; row++) {
            int alpha = GlassEdgeFadeImage.rampAlpha(row, 72, true);
            assertTrue(alpha >= 0 && alpha <= 255, "alpha out of range at row " + row);
            assertTrue(alpha <= previous, "ramp must not increase at row " + row);
            previous = alpha;
        }
        assertEquals(255, GlassEdgeFadeImage.rampAlpha(0, 72, true));
        assertEquals(0, GlassEdgeFadeImage.rampAlpha(71, 72, true));
        // 行号越界时按最近的端行处理。
        assertEquals(255, GlassEdgeFadeImage.rampAlpha(-5, 72, true));
        assertEquals(0, GlassEdgeFadeImage.rampAlpha(99, 72, true));
    }

    /// 下边缘 ramp 与上边缘对称：首行全透明，最后一行（视口边缘）完全不透明。
    @Test
    public void rampAlphaMirrorsForBottomEdge() {
        assertEquals(0, GlassEdgeFadeImage.rampAlpha(0, 3, false));
        assertEquals(64, GlassEdgeFadeImage.rampAlpha(1, 3, false));
        assertEquals(255, GlassEdgeFadeImage.rampAlpha(2, 3, false));

        int previous = -1;
        for (int row = 0; row < 72; row++) {
            int alpha = GlassEdgeFadeImage.rampAlpha(row, 72, false);
            assertTrue(alpha >= 0 && alpha <= 255, "alpha out of range at row " + row);
            assertTrue(alpha >= previous, "ramp must not decrease at row " + row);
            previous = alpha;
        }
        assertEquals(0, GlassEdgeFadeImage.rampAlpha(0, 72, false));
        assertEquals(255, GlassEdgeFadeImage.rampAlpha(71, 72, false));
    }

    /// 单行/空带没有过渡空间，整行保持不透明；两行时恰好 255→0。
    @Test
    public void rampAlphaHandlesDegenerateHeights() {
        assertEquals(255, GlassEdgeFadeImage.rampAlpha(0, 1, true));
        assertEquals(255, GlassEdgeFadeImage.rampAlpha(0, 1, false));
        assertEquals(255, GlassEdgeFadeImage.rampAlpha(7, 1, true));

        assertEquals(255, GlassEdgeFadeImage.rampAlpha(0, 2, true));
        assertEquals(0, GlassEdgeFadeImage.rampAlpha(1, 2, true));
        assertEquals(0, GlassEdgeFadeImage.rampAlpha(0, 2, false));
        assertEquals(255, GlassEdgeFadeImage.rampAlpha(1, 2, false));
    }

    /// 单色壁纸 + 同色表面：整条带（含外缘）材质化后仍为该色，alpha 按新 ramp 变化。
    @Test
    public void renderUniformWallpaperFillsWholeBandWithMaterialColor() {
        Color color = Color.web("#336699");
        WritableImage rendered = GlassEdgeFadeImage.render(
                uniformWallpaper(24, 24, color), new Rectangle2D(0, 0, 24, 24), 1.0, color, 24, 24, true);

        assertNotNull(rendered);
        assertEquals(24.0, rendered.getWidth(), 0.0);
        assertEquals(24.0, rendered.getHeight(), 0.0);

        int expectedRgb = rgb(color);
        for (int row = 0; row < 24; row++) {
            int alpha = GlassEdgeFadeImage.rampAlpha(row, 24, true);
            int argb = pixel(rendered, 0, row);
            assertEquals(alpha, argb >>> 24, "alpha at row " + row);
            if (alpha >= 128) {
                // 预乘 alpha 存储会带来少量通道舍入误差。
                assertRgb(expectedRgb, argb, 2);
            }
        }
        assertEquals(255, pixel(rendered, 0, 0) >>> 24, "edge row must be opaque");
        assertEquals(0, pixel(rendered, 0, 23) >>> 24, "inner row must be transparent");
    }

    /// 下边缘整条带：首行全透明、末行完全不透明，RGB 仍为单色。
    @Test
    public void renderBottomEdgeReversesRamp() {
        Color color = Color.web("#336699");
        WritableImage rendered = GlassEdgeFadeImage.render(
                uniformWallpaper(24, 24, color), new Rectangle2D(0, 0, 24, 24), 1.0, color, 24, 24, false);

        assertNotNull(rendered);
        assertEquals(24.0, rendered.getHeight(), 0.0);
        for (int row = 0; row < 24; row++) {
            assertEquals(GlassEdgeFadeImage.rampAlpha(row, 24, false), pixel(rendered, 0, row) >>> 24,
                    "alpha at row " + row);
        }
        assertEquals(0, pixel(rendered, 0, 0) >>> 24, "inner row must be transparent");
        assertEquals(255, pixel(rendered, 0, 23) >>> 24, "edge row must be opaque");
        assertRgb(rgb(color), pixel(rendered, 0, 23), 0);
    }

    /// 整条带（含上下外缘）都使用材质色：双色壁纸不再在带外缘留下清晰壁纸，
    /// 外缘与相邻材质行同色，因此不会形成比带身更亮的亮边。
    @Test
    public void renderMaterializesWholeBandIncludingOuterEdges() {
        // 块平均：左 12 列红 + 右 12 列蓝 → (128, 0, 128)；与黑色表面按 22% tint 后为 (100, 0, 100)。
        int materialRed = tinted(128, 0);
        int expectedMaterial = (materialRed << 16) | materialRed;
        assertEquals(0x640064, expectedMaterial);

        WritableImage topRendered = GlassEdgeFadeImage.render(
                splitWallpaper(), new Rectangle2D(0, 0, 24, 24), 1.0, Color.BLACK, 24, 24, true);
        WritableImage bottomRendered = GlassEdgeFadeImage.render(
                splitWallpaper(), new Rectangle2D(0, 0, 24, 24), 1.0, Color.BLACK, 24, 24, false);
        assertNotNull(topRendered);
        assertNotNull(bottomRendered);

        // 上外缘行 alpha 255，颜色是材质色而不是清晰的红/蓝壁纸；跨列同样成立。
        assertEquals(255, pixel(topRendered, 0, 0) >>> 24);
        assertEquals(255, pixel(topRendered, 23, 0) >>> 24);
        assertRgb(expectedMaterial, pixel(topRendered, 0, 0), 0);
        assertRgb(expectedMaterial, pixel(topRendered, 23, 0), 0);

        // 下外缘（末行）alpha 255，颜色同样是材质色，且与相邻材质行同色。
        assertEquals(255, pixel(bottomRendered, 0, 23) >>> 24);
        assertEquals(255, pixel(bottomRendered, 23, 23) >>> 24);
        assertRgb(expectedMaterial, pixel(bottomRendered, 0, 23), 0);
        assertRgb(expectedMaterial, pixel(bottomRendered, 23, 23), 0);
        assertRgb(pixel(bottomRendered, 0, 22) & 0xFFFFFF, pixel(bottomRendered, 0, 23), 2);

        // 所有可见行（alpha >= 128）都是同一材质色，带内没有清晰采样残留。
        for (int row = 0; row < 24; row++) {
            if ((pixel(bottomRendered, 0, row) >>> 24) >= 128) {
                assertRgb(expectedMaterial, pixel(bottomRendered, 0, row), 2);
                assertRgb(expectedMaterial, pixel(bottomRendered, 23, row), 2);
            }
        }
    }

    /// 表面色 tint：材质层向表面色偏移 22%，外缘行同样是 tint 后的材质色。
    @Test
    public void renderTintsMaterialWithSurfaceColor() {
        WritableImage rendered = GlassEdgeFadeImage.render(
                uniformWallpaper(24, 24, Color.BLUE), new Rectangle2D(0, 0, 24, 24), 1.0, Color.RED, 24, 24, true);

        assertNotNull(rendered);

        // 材质 = 蓝色 * 0.78 + 红色 * 0.22。
        int expectedRed = tinted(0, 255);
        int expectedBlue = tinted(255, 0);
        assertEquals(56, expectedRed);
        assertEquals(199, expectedBlue);
        int expectedMaterial = (expectedRed << 16) | expectedBlue;

        // 外缘行 alpha 255，颜色同样是 tint 后的材质色（不再保留未 tint 的清晰壁纸）。
        assertEquals(255, pixel(rendered, 0, 0) >>> 24);
        assertRgb(expectedMaterial, pixel(rendered, 0, 0), 0);

        int argb = pixel(rendered, 0, 7);
        assertTrue(argb >>> 24 > 0, "material row must stay visible");
        assertRgb(expectedMaterial, argb, 1);
    }

    /// 2:1 源图、1×1 输出时取图片中心像素（防采样半像素偏移）。
    @Test
    public void renderSamplesCenterWhenOutputIsOnePixel() {
        WritableImage wallpaper = new WritableImage(4, 2);
        PixelWriter writer = wallpaper.getPixelWriter();
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 4; x++) {
                writer.setColor(x, y, x == 1 || x == 2 ? Color.LIME : Color.RED);
            }
        }

        // 表面色取采样中心色，材质化（块平均 + 22% tint）不改变颜色，便于精确断言采样位置。
        WritableImage rendered = GlassEdgeFadeImage.render(
                wallpaper, new Rectangle2D(0, 0, 4, 2), 1.0, Color.LIME, 1, 1, true);

        assertNotNull(rendered);
        assertEquals(1.0, rendered.getWidth(), 0.0);
        assertEquals(1.0, rendered.getHeight(), 0.0);
        assertEquals(255, pixel(rendered, 0, 0) >>> 24);
        assertEquals(0x00FF00, pixel(rendered, 0, 0) & 0xFFFFFF);
    }

    /// 采样必须遵守 imageViewport 的偏移，而不是总从图片左上角开始。
    @Test
    public void renderRespectsImageViewportOffset() {
        // 表面色取视口中心色，材质化（块平均 + 22% tint）不改变颜色，便于精确断言采样位置。
        WritableImage rendered = GlassEdgeFadeImage.render(
                quadrantWallpaper(), new Rectangle2D(2, 2, 2, 2), 1.0, Color.YELLOW, 1, 1, true);

        assertNotNull(rendered);
        assertEquals(255, pixel(rendered, 0, 0) >>> 24);
        assertRgb(0xFFFF00, pixel(rendered, 0, 0), 0);
    }

    /// 半透明壁纸在非透明窗口下与表面色合成，再整体材质化后输出不透明 RGB。
    @Test
    public void renderCompositesSurfaceForTranslucentWallpaper() {
        WritableImage rendered = GlassEdgeFadeImage.render(
                translucentWallpaper(), new Rectangle2D(0, 0, 2, 2), 1.0, Color.WHITE, 2, 2, true);

        assertNotNull(rendered);
        int argb = pixel(rendered, 0, 0);
        assertEquals(255, argb >>> 24);
        // a = 128 / 255：合成色为 (255, 127, 127)；材质化再向白色 tint 22%，
        // 红保持 255，绿/蓝 = 127 * 0.78 + 255 * 0.22 = 155.16 → 155。
        assertEquals(255, (argb >> 16) & 0xFF);
        assertEquals(tinted(127, 255), (argb >> 8) & 0xFF);
        assertEquals(tinted(127, 255), argb & 0xFF);
    }

    /// 透明窗口（surfaceColor 为空）无法复现页面真实背景，直接返回 `null`。
    @Test
    public void renderReturnsNullForTransparentWindow() {
        // 即使壁纸完全不透明也一样：透明窗口背后是桌面，无法确定真实背景。
        assertNull(GlassEdgeFadeImage.render(
                quadrantWallpaper(), new Rectangle2D(0, 0, 4, 4), 1.0, null, 4, 4, true));
        assertNull(GlassEdgeFadeImage.render(
                translucentWallpaper(), new Rectangle2D(0, 0, 2, 2), 1.0, null, 2, 2, true));
    }

    /// 无效尺寸、零面积视口或非有限透明度不生成图片。
    @Test
    public void renderReturnsNullForInvalidGeometry() {
        WritableImage wallpaper = quadrantWallpaper();

        assertNull(GlassEdgeFadeImage.render(
                wallpaper, new Rectangle2D(0, 0, 4, 4), 1.0, Color.WHITE, 0, 4, true));
        assertNull(GlassEdgeFadeImage.render(
                wallpaper, new Rectangle2D(0, 0, 4, 4), 1.0, Color.WHITE, 4, 0, true));
        assertNull(GlassEdgeFadeImage.render(
                wallpaper, new Rectangle2D(0, 0, 0, 4), 1.0, Color.WHITE, 4, 4, true));
        assertNull(GlassEdgeFadeImage.render(
                wallpaper, new Rectangle2D(0, 0, 4, 4), Double.NaN, Color.WHITE, 4, 4, true));
    }

    /// 单色壁纸（`width × height`，所有像素都是 `color`）。
    private static WritableImage uniformWallpaper(int width, int height, Color color) {
        WritableImage image = new WritableImage(width, height);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                writer.setColor(x, y, color);
            }
        }
        return image;
    }

    /// 24×24 双色壁纸：左半红、右半蓝，用于验证块平均材质化。
    private static WritableImage splitWallpaper() {
        WritableImage image = new WritableImage(24, 24);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                writer.setColor(x, y, x < 12 ? Color.RED : Color.BLUE);
            }
        }
        return image;
    }

    /// 把 [Color] 转成 `0xRRGGBB`（通道取整方式与渲染器一致）。
    private static int rgb(Color color) {
        return (channel(color.getRed()) << 16) | (channel(color.getGreen()) << 8) | channel(color.getBlue());
    }

    /// 把 `[0, 1]` 的通道值转成 8 位通道。
    private static int channel(double value) {
        return (int) Math.round(value * 255.0);
    }

    /// 材质层通道：块平均色与表面色按 [GlassEdgeFadeImage#MATERIAL_TINT] 混合后取整。
    private static int tinted(int average, int surface) {
        return (int) Math.round(average * (1.0 - GlassEdgeFadeImage.MATERIAL_TINT)
                + surface * GlassEdgeFadeImage.MATERIAL_TINT);
    }

    /// 断言 ARGB 像素的 RGB 部分等于期望颜色（允许预乘 alpha 存储的舍入误差）。
    private static void assertRgb(int expected, int argb, int tolerance) {
        assertChannel((expected >> 16) & 0xFF, (argb >> 16) & 0xFF, tolerance, "red");
        assertChannel((expected >> 8) & 0xFF, (argb >> 8) & 0xFF, tolerance, "green");
        assertChannel(expected & 0xFF, argb & 0xFF, tolerance, "blue");
    }

    /// 断言单个通道在容差内相等。
    private static void assertChannel(int expected, int actual, int tolerance, String channel) {
        assertTrue(Math.abs(expected - actual) <= tolerance,
                channel + " expected " + expected + " but was " + actual);
    }

    /// 4×4 四色壁纸：左上红、右上绿、左下蓝、右下黄，每块 2×2。
    private static WritableImage quadrantWallpaper() {
        WritableImage image = new WritableImage(4, 4);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                Color color;
                if (x < 2) {
                    color = y < 2 ? Color.RED : Color.BLUE;
                } else {
                    color = y < 2 ? Color.LIME : Color.YELLOW;
                }
                writer.setColor(x, y, color);
            }
        }
        return image;
    }

    /// 2×2 半透明红色壁纸：ARGB = `0x80FF0000`。
    private static WritableImage translucentWallpaper() {
        WritableImage image = new WritableImage(2, 2);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                writer.setArgb(x, y, 0x80FF0000);
            }
        }
        return image;
    }

    /// 读取输出图片的一个像素。
    private static int pixel(WritableImage image, int x, int y) {
        PixelReader reader = image.getPixelReader();
        assertNotNull(reader, "pixel reader");
        return reader.getArgb(x, y);
    }
}
