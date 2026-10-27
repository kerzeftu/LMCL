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

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.ui.RoundedHitArea;
import org.jackhuang.hmcl.community.ui.m3.GlassSurface;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.DialogPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.ui.FXUtils.newBorderButton;
import static org.jackhuang.hmcl.ui.FXUtils.newRaisedButton;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 首页内置卡片的共用小工具：紧凑文字、按钮与对话框骨架。
///
/// 这些方法只做节点拼装，不读磁盘、不启动后台任务；调用方负责线程与生命周期。
@NotNullByDefault
final class BuiltinWidgets {

    /// 卡内小字最多显示的行数之外的字符会被省略。
    private static final int SHORT_TEXT_LIMIT = 48;

    /// 工具类，不允许实例化。
    private BuiltinWidgets() {
    }

    /// 创建单行省略的小字标签。
    ///
    /// @param text 文案
    /// @return 标签
    @NotNull
    static Label caption(@NotNull String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll("subtitle-label", "community-label");
        label.setWrapText(false);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinWidth(0);
        return label;
    }

    /// 创建单行省略的小字标签。
    ///
    /// @param key i18n key
    /// @param args 格式化参数
    /// @return 标签
    @NotNull
    static Label caption(@NotNull String key, Object... args) {
        return caption(i18n(key, args));
    }

    /// 创建可换行的正文标签。
    ///
    /// @param text 文案
    /// @return 标签
    @NotNull
    static Label body(@NotNull String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("community-body");
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinWidth(0);
        return label;
    }

    /// 创建可换行的正文标签。
    ///
    /// @param key i18n key
    /// @param args 格式化参数
    /// @return 标签
    @NotNull
    static Label body(@NotNull String key, Object... args) {
        return body(i18n(key, args));
    }

    /// 创建边框按钮。
    ///
    /// @param text 按钮文字
    /// @param action 点击动作
    /// @return 按钮
    @NotNull
    static JFXButton borderButton(@NotNull String text, @NotNull Runnable action) {
        JFXButton button = newBorderButton(text);
        button.setOnAction(event -> action.run());
        RoundedHitArea.install(button);
        return button;
    }

    /// 创建实心按钮。
    ///
    /// @param text 按钮文字
    /// @param action 点击动作
    /// @return 按钮
    @NotNull
    static JFXButton raisedButton(@NotNull String text, @NotNull Runnable action) {
        JFXButton button = newRaisedButton(text);
        button.setOnAction(event -> action.run());
        RoundedHitArea.install(button);
        return button;
    }

