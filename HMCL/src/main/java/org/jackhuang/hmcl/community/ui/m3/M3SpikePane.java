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
 * Modified by LMCL team on 2026-10-22: Material 3 Expressive technical spike.
 */
package org.jackhuang.hmcl.community.ui.m3;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3Card;
import org.glavo.m3fx.controls.M3CardVariant;
import org.glavo.m3fx.controls.M3LoadingIndicator;
import org.glavo.m3fx.controls.M3ProgressBar;
import org.glavo.m3fx.controls.M3ProgressIndicator;
import org.glavo.m3fx.controls.M3Slider;
import org.glavo.m3fx.controls.M3Switch;
import org.glavo.m3fx.controls.M3TextField;
import org.glavo.m3fx.controls.M3Text;
import org.glavo.m3fx.controls.M3TextRole;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.DialogPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

/// Material 3 Expressive 阶段 0 技术验证面板。
///
/// 只用于 `-Dlmcl.m3.spike=true` 时的开发调试入口，不进入正式导航：
/// 展示按钮、卡片、Wavy 进度条、加载指示器、输入控件和主题桥接效果。
/// 正式视觉改造从阶段 1 开始，本面板不应被产品代码引用。
@NotNullByDefault
public final class M3SpikePane extends VBox {

    /// 操作反馈文本。
    private final Label actionStatus = new Label("点击上方按钮查看状态层");

    /// 创建验证面板；必须在 JavaFX 线程调用。
    public M3SpikePane() {
        getStyleClass().add("community-m3-spike");
        setSpacing(18);
        setPadding(new Insets(20));
        setMaxWidth(960);

        CommunityM3.install(this);

        actionStatus.getStyleClass().add("community-m3-spike-status");

        getChildren().addAll(
                new M3Text("Material 3 Expressive spike", M3TextRole.HEADLINE_MEDIUM),
                new M3Text("m3fx 2481f88fd6 · JavaFX 21.0.8 · 阶段 0 仅验证", M3TextRole.BODY_MEDIUM),
                buildButtons(),
                buildCards(),
                buildProgress(),
                buildInputs(),
                actionStatus);
    }

    /// 打开验证对话框；可从任意线程调用。
    public static void open() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(M3SpikePane::open);
            return;
        }

        M3SpikePane spike = new M3SpikePane();
        ScrollPane scroll = new ScrollPane(spike);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(640);

        DialogPane dialog = new DialogPane() {
            @Override
            protected void onAccept() {
                fireEvent(new DialogCloseEvent());
            }

            @Override
            protected void onCancel() {
                fireEvent(new DialogCloseEvent());
            }
        };
        dialog.setTitle("Material 3 Expressive spike");
        dialog.setBody(scroll);
        dialog.setPrefWidth(1000);
        dialog.setMaxHeight(780);
        Controllers.dialog(dialog);
    }

    /// 五种按钮形态。
    @NotNull
    private Node buildButtons() {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        for (M3ButtonVariant variant : M3ButtonVariant.values()) {
            M3Button button = new M3Button(variant.name(), variant);
            button.setOnAction(event -> actionStatus.setText("clicked " + variant.name()));
            row.getChildren().add(button);
        }
        return section("Button variants", row);
    }

    /// 三种卡片形态。
    @NotNull
    private Node buildCards() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        for (M3CardVariant variant : M3CardVariant.values()) {
            Label label = new Label(variant.name());
            label.setPadding(new Insets(16));
            M3Card card = new M3Card(label, variant);
            card.setPrefWidth(220);
            card.setPrefHeight(96);
            row.getChildren().add(card);
        }
        return section("Card variants", row);
    }

    /// Wavy/Flat/Indeterminate 进度与加载指示器。
    @NotNull
    private Node buildProgress() {
        VBox column = new VBox(14);
        column.getChildren().addAll(
                progressRow("Flat (amplitude 0)", 0.0),
                progressRow("Expressive (amplitude 2.5)", 2.5),
                progressRow("Strong (amplitude 4.0)", 4.0));

        M3ProgressBar indeterminate = new M3ProgressBar(ProgressIndicator.INDETERMINATE_PROGRESS);
        indeterminate.setPrefWidth(360);
        indeterminate.setTrackThickness(4);
        indeterminate.setTrackGap(4);
        indeterminate.setWaveAmplitude(2.5);
        indeterminate.setWavelength(40);
        column.getChildren().add(progressLine("Indeterminate", indeterminate));

        HBox indicators = new HBox(24);
        indicators.setAlignment(Pos.CENTER_LEFT);
        M3LoadingIndicator loading = new M3LoadingIndicator();
        loading.setContainerSize(48);
        loading.setIndicatorSize(24);
        M3ProgressIndicator circular = new M3ProgressIndicator(0.7);
        circular.setPrefSize(48, 48);
        indicators.getChildren().addAll(loading, circular);
        column.getChildren().add(progressLine("Loading indicators", indicators));

        return section("Progress", column);
    }

    /// 生成一条确定进度条。
    @NotNull
    private Node progressRow(@NotNull String title, double amplitude) {
        M3ProgressBar bar = new M3ProgressBar(0.65);
        bar.setPrefWidth(360);
        bar.setTrackThickness(4);
        bar.setTrackGap(4);
        bar.setStopSize(4);
        bar.setWaveAmplitude(amplitude);
        bar.setWavelength(40);
        return progressLine(title, bar);
    }

    /// 标题 + 控件的单行布局。
    @NotNull
    private Node progressLine(@NotNull String title, @NotNull Node control) {
        Label label = new Label(title);
        label.setMinWidth(220);
        HBox line = new HBox(12, label, control);
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    /// 输入控件。
    @NotNull
    private Node buildInputs() {
        M3Switch toggle = new M3Switch("Switch");
        toggle.setSelected(true);

        M3Slider slider = new M3Slider(0, 100, 40);
        slider.setPrefWidth(220);

        M3TextField textField = new M3TextField("Text field");
        textField.setPrefWidth(220);

        HBox row = new HBox(24, toggle, slider, textField);
        row.setAlignment(Pos.CENTER_LEFT);
        return section("Inputs", row);
    }

    /// 分组标题 + 内容。
    @NotNull
    private Node section(@NotNull String title, @NotNull Node content) {
        M3Text heading = new M3Text(title, M3TextRole.TITLE_MEDIUM);
        VBox box = new VBox(10, heading, content);
        box.setPadding(new Insets(4, 0, 2, 0));
        VBox.setVgrow(content, Priority.NEVER);
        return box;
    }
}
