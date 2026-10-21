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

import org.jackhuang.hmcl.community.ui.CommunityStyles;
import com.jfoenix.controls.JFXButton;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.jackhuang.hmcl.community.env.CommunityPaths;
import org.jackhuang.hmcl.community.migration.OfficialDataImporter;
import org.jackhuang.hmcl.community.migration.OfficialDataLocator;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 社区版设置面板：首页标题行 [设置] 打开，`DialogPane` 内左侧分组、右侧内容。
///
/// 结构：
/// - 内置 `dashboard`（重置布局/进入编辑）、`data`（数据目录；导入入口由步骤 06 扩展）；
/// - 扩展：`META-INF/services` 登记的 [SettingsSectionProvider]（后续步骤使用）。
///
/// 稳定性：分组内容在首次选中时才构造，单个分组构造失败只影响该分组；
/// 所有链接地址为空串时隐藏，不渲染空 href。
@NotNullByDefault
public final class CommunitySettingsPanel {

    /// 面板宽度（像素）。
    private static final int PANEL_WIDTH = 640;

    /// 当前已打开的面板根节点；用于避免重复打开。
    private static @Nullable VBox current;

    /// 仪表盘操作钩子，由 [org.jackhuang.hmcl.community.dashboard.DashboardPane] 注册。
    private static volatile @Nullable DashboardActions dashboardActions;

    /// 仪表盘相关操作。
    public interface DashboardActions {

        /// 恢复默认布局（会二次确认）。
        void resetLayout();

        /// 关闭面板并进入编辑模式。
        void startEditing();
    }

    /// 本类只提供静态方法。
    private CommunitySettingsPanel() {
    }

    /// 注册仪表盘操作钩子；传 `null` 表示注销。
    ///
    /// @param actions 钩子
    public static void setDashboardActions(@Nullable DashboardActions actions) {
        dashboardActions = actions;
    }

    /// 打开设置面板（默认选中第一个分组）。
    ///
    /// @param owner 所属窗口（仅用于定位；可为 `null`）
    public static void open(@Nullable Window owner) {
        open(owner, null);
    }

    /// 打开设置面板并选中指定分组。
    ///
    /// @param owner 所属窗口（仅用于定位；可为 `null`）
    /// @param sectionId 初始分组 id（`null` 或不存在时选中第一个分组）
    public static void open(@Nullable Window owner, @Nullable String sectionId) {
        FXUtils.checkFxUserThread();

        if (current != null && current.getScene() != null) {
            current.requestFocus();
            return;
        }

        List<SettingsSection> sections = collectSections();
        if (sections.isEmpty()) {
            return;
        }

        VBox root = buildRoot(sections, sectionId);
        current = root;
        root.addEventHandler(DialogCloseEvent.CLOSE, event -> current = null);
        Controllers.dialog(root);
    }

    /// 收集内置分组与扩展分组，按 `order` 排序、id 去重。
    ///
    /// @return 分组列表
    @NotNull
    private static List<SettingsSection> collectSections() {
        Map<String, SettingsSection> byId = new LinkedHashMap<>();

        byId.put("dashboard", new SettingsSection(
                "dashboard", "community.settings.tab.dashboard", 10,
                CommunitySettingsPanel::buildDashboardSection));
        byId.put("data", new SettingsSection(
                "data", "community.settings.tab.data", 20,
                CommunitySettingsPanel::buildDataSection));

        ServiceLoader<SettingsSectionProvider> serviceLoader =
                ServiceLoader.load(SettingsSectionProvider.class, CommunitySettingsPanel.class.getClassLoader());
        var iterator = serviceLoader.iterator();
        while (true) {
            SettingsSectionProvider provider;
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                provider = iterator.next();
            } catch (ServiceConfigurationError e) {
                LOG.warning("Failed to load settings section provider", e);
                break;
            }
            try {
                List<SettingsSection> provided = provider.sections();
                if (provided == null) {
                    continue;
                }
                for (SettingsSection section : provided) {
                    if (section == null || section.id().isBlank()) {
                        continue;
                    }
                    if (byId.putIfAbsent(section.id(), section) != null) {
                        LOG.warning("Ignored duplicate settings section id: " + section.id());
                    }
                }
            } catch (Throwable e) {
                LOG.warning("Failed to load settings sections from " + provider.getClass().getName(), e);
            }
        }

