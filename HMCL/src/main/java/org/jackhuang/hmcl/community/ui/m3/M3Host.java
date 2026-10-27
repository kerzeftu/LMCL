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
 * Modified by LMCL team on 2026-10-23: M3 theme host for the LMCL shell.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.beans.InvalidationListener;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.setting.FontManager;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// 承载 LMCL 壳层并安装 Material 3 主题的容器。
///
/// 主题只作用于本节点的子树，不接管 `Scene`；颜色跟随 [M3DynamicColors]，
/// 字体跟随 [FontManager]。强调色、壁纸或字体变化时重新安装，避免用户切换后
/// 必须重启。
///
/// 线程：构造与 [dispose] 必须在 JavaFX 应用线程调用。
@NotNullByDefault
public final class M3Host extends StackPane {

    /// 主题色变化监听。
    private final InvalidationListener themeListener = observable -> refresh();

    /// 字体变化监听。
    private final InvalidationListener fontListener = observable -> refresh();

    /// 创建一个空宿主。
    public M3Host() {
        this(null);
    }

    /// 创建宿主并放入内容。
    ///
    /// @param content 内容节点，可为 `null`
    public M3Host(@Nullable Node content) {
        getStyleClass().addAll("lmcl-m3-host", "lmcl-m3");
        if (M3Settings.isGlassEnabled()) {
            getStyleClass().add("lmcl-m3-glass");
        }
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setContent(content);
        CommunityM3.install(this);
        M3MotionSupport.install();
        GlassBackdrop.get().attach(this);
        M3DynamicColors.get().schemeProperty().addListener(themeListener);
        FontManager.fontProperty().addListener(fontListener);
    }

    /// 替换内容；`null` 表示清空。
    ///
    /// @param content 新内容
    public void setContent(@Nullable Node content) {
        if (content == null) {
            getChildren().clear();
        } else {
            getChildren().setAll(content);
        }
    }

    /// 主题色或字体变化后重新安装主题。
    private void refresh() {
        CommunityM3.refresh(this);
    }

    /// 移除监听并卸载主题；重复调用无副作用。
    public void dispose() {
        GlassBackdrop.get().detach();
        M3DynamicColors.get().schemeProperty().removeListener(themeListener);
        FontManager.fontProperty().removeListener(fontListener);
        CommunityM3.uninstall(this);
    }

    /// 返回当前内容节点；空宿主返回 `null`。
    ///
    /// @return 内容节点或 `null`
    @Nullable
    public Node getContent() {
        return getChildren().isEmpty() ? null : getChildren().get(0);
    }

    /// 仅供调试：返回当前是否已安装 M3 主题。
    ///
    /// @return 是否已安装
    public boolean isM3Installed() {
        return CommunityM3.isInstalled(this);
    }
}
