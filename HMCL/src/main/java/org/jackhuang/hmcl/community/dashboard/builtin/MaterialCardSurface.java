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

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.scene.Parent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.community.ui.m3.M3Settings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/// 磨砂材质卡内容根的透明守卫。
///
/// `materialCard` 的直接子节点通常自带 `.card` 类，会从主题里拿到一层实色
/// 背景。2×1 卡的内容往往比卡面矮，实色背景之外还能露出一圈玻璃；而 1×1 卡
/// 的内容正好铺满整张卡，只要内容根不透明，`GlassSurface` 就会被完全盖住，
/// 看起来和普通实色卡一样。
///
/// 只靠样式表保证并不可靠：层叠结果取决于样式表的安装位置与主题切换的顺序。
/// 因此这里在内容根上挂 `Background` 监听——每次 CSS 或代码重新设置背景时，
/// 若玻璃开启（[M3Settings#isGlassEnabled()]）且背景含可见填充，就把背景替换
/// 为 [Background#EMPTY]，并记住被挡下的背景；玻璃关闭或切回 Classic 后恢复
/// 该背景，卡片重新得到实色底。守卫只作用于 `materialCard` 包裹的根节点，
/// 不触碰其他卡片的样式。
@NotNullByDefault
final class MaterialCardSurface {

    /// 守卫在内容根 [javafx.scene.Node#getProperties()] 中的键。
    private static final String GUARD_KEY = "lmcl.community.material-card-surface";

    /// 工具类，不允许实例化。
    private MaterialCardSurface() {
    }

    /// 为内容根安装守卫；玻璃开关读取 [M3Settings#isGlassEnabled()]。
    ///
    /// @param content 内容根
    static void install(@NotNull Region content) {
        install(content, M3Settings::isGlassEnabled);
    }

    /// 为内容根安装守卫，并使用指定的玻璃开关判定（测试注入用）。
    ///
    /// @param content      内容根
    /// @param glassEnabled 玻璃开关判定
    static void install(@NotNull Region content, @NotNull BooleanSupplier glassEnabled) {
        uninstall(content);
        Guard guard = new Guard(content, glassEnabled);
        content.getProperties().put(GUARD_KEY, guard);
        guard.start();
    }

    /// 移除内容根上的守卫；未安装时无副作用。
    ///
    /// @param content 内容根
    static void uninstall(@NotNull Region content) {
        if (content.getProperties().remove(GUARD_KEY) instanceof Guard guard) {
            guard.dispose();
        }
    }

    /// 立即按当前玻璃开关重新评估一次（主题切换后或测试中使用）。
    ///
    /// @param content 内容根
    static void refresh(@NotNull Region content) {
        if (content.getProperties().get(GUARD_KEY) instanceof Guard guard) {
            guard.sync();
        }
    }