        List<SettingsSection> sections = new ArrayList<>(byId.values());
        sections.sort(Comparator.comparingInt(SettingsSection::order));
        return List.copyOf(sections);
    }

    /// 构建面板根节点：标题行 + 左侧分组 + 右侧内容。
    ///
    /// @param sections 分组
    /// @param initialSectionId 初始选中的分组 id；`null` 或不存在时选中第一个
    /// @return 根节点
    @NotNull
    private static VBox buildRoot(@NotNull List<SettingsSection> sections, @Nullable String initialSectionId) {
        VBox root = new VBox(12);
        CommunityStyles.apply(root);
        root.getStyleClass().add("jfx-dialog-layout");
        root.setPrefWidth(PANEL_WIDTH);
        root.setMaxWidth(PANEL_WIDTH);
        root.setMinWidth(480);

        Label title = new Label(i18n("community.settings.title"));
        title.getStyleClass().add("title-label");
        JFXButton close = new JFXButton("✕");
        close.getStyleClass().add("dialog-cancel");
        close.setOnAction(event -> root.fireEvent(new DialogCloseEvent()));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(title, spacer, close);

        ToggleGroup group = new ToggleGroup();
        VBox tabs = new VBox(4);
        tabs.setMinWidth(150);
        tabs.setPrefWidth(150);

        StackPane content = new StackPane();
        content.setMinHeight(320);
        content.setPrefWidth(PANEL_WIDTH - 190);

        Map<String, Parent> cache = new LinkedHashMap<>();
        for (SettingsSection section : sections) {
            ToggleButton tab = new ToggleButton(i18n(section.i18nKey()));
            tab.setToggleGroup(group);
            tab.setMaxWidth(Double.MAX_VALUE);
            tab.getStyleClass().add("jfx-button");
            tab.setUserData(section);
            tab.setOnAction(event -> {
                if (!tab.isSelected()) {
                    tab.setSelected(true);
                    return;
                }
                content.getChildren().setAll(resolveContent(section, cache));
            });
            tabs.getChildren().add(tab);
        }

        if (!tabs.getChildren().isEmpty()) {
            ToggleButton initial = (ToggleButton) tabs.getChildren().get(0);
            if (initialSectionId != null) {
                for (var node : tabs.getChildren()) {
                    ToggleButton tab = (ToggleButton) node;
                    if (((SettingsSection) tab.getUserData()).id().equals(initialSectionId)) {
                        initial = tab;
                        break;
                    }
                }
            }
            initial.setSelected(true);
            content.getChildren().setAll(resolveContent((SettingsSection) initial.getUserData(), cache));
        }

        HBox body = new HBox(12, tabs, content);
        HBox.setHgrow(content, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);

        root.getChildren().addAll(header, body);
        return root;
    }

    /// 解析分组内容（带缓存与失败隔离）。
    ///
    /// @param section 分组
    /// @param cache 缓存
    /// @return 可展示节点
    @NotNull
    private static Parent resolveContent(
            @NotNull SettingsSection section,
            @NotNull Map<String, Parent> cache) {
        Parent cached = cache.get(section.id());
        if (cached != null) {
            return cached;
        }
        Parent content;
        try {
            content = section.content().get();
        } catch (Throwable e) {
            LOG.warning("Failed to build settings section " + section.id(), e);
            content = errorSection(e);
        }
        if (content == null) {
            content = errorSection(new IllegalStateException("settings section is unavailable"));
        }
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("edge-to-edge");
        cache.put(section.id(), scroll);
        return scroll;
    }

    /// 构造分组构造失败时的占位内容。
    ///
    /// @param error 异常
    /// @return 占位节点
    @NotNull
    private static VBox errorSection(@NotNull Throwable error) {
        Label label = new Label(i18n("community.settings.section_failed",
                error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        label.setWrapText(true);
        label.getStyleClass().add("subtitle-label");
        return new VBox(label);
    }

    /// 构造 `dashboard` 分组。
    ///
    /// @return 内容
    @Nullable
    private static Parent buildDashboardSection() {
        VBox box = section("community.settings.dashboard.title");

        JFXButton edit = new JFXButton(i18n("community.settings.dashboard.edit"));
        edit.getStyleClass().add("dialog-accept");
        edit.setOnAction(event -> {
            DashboardActions actions = dashboardActions;
            if (actions != null) {
                actions.startEditing();
            }
            if (current != null) {
                current.fireEvent(new DialogCloseEvent());
            }
        });

        JFXButton reset = new JFXButton(i18n("community.settings.dashboard.reset"));
        reset.getStyleClass().add("dialog-cancel");
        reset.setOnAction(event -> {
            DashboardActions actions = dashboardActions;
            if (actions != null) {
                actions.resetLayout();
            }
        });

        box.getChildren().addAll(
                new Label(i18n("community.settings.dashboard.hint")),
                new HBox(8, edit, reset));
        return box;
    }

    /// 构造 `data` 分组：展示两个根目录、扫描官方 HMCL 数据、显式导入（步骤 06）。
    ///
    /// @return 内容
    @NotNull
    private static Parent buildDataSection() {
        VBox box = section("community.settings.data.title");

        Path local = CommunityPaths.localHome();
        Path user = CommunityPaths.userHome();

        box.getChildren().addAll(
                pathRow("community.settings.data.path.local", local),
                pathRow("community.settings.data.path.user", user));

        Label status = new Label(i18n("community.settings.data.scanning"));
        status.setWrapText(true);
        status.getStyleClass().add("subtitle-label");

        JFXButton rescan = new JFXButton(i18n("community.settings.data.rescan"));
        rescan.getStyleClass().add("dialog-accept");
        JFXButton importButton = new JFXButton(i18n("community.settings.data.import"));
        importButton.getStyleClass().add("dialog-accept");
        importButton.setDisable(true);
        Label importHelp = new Label(i18n("community.settings.data.import.help"));
        importHelp.setWrapText(true);
        importHelp.getStyleClass().add("subtitle-label");

        box.getChildren().addAll(status, new HBox(8, rescan, importButton), importHelp);

        // 当前可供导入的来源（最后一个检测到的目录）。
        Path[] detected = new Path[1];

        Runnable refresh = () -> {
            status.setText(i18n("community.settings.data.scanning"));
            importButton.setDisable(true);
            Schedulers.io().execute(() -> {
                OfficialDataLocator.ScanResult result = OfficialDataLocator.scan();
                Schedulers.javafx().execute(() -> {
                    if (result.detected() && !result.sources().isEmpty()) {
                        detected[0] = result.sources().get(result.sources().size() - 1);
                        status.setText(i18n("community.settings.data.detected", detected[0].toString()));
                        importButton.setDisable(false);
                    } else {
                        detected[0] = null;
                        status.setText(i18n("community.settings.data.not_detected"));
                    }
                });
            });
        };

        rescan.setOnAction(event -> refresh.run());
        importButton.setOnAction(event -> {
            Path source = detected[0];
            if (source == null) {
                return;
            }
            MessageDialogPane pane = new MessageDialogPane.Builder(
                    i18n("community.settings.data.confirm", source.toString()),
                    i18n("community.settings.data.title"),
                    MessageDialogPane.MessageType.QUESTION)
                    .addAction(i18n("community.settings.data.import"), () -> runImport(source, importButton, status))
                    .addCancel(i18n("community.dashboard.cancel"), null)
                    .build();
            Controllers.dialog(pane);
        });

        refresh.run();
        return box;
    }

    /// 在 IO 线程执行导入，完成后回到 FX 线程展示结果。
    ///
    /// @param source       官方数据根
    /// @param importButton 导入按钮（期间禁用）
    /// @param status       状态标签
    private static void runImport(@NotNull Path source,
                                  @NotNull JFXButton importButton,
                                  @NotNull Label status) {
        importButton.setDisable(true);
        status.setText(i18n("community.settings.data.importing"));
        Schedulers.io().execute(() -> {
            try {
                OfficialDataImporter.ImportResult result = OfficialDataImporter.importDefaults(source);
                Schedulers.javafx().execute(() -> {
                    importButton.setDisable(false);
                    String text = i18n("community.settings.data.imported",
                            result.copied(), result.skipped());
                    if (!result.warnings().isEmpty()) {
                        text += "\n" + String.join("\n", result.warnings());
                    }
                    status.setText(text);
                    Controllers.dialog(new MessageDialogPane.Builder(
                            text, i18n("community.settings.data.title"),
                            MessageDialogPane.MessageType.INFO)
                            .ok(null)
                            .build());
                });
            } catch (IOException | RuntimeException e) {
                LOG.warning("Failed to import HMCL data from " + source, e);
                Schedulers.javafx().execute(() -> {
                    importButton.setDisable(false);
                    status.setText(i18n("community.settings.data.import_failed"));
                });
            }
        });
    }

    /// 构造一条“路径 + 打开目录”行。
    ///
    /// @param labelKey 标签 i18n key
    /// @param path 路径
    /// @return 行节点
    @NotNull
    private static VBox pathRow(@NotNull String labelKey, @NotNull Path path) {
        Label label = new Label(i18n(labelKey));
        label.getStyleClass().add("subtitle-label");
        Label value = new Label(path.toString());
        value.setWrapText(true);
        JFXButton open = new JFXButton(i18n("community.settings.data.open"));
        open.getStyleClass().add("dialog-accept");
        open.setOnAction(event -> FXUtils.openFolder(path));
        return new VBox(4, label, value, open);
    }

    /// 构造分组标题 + 标题标签。
    ///
    /// @param titleKey 标题 i18n key
    /// @return 容器
    @NotNull
    private static VBox section(@NotNull String titleKey) {
        Label title = new Label(i18n(titleKey));
        title.getStyleClass().add("title-label");
        VBox box = new VBox(10, title);
        box.setFillWidth(true);
        return box;
    }
}
