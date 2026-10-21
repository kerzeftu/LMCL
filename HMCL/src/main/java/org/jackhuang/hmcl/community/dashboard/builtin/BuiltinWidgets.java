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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

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

    /// 创建对话框根节点：标题行 + 关闭按钮 + 内容。
    ///
    /// 调用方应使用 `Controllers.dialog(root)` 展示；关闭按钮会触发
    /// [DialogCloseEvent]，由上游装饰器负责真正关闭。
    ///
    /// @param title 标题
    /// @param content 内容节点
    /// @param width 对话框宽度
    /// @return 根节点
    @NotNull
    static VBox dialogRoot(@NotNull String title, @NotNull Node content, double width) {
        VBox root = new VBox(10);
        root.getStyleClass().add("jfx-dialog-layout");
        root.setPadding(new Insets(10));
        root.setPrefWidth(width);
        root.setMinWidth(Math.min(width, 480));
        root.setMaxWidth(width);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().addAll("title-label", "community-title");

        JFXButton close = new JFXButton("✕");
        close.getStyleClass().add("dialog-cancel");
        close.setOnAction(event -> root.fireEvent(new DialogCloseEvent()));

        HBox header = new HBox(8, titleLabel, spacer(), close);
        header.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(header, content);
        VBox.setVgrow(content, Priority.ALWAYS);
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
