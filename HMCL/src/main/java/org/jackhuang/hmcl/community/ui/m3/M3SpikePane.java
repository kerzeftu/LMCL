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

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;
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
                new M3Text("m3fx 96ccd6d · JavaFX 21.0.8 · 阶段 0 仅验证", M3TextRole.BODY_MEDIUM),
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

    /// M3 Expressive 规范推荐的 wavy 进度条尺寸；桌面端 4 px 线宽与 2.5 px 振幅更接近上一版观感。
    private static final double TRACK_THICKNESS = 4;
    private static final double TRACK_GAP = 4;
    private static final double STOP_SIZE = 4;
    private static final double WAVELENGTH = 40;

    /// Wavy/Flat/Indeterminate 进度与加载指示器。
    @NotNull
    private Node buildProgress() {
        VBox column = new VBox(18);
        column.getChildren().addAll(
                progressRow("Wavy 65%", 0.65, 2.5),
                progressRow("Flat 65%", 0.65, 0.0));

        // m3fx 的确定进度条视觉可用，但它的 indeterminate 动画从 0.18 开始循环，
        // 每轮都会在左侧露出半截进度段，看起来像生硬地跳回前面。这里用连续
        // 传送带式波形替代：每个周期位移恰好一个轨道宽度，首尾图案相同。
        ContinuousWavyBar indeterminate = new ContinuousWavyBar();
        indeterminate.setPrefWidth(420);
        indeterminate.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(indeterminate, Priority.ALWAYS);
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
    private Node progressRow(@NotNull String title, double progress, double amplitude) {
        M3ProgressBar bar = new M3ProgressBar(progress);
        configure(bar);
        bar.setWaveAmplitude(amplitude);
        return progressLine(title, bar);
    }

    /// 应用统一的尺寸，避免默认 4 px 细线在桌面端显得像普通细进度条。
    private static void configure(@NotNull M3ProgressBar bar) {
        bar.setPrefWidth(420);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setTrackThickness(TRACK_THICKNESS);
        bar.setTrackGap(TRACK_GAP);
        bar.setStopSize(STOP_SIZE);
        bar.setWavelength(WAVELENGTH);
        HBox.setHgrow(bar, Priority.ALWAYS);
    }

    /// 标题 + 控件的单行布局。
    @NotNull
    private Node progressLine(@NotNull String title, @NotNull Node control) {
        Label label = new Label(title);
        label.setMinWidth(220);
        HBox line = new HBox(12, label, control);
        line.setAlignment(Pos.CENTER_LEFT);
        line.setMaxWidth(Double.MAX_VALUE);
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

    /// 连续波形的 indeterminate 进度条。
    ///
    /// 用「轨道宽度的整数倍」作为循环位移：每轮结束时所有活动段正好平移
    /// 一整条轨道，首尾图案完全一致，因此不会在左侧突然跳出一截。
    /// 活动段按 0.58 个轨道宽、间隔 1 个轨道宽排布，始终有一段从左进入、
    /// 另一段从右离开。
    private static final class ContinuousWavyBar extends Region {
        private static final double SEGMENT_RATIO = 0.58;
        private static final double AMPLITUDE = 2.5;
        private static final double WAVE_CYCLES = 2;
        private static final Duration CYCLE = Duration.millis(2200);

        private final Rectangle track = new Rectangle();
        private final Rectangle clip = new Rectangle();
        private final Path wave = new Path();
        private final DoubleProperty offset = new SimpleDoubleProperty(0);
        private final Timeline timeline;

        private ContinuousWavyBar() {
            getStyleClass().add("community-wavy-spike");
            track.getStyleClass().add("track");
            wave.getStyleClass().add("wave");
            wave.setFill(null);
            wave.setStrokeLineCap(StrokeLineCap.ROUND);

            getChildren().addAll(track, wave);
            setClip(clip);
            setPrefHeight(TRACK_THICKNESS + AMPLITUDE * 2 + 4);
            setMinHeight(TRACK_THICKNESS + AMPLITUDE * 2 + 4);

            offset.addListener((observable, oldValue, newValue) -> rebuild());
            timeline = new Timeline(new KeyFrame(CYCLE, new KeyValue(offset, 1.0, Interpolator.LINEAR)));
            timeline.setCycleCount(Animation.INDEFINITE);
            sceneProperty().addListener((observable, oldScene, newScene) -> {
                if (newScene == null) {
                    timeline.stop();
                } else {
                    timeline.play();
                }
            });
        }

        @Override
        protected void layoutChildren() {
            double width = getWidth();
            double height = getHeight();
            double centerY = height / 2;

            track.setX(0);
            track.setY(centerY - TRACK_THICKNESS / 2);
            track.setWidth(width);
            track.setHeight(TRACK_THICKNESS);
            track.setArcWidth(TRACK_THICKNESS);
            track.setArcHeight(TRACK_THICKNESS);

            double clipHeight = TRACK_THICKNESS + AMPLITUDE * 2;
            clip.setX(0);
            clip.setY(centerY - clipHeight / 2);
            clip.setWidth(width);
            clip.setHeight(clipHeight);
            clip.setArcWidth(TRACK_THICKNESS);
            clip.setArcHeight(TRACK_THICKNESS);

            rebuild();
        }

        private void rebuild() {
            wave.getElements().clear();
            double width = getWidth();
            if (width <= 0) {
                return;
            }

            double centerY = getHeight() / 2;
            double segment = Math.max(Math.min(width, 48), width * SEGMENT_RATIO);
            double patternOffset = offset.get() * width;
            // 让波形自身在垂直方向连续起伏；每个循环走整数个波，重置时相位相同。
            double wavePhase = offset.get() * 2 * Math.PI * WAVE_CYCLES;

            for (int k = -2; k <= 3; k++) {
                double segmentStart = -segment + patternOffset + k * width;
                if (segmentStart > width || segmentStart + segment < 0) {
                    continue;
                }

                int steps = Math.max(2, (int) Math.ceil(segment / 2));
                for (int i = 0; i <= steps; i++) {
                    double localX = segment * i / steps;
                    double x = segmentStart + localX;
                    double y = centerY + Math.sin(2 * Math.PI * localX / WAVELENGTH + wavePhase) * AMPLITUDE;
                    if (i == 0) {
                        wave.getElements().add(new MoveTo(x, y));
                    } else {
                        wave.getElements().add(new LineTo(x, y));
                    }
                }
            }
            wave.setStrokeWidth(TRACK_THICKNESS);
        }
    }
}