    /// 判断背景是否没有任何可见填充。
    ///
    /// `null`、没有填充、全部填充都是完全透明的颜色时返回 `true`；渐变、图片
    /// 等无法廉价判定为透明的画刷按“可见”处理，避免漏挡实色表面。
    ///
    /// @param background 背景，可为 `null`
    /// @return 是否完全透明
    static boolean isTransparent(@Nullable Background background) {
        if (background == null) {
            return true;
        }
        for (BackgroundFill fill : background.getFills()) {
            Paint paint = fill.getFill();
            if (paint instanceof Color color) {
                if (color.getOpacity() > 0) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    /// 一张内容根上的守卫实例。
    ///
    /// 监听内容根自身的背景变化，以及祖先链的结构/样式类变化：主题切换会重建
    /// `M3Host` 并把 shell 重新挂到新的祖先下，只有重建祖先监听才能及时恢复。
    private static final class Guard {

        /// 被守卫的内容根。
        private final Region content;

        /// 玻璃开关判定。
        private final BooleanSupplier glassEnabled;

        /// 内容根背景变化时重新评估。
        private final InvalidationListener backgroundListener = observable -> sync();

        /// 内容根父节点或场景变化时重建祖先监听。
        private final InvalidationListener structureListener = observable -> rebind();

        /// 当前注册在祖先链上的监听。
        private final List<Watch> watches = new ArrayList<>();

        /// 被守卫挡下的实色背景；玻璃关闭后用于恢复。
        private @Nullable Background blocked;

        /// 正在同步时忽略重入通知。
        private boolean syncing;

        /// 是否已释放，释放后不再重建监听。
        private boolean disposed;

        /// 创建守卫。
        ///
        /// @param content      内容根
        /// @param glassEnabled 玻璃开关判定
        private Guard(@NotNull Region content, @NotNull BooleanSupplier glassEnabled) {
            this.content = content;
            this.glassEnabled = glassEnabled;
        }

        /// 注册监听并立即评估一次。
        private void start() {
            content.backgroundProperty().addListener(backgroundListener);
            content.parentProperty().addListener(structureListener);
            content.sceneProperty().addListener(structureListener);
            rebind();
        }

        /// 移除全部监听，并把被挡下的实色背景恢复回去。
        private void dispose() {
            disposed = true;
            content.backgroundProperty().removeListener(backgroundListener);
            content.parentProperty().removeListener(structureListener);
            content.sceneProperty().removeListener(structureListener);
            unwatch();
            restore();
        }

        /// 重建祖先链监听：主题切换、页面挂载都会改变这条链。
        private void rebind() {
            if (disposed) {
                return;
            }
            unwatch();
            Parent parent = content.getParent();
            while (parent != null) {
                watches.add(new Watch(this, parent));
                parent = parent.getParent();
            }
            sync();
        }

        /// 移除祖先链监听。
        private void unwatch() {
            for (Watch watch : watches) {
                watch.remove();
            }
            watches.clear();
        }

        /// 按当前玻璃开关评估并修正背景。
        private void sync() {
            if (syncing || disposed) {
                return;
            }
            syncing = true;
            try {
                if (glassEnabled.getAsBoolean()) {
                    Background background = content.getBackground();
                    if (background != null && !isTransparent(background)) {
                        blocked = background;
                        content.setBackground(Background.EMPTY);
                    }
                } else {
                    restore();
                }
            } finally {
                syncing = false;
            }
        }

        /// 玻璃关闭时把被挡下的实色背景还回去。
        private void restore() {
            Background background = blocked;
            if (background != null && isTransparent(content.getBackground())) {
                content.setBackground(background);
            }
            blocked = null;
        }
    }

    /// 一个祖先节点上注册的弱监听。
    ///
    /// 真正的监听器由本对象强引用：`WeakInvalidationListener` 与
    /// `WeakListChangeListener` 只弱引用目标，若这里不持有，弱包装会立即失效。
    private static final class Watch {

        /// 被监听的祖先节点。
        private final Parent parent;

        /// 祖先父节点监听；重新挂载 shell 时触发祖先链重建。
        private final InvalidationListener parentListener;

        /// 祖先样式类监听；`lmcl-m3-glass` 增删时重新评估。
        private final ListChangeListener<String> styleClassListener;

        /// 注册到祖先上的父节点弱监听。
        private final InvalidationListener weakParentListener;

        /// 注册到祖先上的样式类弱监听。
        private final ListChangeListener<String> weakStyleClassListener;

        /// 在祖先节点上注册弱监听。
        ///
        /// @param guard  所属守卫
        /// @param parent 祖先节点
        private Watch(@NotNull Guard guard, @NotNull Parent parent) {
            this.parent = parent;
            this.parentListener = observable -> guard.rebind();
            this.styleClassListener = change -> guard.sync();
            this.weakParentListener = new WeakInvalidationListener(parentListener);
            this.weakStyleClassListener = new WeakListChangeListener<>(styleClassListener);
            parent.parentProperty().addListener(weakParentListener);
            parent.getStyleClass().addListener(weakStyleClassListener);
        }

        /// 从祖先节点上移除监听。
        private void remove() {
            parent.parentProperty().removeListener(weakParentListener);
            parent.getStyleClass().removeListener(weakStyleClassListener);
        }
    }
}
