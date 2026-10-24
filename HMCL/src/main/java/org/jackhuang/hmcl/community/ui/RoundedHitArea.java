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
package org.jackhuang.hmcl.community.ui;

import javafx.event.EventTarget;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

/// 让带圆角的 JavaFX 控件只在自己的可见形状内响应鼠标点击。
///
/// JavaFX 的命中测试默认使用节点的矩形布局边界。对 1×1、胶囊等圆角较大
/// 的按钮，用户会点到看得见按钮之外的透明角。这个工具在按下/释放阶段
/// 按节点当前 CSS 背景或边框的圆角再次判断，形状外的点击直接消费。
@NotNullByDefault
public final class RoundedHitArea {

    /// 已经安装过的标记，避免重复注册过滤器。
    private static final String INSTALLED_KEY = "lmcl.roundedHitArea";

    private RoundedHitArea() {
    }

    /// 给按钮安装圆角命中测试。重复调用是安全的。
    ///
    /// @param button 按钮
    public static void install(@NotNull Region button) {
        if (Boolean.TRUE.equals(button.getProperties().get(INSTALLED_KEY))) {
            return;
        }
        button.getProperties().put(INSTALLED_KEY, Boolean.TRUE);
        button.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> consumeOutside(button, event));
        button.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> consumeOutside(button, event));
    }

    private static void consumeOutside(@NotNull Region region, @NotNull MouseEvent event) {
        Point2D local = region.sceneToLocal(event.getSceneX(), event.getSceneY());
        if (!contains(region, local.getX(), local.getY())) {
            event.consume();
        }
    }

    /// 判断本地坐标是否在区域的圆角形状内。
    ///
    /// @param region 区域
    /// @param x 本地 x
    /// @param y 本地 y
    /// @return 是否命中
    static boolean contains(@NotNull Region region, double x, double y) {
        double width = region.getWidth();
        double height = region.getHeight();
        if (width <= 0 || height <= 0 || x < 0 || y < 0 || x > width || y > height) {
            return false;
        }
        return containsRounded(x, y, width, height, radius(region, width, height));
    }

    /// 纯几何判断，便于单元测试。
    ///
    /// @param x 本地 x
    /// @param y 本地 y
    /// @param width 宽
    /// @param height 高
    /// @param radius 圆角
    /// @return 是否在圆角矩形内
    static boolean containsRounded(double x, double y, double width, double height, double radius) {
        if (width <= 0 || height <= 0 || x < 0 || y < 0 || x > width || y > height) {
            return false;
        }
        double r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r <= 0) {
            return true;
        }
        double cx = Math.max(r, Math.min(x, width - r));
        double cy = Math.max(r, Math.min(y, height - r));
        double dx = x - cx;
        double dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private static double radius(@NotNull Region region, double width, double height) {
        double radius = 0;
        Background background = region.getBackground();
        if (background != null) {
            for (BackgroundFill fill : background.getFills()) {
                radius = Math.max(radius, radius(fill.getRadii(), width, height));
            }
        }
        Border border = region.getBorder();
        if (border != null) {
            for (BorderStroke stroke : border.getStrokes()) {
                radius = Math.max(radius, radius(stroke.getRadii(), width, height));
            }
        }
        return radius;
    }

    private static double radius(@NotNull CornerRadii radii, double width, double height) {
        double horizontal = Math.max(
                resolved(radii.getTopLeftHorizontalRadius(), radii.isTopLeftHorizontalRadiusAsPercentage(), width),
                Math.max(
                        resolved(radii.getTopRightHorizontalRadius(), radii.isTopRightHorizontalRadiusAsPercentage(), width),
                        Math.max(
                                resolved(radii.getBottomLeftHorizontalRadius(), radii.isBottomLeftHorizontalRadiusAsPercentage(), width),
                                resolved(radii.getBottomRightHorizontalRadius(), radii.isBottomRightHorizontalRadiusAsPercentage(), width))));
        double vertical = Math.max(
                resolved(radii.getTopLeftVerticalRadius(), radii.isTopLeftVerticalRadiusAsPercentage(), height),
                Math.max(
                        resolved(radii.getTopRightVerticalRadius(), radii.isTopRightVerticalRadiusAsPercentage(), height),
                        Math.max(
                                resolved(radii.getBottomLeftVerticalRadius(), radii.isBottomLeftVerticalRadiusAsPercentage(), height),
                                resolved(radii.getBottomRightVerticalRadius(), radii.isBottomRightVerticalRadiusAsPercentage(), height))));
        return Math.max(horizontal, vertical);
    }

    private static double resolved(double value, boolean percentage, double basis) {
        return percentage ? value * basis : value;
    }
}
