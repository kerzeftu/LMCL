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
package org.jackhuang.hmcl.community.jvm;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.util.platform.Bits;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// JVM 参数预设向导（首页 `java-recommend` 卡片的「JVM 参数」tab 内容）。
///
/// 无参构造；由卡片调用 [#setRepository(HMCLGameRepository)] 注入当前仓库，
/// 面板只在用户主动操作时写设置，不自动修改任何实例。
///
/// 行为约定：
/// - 默认档位是 [#JvmPreset#UPSTREAM]，不写参数；
/// - 所有写入经 [JvmArgWriter]，面板不直接 `setValue`；
/// - 打开时若 `jvmOptions` 与上次生成值不一致，切到“自定义”并提示，不覆盖；
/// - `noJVMOptions=true` 或实例设置只读时禁用应用按钮并说明原因。
@NotNullByDefault
public final class JvmPresetWizard extends VBox {

    /// 实例下拉。
    private final ComboBox<String> instanceBox = new ComboBox<>();

    /// 档位单选组。
    private final ToggleGroup modeGroup = new ToggleGroup();

    /// 四个档位单选按钮。
    private final RadioButton upstreamButton = new RadioButton(i18n(JvmPreset.UPSTREAM.i18nKey()));
    private final RadioButton g1Button = new RadioButton(i18n(JvmPreset.G1.i18nKey()));
    private final RadioButton zgcButton = new RadioButton(i18n(JvmPreset.ZGC.i18nKey()));
    private final RadioButton customButton = new RadioButton(i18n(JvmPreset.CUSTOM.i18nKey()));

    /// 堆大小输入。
    private final TextField heapField = new TextField();

    /// JEP 346 高级选项。
    private final CheckBox memoryOptInBox = new CheckBox(i18n("community.jvm.memory_optin"));

    /// 实例 Java 信息。
    private final Label javaLabel = new Label();

    /// 参数预览（生成值）。
    private final Label previewLabel = new Label();

    /// 状态/提示行。
    private final Label statusLabel = new Label();

    /// 底部固定说明。
    private final Label footerLabel = new Label(i18n("community.jvm.note.fixed"));

    /// 冲突容器。
    private final VBox conflictBox = new VBox(4);

    /// 操作按钮。
    private final Button applyButton = new Button(i18n("community.jvm.action.apply"));
    private final Button clearButton = new Button(i18n("community.jvm.action.clear"));
    private final Button refreshButton = new Button(i18n("community.jvm.action.refresh"));

    /// 当前仓库，可为 `null`。
    private @Nullable HMCLGameRepository repository;

    /// 当前实例 id，可为 `null`。
    private @Nullable String currentInstanceId;

    /// 上次向导生成值，可为 `null`。
    private @Nullable String previousGenerated;

    /// 当前档位。
    private JvmPreset currentMode = JvmPreset.UPSTREAM;

    /// 当前实例的 Java 大版本；`<= 0` 表示未知。
    private int javaMajor;

    /// 当前实例的 Java 位宽。
    private Bits javaBits = Bits.UNKNOWN;

    /// 防重入标记。
    private boolean updating;

