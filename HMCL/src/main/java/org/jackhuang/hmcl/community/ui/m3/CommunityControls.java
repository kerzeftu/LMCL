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
 * Modified by LMCL team on 2026-10-23: M3 control factories.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3Card;
import org.glavo.m3fx.controls.M3CardVariant;
import org.glavo.m3fx.controls.M3ListItem;
import org.glavo.m3fx.controls.M3TextInputVariant;
import org.glavo.m3fx.controls.M3TextField;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// LMCL 业务代码创建 Material 3 控件的薄封装。
///
/// 业务代码尽量只依赖本类，不直接依赖 m3fx 的具体控件类型；后续替换/升级
/// m3fx 时只需改这里。所有方法必须在 JavaFX 应用线程调用。
@NotNullByDefault
public final class CommunityControls {

    /// 工具类，不允许实例化。
    private CommunityControls() {
    }

    /// 创建填充按钮。
    ///
    /// @param text 文本
    /// @param action 点击处理器，可为 `null`
    /// @return 按钮
    public static M3Button filledButton(String text, @Nullable EventHandler<ActionEvent> action) {
        return button(text, M3ButtonVariant.FILLED, action);
    }

    /// 创建 tonal 按钮。
    ///
    /// @param text 文本
    /// @param action 点击处理器，可为 `null`
    /// @return 按钮
    public static M3Button tonalButton(String text, @Nullable EventHandler<ActionEvent> action) {
        return button(text, M3ButtonVariant.TONAL, action);
    }

    /// 创建描边按钮。
    ///
    /// @param text 文本
    /// @param action 点击处理器，可为 `null`
    /// @return 按钮
    public static M3Button outlinedButton(String text, @Nullable EventHandler<ActionEvent> action) {
        return button(text, M3ButtonVariant.OUTLINED, action);
    }

    /// 创建文字按钮。
    ///
    /// @param text 文本
    /// @param action 点击处理器，可为 `null`
    /// @return 按钮
    public static M3Button textButton(String text, @Nullable EventHandler<ActionEvent> action) {
        return button(text, M3ButtonVariant.TEXT, action);
    }

    /// 创建指定变体的按钮。
    ///
    /// @param text 文本
    /// @param variant 变体
    /// @param action 点击处理器，可为 `null`
    /// @return 按钮
    public static M3Button button(
            String text,
            M3ButtonVariant variant,
            @Nullable EventHandler<ActionEvent> action) {
        M3Button button = new M3Button(text, variant);
        if (action != null) {
            button.setOnAction(action);
        }
        return button;
    }

    /// 创建填充卡片。
    ///
    /// @param content 内容
    /// @return 卡片
    public static M3Card filledCard(Node content) {
        return new M3Card(content, M3CardVariant.FILLED);
    }

    /// 创建带描边的卡片。
    ///
    /// @param content 内容
    /// @return 卡片
    public static M3Card outlinedCard(Node content) {
        return new M3Card(content, M3CardVariant.OUTLINED);
    }

    /// 创建两行列表项。
    ///
    /// @param headline 标题
    /// @param supporting 说明
    /// @return 列表项
    public static M3ListItem listItem(String headline, String supporting) {
        M3ListItem item = new M3ListItem(headline);
        item.setSupportingText(supporting);
        return item;
    }

    /// 创建描边输入框。
    ///
    /// @param prompt 占位文本
    /// @return 输入框
    public static M3TextField textField(String prompt) {
        M3TextField field = new M3TextField();
        field.setVariant(M3TextInputVariant.OUTLINED);
        field.setPromptText(prompt);
        return field;
    }
}
