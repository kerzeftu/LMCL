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
package org.jackhuang.hmcl.community.dashboard.builtin;

import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// [MaterialCardSurface] 的透明守卫测试。
///
/// 只构造 `Region`/`Background` 值对象，不需要 JavaFX Toolkit；守卫是否生效完全
/// 由注入的玻璃开关控制，因此结果确定。
@NotNullByDefault
public final class MaterialCardSurfaceTest {

    /// 玻璃开启时，内容根的实色背景被替换为透明。
    @Test
    public void glassEnabledClearsOpaqueBackground() {
        VBox content = new VBox();
        content.setBackground(solid());
        AtomicBoolean glass = new AtomicBoolean(true);

        MaterialCardSurface.install(content, glass::get);

        assertTrue(MaterialCardSurface.isTransparent(content.getBackground()),
                "glass must hide the content background");
    }

    /// 玻璃关闭时，内容根保留原来的实色背景。
    @Test
    public void glassDisabledKeepsOpaqueBackground() {
        VBox content = new VBox();
        Background solid = solid();
        content.setBackground(solid);
        AtomicBoolean glass = new AtomicBoolean(false);

        MaterialCardSurface.install(content, glass::get);

        assertSame(solid, content.getBackground(),
                "solid mode must keep the content background");
    }

    /// 玻璃关闭后刷新一次，恢复此前被挡下的实色背景。
    @Test
    public void refreshRestoresBlockedBackgroundAfterGlassOff() {
        VBox content = new VBox();
        Background solid = solid();
        content.setBackground(solid);
        AtomicBoolean glass = new AtomicBoolean(true);
        MaterialCardSurface.install(content, glass::get);
        assertTrue(MaterialCardSurface.isTransparent(content.getBackground()));

        glass.set(false);
        MaterialCardSurface.refresh(content);

        assertSame(solid, content.getBackground(),
                "turning glass off must restore the solid card surface");
    }

    /// 玻璃开启但内容本来透明时不写入任何背景。
    @Test
    public void transparentBackgroundIsLeftAlone() {
        VBox content = new VBox();
        AtomicBoolean glass = new AtomicBoolean(true);

        MaterialCardSurface.install(content, glass::get);

        assertTrue(content.getBackground() == null || content.getBackground().isEmpty(),
                "empty background must stay empty");
    }

    /// 卸载守卫后不再清空新设置的实色背景。
    @Test
    public void uninstallStopsClearingBackground() {
        VBox content = new VBox();
        content.setBackground(solid());
        AtomicBoolean glass = new AtomicBoolean(true);
        MaterialCardSurface.install(content, glass::get);
        MaterialCardSurface.uninstall(content);

        Background next = solid();
        content.setBackground(next);

        assertSame(next, content.getBackground(),
                "uninstalled guard must not touch the background anymore");
    }

    /// 透明判定：空值、空背景、全透明颜色都是透明；实色与渐变按可见处理。
    @Test
    public void isTransparentRecognisesVisibleFills() {
        assertTrue(MaterialCardSurface.isTransparent(null));
        assertTrue(MaterialCardSurface.isTransparent(Background.EMPTY));
        assertTrue(MaterialCardSurface.isTransparent(new Background(
                new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY))));
        assertFalse(MaterialCardSurface.isTransparent(solid()));
        assertFalse(MaterialCardSurface.isTransparent(new Background(
                new BackgroundFill(Color.rgb(255, 255, 255, 0.5), CornerRadii.EMPTY, Insets.EMPTY))));
    }

    /// 构造一层 50% 不透明的白色背景，模拟 `.card` 从主题拿到的表面。
    ///
    /// @return 实色背景
    private static Background solid() {
        return new Background(new BackgroundFill(
                Color.rgb(255, 255, 255, 0.5), CornerRadii.EMPTY, Insets.EMPTY));
    }
}