    /// 构造向导（不加载数据）。
    public JvmPresetWizard() {
        setSpacing(10);
        setPadding(new Insets(12));
        getStyleClass().add("jvm-preset-wizard");

        Label title = new Label(i18n("community.jvm.title"));
        title.getStyleClass().add("title-label");

        instanceBox.setPrefWidth(240);
        instanceBox.setOnAction(e -> {
            if (!updating) {
                loadSelectedInstance();
            }
        });

        HBox instanceRow = new HBox(8, new Label(i18n("community.jvm.instance")), instanceBox, javaLabel);
        instanceRow.setAlignment(Pos.CENTER_LEFT);

        upstreamButton.setToggleGroup(modeGroup);
        g1Button.setToggleGroup(modeGroup);
        zgcButton.setToggleGroup(modeGroup);
        customButton.setToggleGroup(modeGroup);
        upstreamButton.setSelected(true);
        modeGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (updating) {
                return;
            }
            currentMode = selectedMode();
            refreshDerivedState();
        });

        HBox modeRow = new HBox(12, upstreamButton, g1Button, zgcButton, customButton);
        modeRow.setAlignment(Pos.CENTER_LEFT);

        heapField.setPrefWidth(100);
        heapField.setPromptText("4096");
        HBox heapRow = new HBox(8, new Label(i18n("community.jvm.heap")), heapField,
                new Label("MiB"), memoryOptInBox);
        heapRow.setAlignment(Pos.CENTER_LEFT);
        heapField.textProperty().addListener((obs, old, now) -> {
            if (!updating) {
                refreshDerivedState();
            }
        });
        memoryOptInBox.selectedProperty().addListener((obs, old, now) -> {
            if (!updating) {
                refreshDerivedState();
            }
        });

        previewLabel.setWrapText(true);
        previewLabel.setStyle("-fx-font-family: monospace;");
        VBox previewBox = new VBox(4, new Label(i18n("community.jvm.preview")), previewLabel);
        previewBox.getStyleClass().add("card");
        previewBox.setPadding(new Insets(8));

        conflictBox.setPadding(new Insets(4, 0, 0, 0));

        applyButton.setOnAction(e -> apply());
        clearButton.setOnAction(e -> clear());
        refreshButton.setOnAction(e -> refresh());
        HBox actionRow = new HBox(8, applyButton, clearButton, refreshButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel.setWrapText(true);
        footerLabel.setWrapText(true);
        footerLabel.getStyleClass().add("subtitle-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(
                title,
                instanceRow,
                modeRow,
                heapRow,
                previewBox,
                conflictBox,
                statusLabel,
                actionRow,
                footerLabel
        );
        refreshButtonState();
    }

    /// 注入实例仓库。
    ///
    /// @param repository 当前仓库；`null` 表示尚未就绪
    public void setRepository(@Nullable HMCLGameRepository repository) {
        this.repository = repository;
        rebuildInstances();
    }

    /// 重新读取实例列表与当前实例状态。
    public void refresh() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refresh);
            return;
        }
        rebuildInstances();
    }

    /// 是否已加载出至少一个实例。
    ///
    /// @return 是否可用
    public boolean isLoaded() {
        return !instanceBox.getItems().isEmpty();
    }

    /// 返回当前是否有仓库。
    ///
    /// @return 是否有仓库
    public boolean hasRepository() {
        return repository != null;
    }

    /// 重建实例下拉（保持当前选择）。
    private void rebuildInstances() {
        updating = true;
        try {
            String selected = instanceBox.getValue();
            instanceBox.getItems().clear();
            if (repository != null) {
                List<String> ids = new ArrayList<>();
                for (Version version : repository.getVersions()) {
                    if (!version.isHidden()) {
                        ids.add(version.getId());
                    }
                }
                ids.sort(String::compareToIgnoreCase);
                instanceBox.getItems().addAll(ids);
                if (selected != null && ids.contains(selected)) {
                    instanceBox.setValue(selected);
                } else if (!ids.isEmpty()) {
                    instanceBox.setValue(ids.get(0));
                }
            }
        } finally {
            updating = false;
        }
        loadSelectedInstance();
    }

    /// 加载当前选中实例的状态。
    private void loadSelectedInstance() {
        updating = true;
        try {
            currentInstanceId = instanceBox.getValue();
            previousGenerated = null;
            javaMajor = 0;
            javaBits = Bits.UNKNOWN;
            currentMode = JvmPreset.UPSTREAM;
            if (repository == null || currentInstanceId == null) {
                javaLabel.setText(i18n("community.jvm.no_instances"));
                statusLabel.setText("");
                conflictBox.getChildren().clear();
                previewLabel.setText("");
                refreshButtonState();
                return;
            }

            Version version = null;
            try {
                version = repository.getVersion(currentInstanceId);
            } catch (RuntimeException ignored) {
                // 实例可能在列表刷新后被删除，按“无实例”处理。
            }
            GameSettings.Instance setting = repository.getInstanceGameSettingsOrCreate(currentInstanceId);
            boolean readOnly = repository.isInstanceGameSettingsReadOnly(currentInstanceId);
            String current = setting == null ? null : setting.jvmOptionsProperty().getValue();

            JavaRuntime java = resolveJava(version);
            if (java != null) {
                javaMajor = java.getParsedVersion();
                javaBits = java.getBits();
                javaLabel.setText(i18n("community.jvm.java", java.getVersion()));
            } else {
                javaLabel.setText(i18n("community.jvm.java.unknown"));
            }

            JvmWizardStore.State state = JvmWizardStore.load(currentInstanceId);
            if (state != null) {
                previousGenerated = state.generated();
                int heap = state.heapMb();
                String normalizedCurrent = normalize(current);
                if (normalize(state.generated()).equals(normalizedCurrent)) {
                    selectMode(state.mode());
                    heapField.setText(Integer.toString(heap));
                } else if (normalizedCurrent.isEmpty() && state.mode() == JvmPreset.UPSTREAM) {
                    selectMode(JvmPreset.UPSTREAM);
                    heapField.setText(Integer.toString(heap));
                } else {
                    selectMode(JvmPreset.CUSTOM);
                    heapField.setText(Integer.toString(heap));
                    statusLabel.setText(i18n("community.jvm.manual_modified"));
                }
            } else {
                selectMode(JvmPreset.UPSTREAM);
                heapField.setText(Integer.toString(recommendedHeapMb()));
            }

            if (readOnly) {
                statusLabel.setText(i18n("community.jvm.readonly_instance"));
            }
        } finally {
            updating = false;
        }
        refreshDerivedState();
    }

    /// 解析实例适合的 Java（失败返回 `null`，不阻断面板）。
    ///
    /// @param version 实例版本，可为 `null`
    /// @return Java 运行时或 `null`
    @Nullable
    private JavaRuntime resolveJava(@Nullable Version version) {
        if (repository == null || version == null) {
            return null;
        }
        try {
            GameVersionNumber gameVersion = GameVersionNumber.asGameVersion(repository.getGameVersion(version));
            return JavaManager.findSuitableJava(gameVersion, version);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /// 刷新控件可用性、预览、冲突与备注。
    private void refreshDerivedState() {
        if (updating) {
            return;
        }
        boolean hasInstance = repository != null && currentInstanceId != null;
        JvmPreset mode = selectedMode();
        currentMode = mode;

        boolean needsHeap = mode == JvmPreset.G1 || mode == JvmPreset.ZGC;
        heapField.setDisable(!hasInstance || !needsHeap);
        memoryOptInBox.setVisible(mode == JvmPreset.G1);
        memoryOptInBox.setManaged(mode == JvmPreset.G1);
        memoryOptInBox.setDisable(!hasInstance || mode != JvmPreset.G1);

        String generated = generatedFor(mode);
        previewLabel.setText(generated.isBlank() ? i18n("community.jvm.preview.empty") : generated);

        List<JvmArgValidator.Conflict> conflicts = validateCandidate(mode);
        renderConflicts(conflicts);

        String note = noteFor(mode);
        if (!note.isBlank()) {
            statusLabel.setText(note);
        } else if (statusLabel.getText() != null && !statusLabel.getText().equals(i18n("community.jvm.manual_modified"))) {
            statusLabel.setText("");
        }

        refreshButtonState();
    }

    /// 刷新按钮可用性。
    private void refreshButtonState() {
        boolean hasInstance = repository != null && currentInstanceId != null;
        boolean readOnly = hasInstance && repository.isInstanceGameSettingsReadOnly(currentInstanceId);
        GameSettings.Instance setting = hasInstance ? repository.getInstanceGameSettingsOrCreate(currentInstanceId) : null;
        boolean noJvm = setting != null && Boolean.TRUE.equals(setting.noJVMOptionsProperty().getValue());

        applyButton.setDisable(!hasInstance || readOnly || noJvm);
        clearButton.setDisable(!hasInstance || readOnly || previousGenerated == null
                || previousGenerated.isBlank() || !isCurrentEqualsGenerated());
        refreshButton.setDisable(!hasInstance);
    }

    /// 当前 jvmOptions 是否与上次生成值一致。
    private boolean isCurrentEqualsGenerated() {
        if (repository == null || currentInstanceId == null || previousGenerated == null) {
            return false;
        }
        String current = JvmArgWriter.read(repository, currentInstanceId);
        return normalize(current).equals(normalize(previousGenerated));
    }

    /// 返回选中档位。
    @NotNull
    private JvmPreset selectedMode() {
        if (modeGroup.getSelectedToggle() == g1Button) {
            return JvmPreset.G1;
        }
        if (modeGroup.getSelectedToggle() == zgcButton) {
            return JvmPreset.ZGC;
        }
        if (modeGroup.getSelectedToggle() == customButton) {
            return JvmPreset.CUSTOM;
        }
        return JvmPreset.UPSTREAM;
    }

    /// 选择档位（不触发重入）。
    ///
    /// @param mode 档位
    private void selectMode(@NotNull JvmPreset mode) {
        switch (mode) {
            case G1 -> g1Button.setSelected(true);
            case ZGC -> zgcButton.setSelected(true);
            case CUSTOM -> customButton.setSelected(true);
            default -> upstreamButton.setSelected(true);
        }
        currentMode = mode;
    }

    /// 生成当前档位要写入的片段。
    ///
    /// @param mode 档位
    /// @return 参数串；不写入时为空串
    @NotNull
    private String generatedFor(@NotNull JvmPreset mode) {
        if (mode != JvmPreset.G1 && mode != JvmPreset.ZGC) {
            return "";
        }
        List<String> extra = extraArgs(mode);
        return JvmArgWriter.compose(mode, heapMbOrRecommended(), extra);
    }

    /// 生成 JEP 346 额外参数。
    ///
    /// @param mode 档位
    /// @return 参数列表；不适用时为空
    @NotNull
    private List<String> extraArgs(@NotNull JvmPreset mode) {
        if (mode != JvmPreset.G1 || !memoryOptInBox.isSelected()) {
            return List.of();
        }
        JvmMemoryOptions.GenerationResult result = JvmMemoryOptions.generate(javaMajor, "G1", true);
        return result.args();
    }

    /// 解析堆输入，非法时回退到推荐值。
    private int heapMbOrRecommended() {
        try {
            int value = Integer.parseInt(heapField.getText().trim());
            return value <= 0 ? recommendedHeapMb() : value;
        } catch (NumberFormatException e) {
            return recommendedHeapMb();
        }
    }

    /// 推荐堆大小：物理内存一半，夹在 1024–8192 MiB，512 MiB 对齐。
    private int recommendedHeapMb() {
        long physicalMb = SystemInfo.getTotalMemorySize() / (1024L * 1024L);
        if (physicalMb <= 0) {
            return JvmWizardStore.DEFAULT_HEAP_MB;
        }
        long recommended = Math.min(8192L, physicalMb / 2);
        recommended = Math.max(1024L, recommended);
        recommended = (recommended / 512L) * 512L;
        return (int) recommended;
    }

    /// 生成候选参数串并检测冲突。
    ///
    /// @param mode 档位
    /// @return 冲突列表
    @NotNull
    private List<JvmArgValidator.Conflict> validateCandidate(@NotNull JvmPreset mode) {
        if (repository == null || currentInstanceId == null) {
            return List.of();
        }
        String current = JvmArgWriter.read(repository, currentInstanceId);
        String candidate;
        if (mode == JvmPreset.G1 || mode == JvmPreset.ZGC) {
            candidate = JvmArgWriter.merge(current, previousGenerated, generatedFor(mode));
        } else {
            candidate = current == null ? "" : current;
        }
        return JvmArgValidator.validate(new JvmArgValidator.Input(
                candidate,
                mode,
                javaMajor,
                javaBits,
                SystemInfo.getTotalMemorySize()
        ));
    }

    /// 渲染冲突列表。
    ///
    /// @param conflicts 冲突列表
    private void renderConflicts(@NotNull List<JvmArgValidator.Conflict> conflicts) {
        conflictBox.getChildren().clear();
        if (conflicts.isEmpty()) {
            return;
        }
        Label header = new Label(i18n("community.jvm.conflict.title"));
        header.getStyleClass().add("title-label");
        conflictBox.getChildren().add(header);
        for (JvmArgValidator.Conflict conflict : conflicts) {
            Label severity = new Label(i18n("community.jvm.severity." + conflict.severity().name().toLowerCase(Locale.ROOT)));
            severity.getStyleClass().add("subtitle-label");
            Label message = new Label(i18n(conflict.messageKey()));
            message.setWrapText(true);
            HBox.setHgrow(message, Priority.ALWAYS);
            HBox row = new HBox(8, severity, message);
            row.setAlignment(Pos.CENTER_LEFT);
            if ("remove".equals(conflict.action()) && conflict.matchedArg() != null) {
                String arg = conflict.matchedArg();
                Button remove = new Button(i18n("community.jvm.conflict.action.remove"));
                remove.setOnAction(e -> {
                    if (repository != null && currentInstanceId != null
                            && JvmArgWriter.removeArg(repository, currentInstanceId, arg)) {
                        statusLabel.setText(i18n("community.jvm.status.removed"));
                        loadSelectedInstance();
                    }
                });
                row.getChildren().add(remove);
            }
            conflictBox.getChildren().add(row);
        }
    }

    /// 返回档位对应的说明文案。
    ///
    /// @param mode 档位
    /// @return 文案；无说明时为空串
    @NotNull
    private String noteFor(@NotNull JvmPreset mode) {
        if (repository == null || currentInstanceId == null) {
            return "";
        }
        GameSettings.Instance setting = repository.getInstanceGameSettingsOrCreate(currentInstanceId);
        boolean noJvm = setting != null && Boolean.TRUE.equals(setting.noJVMOptionsProperty().getValue());
        boolean noOptimizing = setting != null && Boolean.TRUE.equals(setting.noOptimizingJVMOptionsProperty().getValue());
        if (noJvm) {
            return i18n("community.jvm.note.no_jvm");
        }
        if (mode == JvmPreset.G1) {
            return noOptimizing ? i18n("community.jvm.note.no_optimizing") : i18n("community.jvm.note.upstream_g1");
        }
        if (mode == JvmPreset.ZGC) {
            return i18n("community.jvm.note.zgc_requires");
        }
        return "";
    }

    /// 应用当前档位。
    private void apply() {
        if (repository == null || currentInstanceId == null) {
            statusLabel.setText(i18n("community.jvm.error.no_instance"));
            return;
        }
        JvmPreset mode = selectedMode();
        if (mode == JvmPreset.CUSTOM) {
            statusLabel.setText(i18n("community.jvm.note.custom_readonly"));
            return;
        }
        JvmArgWriter.Result result = JvmArgWriter.apply(
                repository,
                currentInstanceId,
                mode,
                heapMbOrRecommended(),
                extraArgs(mode),
                previousGenerated
        );
        if (!result.applied()) {
            statusLabel.setText(i18n("community.jvm.readonly_instance"));
            return;
        }
        previousGenerated = result.generated();
        if (mode == JvmPreset.UPSTREAM) {
            JvmWizardStore.remove(currentInstanceId);
        } else {
            JvmWizardStore.save(currentInstanceId, new JvmWizardStore.State(
                    mode, heapMbOrRecommended(), result.generated(), null));
        }
        statusLabel.setText(i18n("community.jvm.status.saved"));
        loadSelectedInstance();
    }

    /// 清除向导生成的参数。
    private void clear() {
        if (repository == null || currentInstanceId == null || previousGenerated == null) {
            return;
        }
        if (JvmArgWriter.clear(repository, currentInstanceId, previousGenerated)) {
            JvmWizardStore.remove(currentInstanceId);
            previousGenerated = null;
            statusLabel.setText(i18n("community.jvm.status.cleared"));
            loadSelectedInstance();
        } else {
            statusLabel.setText(i18n("community.jvm.status.clear_failed"));
        }
    }

    /// 规范化参数串（去首尾空白 + 空白折叠）。
    ///
    /// @param args 参数串
    /// @return 规范化结果，`null` 视为空串
    @NotNull
    private static String normalize(@Nullable String args) {
        return String.join(" ", JvmArgValidator.tokenize(args));
    }
}