    /// 创建水平方向的可伸缩空白。
    ///
    /// @return 空白节点
    @NotNull
    static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /// 用一块双层磨砂材质包裹卡片内容。
    ///
    /// `GlassSurface` 始终铺满 wrapper 作为背景；快照可用时绘制两层模糊壁纸，
    /// 不可用时显示统一半透明兜底。内容自身的 `.card` 只在 M3 玻璃开启时透明，
    /// 因此 Classic 与关闭玻璃模式仍保留原来的实色卡片。
    ///
    /// @param content   卡片节点
    /// @param arc       圆角直径
    /// @param debugName 诊断名称，可为 `null`
    /// @return 材质包装节点
    @NotNull
    static StackPane materialCard(@NotNull Node content, double arc, @Nullable String debugName) {
        GlassSurface glass = new GlassSurface(arc, GlassSurface.Kind.CARD);
        glass.setDebugName(debugName);
        glass.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        StackPane wrapper = new StackPane(glass, content);
        wrapper.getStyleClass().add("community-material-card");
        wrapper.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        wrapper.setMinHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(glass, Pos.TOP_LEFT);
        StackPane.setAlignment(content, Pos.TOP_LEFT);
        if (content instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return wrapper;
    }

    /// 用一块双层磨砂材质包裹卡片内容。
    ///
    /// @param content 卡片节点
    /// @param arc     圆角直径
    /// @return 材质包装节点
    @NotNull
    static StackPane materialCard(@NotNull Node content, double arc) {
        return materialCard(content, arc, null);
    }

    /// 创建符合上游 `DialogPane` 视觉的对话框骨架。
    ///
    /// 标题、进度条与动作栏由上游 `DialogPane` 提供；调用方把业务按钮放进
    /// content 底部，保持现有交互不变。默认的确定/取消动作被移除，避免重复。
    ///
    /// @param title 标题
    /// @param content 内容节点
    /// @param width 对话框宽度
    /// @return 对话框
    @NotNull
    static DialogPane dialogRoot(@NotNull String title, @NotNull Node content, double width) {
        DialogPane root = new DialogPane() {
            @Override
            protected void onAccept() {
                fireEvent(new DialogCloseEvent());
            }

            @Override
            protected void onCancel() {
                fireEvent(new DialogCloseEvent());
            }
        };
        root.setTitle(title);
        root.setBody(content);
        root.setActions();
        root.setPrefWidth(width);
        root.setMinWidth(Math.min(width, 480));
        root.setMaxWidth(width);
        return root;
    }

    /// 创建右对齐的对话框按钮行。
    ///
    /// @param buttons 按钮
    /// @return 按钮行
    @NotNull
    static HBox dialogActions(@NotNull Node... buttons) {
        HBox box = new HBox(8, buttons);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    /// 创建首页卡片底部的操作行。
    ///
    /// 规则（m19511）：一个按钮靠右；恰好两个按钮时一左一右，中间由可伸缩
    /// 空白撑开；三个及以上靠右成组。调用方只传按钮，状态/提示文字应放在
    /// body 中，避免抢占左右位置。
    ///
    /// @param buttons 按钮
    /// @return 底部操作行
    @NotNull
    static HBox cardFooter(@NotNull Node... buttons) {
        HBox footer = new HBox(8);
        footer.getStyleClass().add("community-card-actions");
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setFillHeight(true);
        if (buttons.length == 2) {
            footer.getChildren().addAll(buttons[0], spacer(), buttons[1]);
        } else {
            footer.getChildren().add(spacer());
            footer.getChildren().addAll(buttons);
        }
        return footer;
    }

    /// 把卡片正文与底部操作行组合成“正文可增长、footer 贴底”的结构。
    ///
    /// 卡片高度由自身 `prefHeight` 决定；父容器额外给高度时，正文容器吃掉
    /// 多余空间，footer 保持在卡片底部。
    ///
    /// @param body 正文
    /// @param footer 底部操作行
    /// @return 卡片内容根
    @NotNull
    static VBox cardContent(@NotNull Node body, @NotNull Node footer) {
        VBox bodyBox = new VBox(body);
        bodyBox.setFillWidth(true);
        bodyBox.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(bodyBox, Priority.ALWAYS);

        VBox content = new VBox(6, bodyBox, footer);
        content.setFillWidth(true);
        return content;
    }

    /// 截断过长的实例 id，保留可辨识前缀。
    ///
    /// @param text 原文
    /// @param max 最大字符数（含省略号）
    /// @return 截断后的文本
    @NotNull
    static String shorten(@NotNull String text, int max) {
        if (max <= 0 || text.length() <= max) {
            return text;
        }
        if (max == 1) {
            return "…";
        }
        return text.substring(0, max - 1) + "…";
    }

    /// 截断卡内小字，避免 1×1 卡片出现横向溢出。
    ///
    /// @param text 原文
    /// @return 截断后的文本
    @NotNull
    static String shortText(@NotNull String text) {
        return shorten(text, SHORT_TEXT_LIMIT);
    }

    /// 读取实例对应的游戏版本；缺失时回退到实例 id。
    ///
    /// @param repository 实例仓库
    /// @param version 实例
    /// @return 展示用版本号
    @NotNull
    static String gameVersion(@NotNull HMCLGameRepository repository, @NotNull Version version) {
        try {
            return repository.getGameVersion(version).orElse(version.getId());
        } catch (Throwable e) {
            return version.getId();
        }
    }
}
