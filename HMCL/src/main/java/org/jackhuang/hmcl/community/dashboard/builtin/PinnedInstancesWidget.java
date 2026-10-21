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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.community.dashboard.DashboardContext;
import org.jackhuang.hmcl.community.dashboard.DashboardWidget;
import org.jackhuang.hmcl.community.dashboard.DashboardWidgetDescriptor;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.ui.Controllers.dialog;
import static org.jackhuang.hmcl.ui.FXUtils.openFolder;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 首页置顶实例卡：按用户顺序展示置顶实例，点击切换选中实例。
///
/// 置顶列表的唯一数据源是 `dashboard.json` 的 `pinnedInstanceIds`（经
/// [org.jackhuang.hmcl.community.dashboard.DashboardPrefs] 读写），卡片不维护第二份状态。
@NotNullByDefault
public final class PinnedInstancesWidget extends DashboardWidget {

    /// 最多可置顶的实例数。
    private static final int MAX_PINNED = 12;

    /// 实例卡片容器。
    private final FlowPane tiles = new FlowPane(6, 6);

    /// 空态说明。
    private final Label emptyLabel = BuiltinWidgets.caption("community.pinned.empty");

    /// 超出可见数量时的提示。
    private final Label moreLabel = BuiltinWidgets.caption("");

    /// 管理按钮。
    private final JFXButton manageButton = BuiltinWidgets.borderButton(
            i18n("community.pinned.manage"), this::openManageDialog);

    /// 已加载的实例 id -> 实例。
    private final Map<String, Version> versions = new LinkedHashMap<>();

    /// 是否有管理对话框正在展示。
    private boolean managing;

    /// 构造置顶卡。
    ///
    /// @param descriptor 组件描述
    /// @param context 组件上下文
    public PinnedInstancesWidget(@NotNull DashboardWidgetDescriptor descriptor,
                                 @NotNull DashboardContext context) {
        super(descriptor, context);
    }

    /// 创建卡片骨架。
    ///
    /// @return 内容节点
    @Override
    @NotNull
    protected Node createContent() {
        tiles.setPrefWrapLength(360);
        moreLabel.setVisible(false);
        moreLabel.setManaged(false);

        HBox footer = new HBox(8, BuiltinWidgets.spacer(), moreLabel, manageButton);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(4, tiles, emptyLabel, footer);
        box.setFillWidth(true);

        heightProperty().addListener((observable, oldValue, newValue) -> render());
        return box;
    }

    /// 重新读取实例列表。
    @Override
    protected void refreshAsync() {
        HMCLGameRepository repository = context.repository().get();
        if (repository == null) {
            versions.clear();
            render();
            return;
        }
        loadAsync(() -> List.copyOf(repository.getVersions()), list -> {
            versions.clear();
            for (Version version : list) {
                versions.put(version.getId(), version);
            }
            render();
        });
    }

    /// 重新渲染置顶实例。
    private void render() {
        if (disposed) {
            return;
        }

        List<String> pinned = context.prefs().pinnedInstanceIds();
        List<String> valid = new ArrayList<>();
        for (String id : pinned) {
            if (versions.containsKey(id)) {
                valid.add(id);
            } else {
                LOG.debug("Ignoring pinned instance that no longer exists: " + id);
            }
        }

        int maxVisible = getHeight() >= 200 ? 6 : 3;
        tiles.getChildren().clear();
        for (int i = 0; i < Math.min(maxVisible, valid.size()); i++) {
            tiles.getChildren().add(createTile(valid.get(i)));
        }

        boolean empty = valid.isEmpty();
        emptyLabel.setVisible(empty);
        emptyLabel.setManaged(empty);

        int hidden = valid.size() - Math.min(maxVisible, valid.size());
        boolean showMore = hidden > 0;
        moreLabel.setVisible(showMore);
        moreLabel.setManaged(showMore);
        if (showMore) {
            moreLabel.setText(i18n("community.pinned.more", hidden));
        }
    }

    /// 创建一个实例按钮。
    ///
    /// @param id 实例 id
    /// @return 按钮
    @NotNull
    private Node createTile(@NotNull String id) {
        Version version = versions.get(id);
        JFXButton button = BuiltinWidgets.borderButton(BuiltinWidgets.shorten(id, 14), () -> select(id));
        button.setMinWidth(84);
        button.setMaxWidth(140);

        HMCLGameRepository repository = context.repository().get();
        String gameVersion = version == null || repository == null
                ? id
                : BuiltinWidgets.gameVersion(repository, version);
        Tooltip tooltip = new Tooltip(id + "\n" + gameVersion);
        button.setTooltip(tooltip);

        ContextMenu menu = new ContextMenu();
        MenuItem selectItem = new MenuItem(i18n("community.pinned.select"));
        selectItem.setOnAction(event -> select(id));
        MenuItem unpinItem = new MenuItem(i18n("community.pinned.unpin"));
        unpinItem.setOnAction(event -> unpin(id));
        MenuItem folderItem = new MenuItem(i18n("community.pinned.open_folder"));
        folderItem.setOnAction(event -> openInstanceFolder(id));
        menu.getItems().addAll(selectItem, unpinItem, folderItem);
        button.setContextMenu(menu);
        return button;
    }

    /// 选中实例。
    ///
    /// @param id 实例 id
    private void select(@NotNull String id) {
        HMCLGameRepository repository = context.repository().get();
        if (repository == null) {
            return;
        }
        try {
            repository.setSelectedInstance(id);
        } catch (Throwable e) {
            LOG.warning("Failed to select pinned instance " + id, e);
        }
    }

    /// 从置顶列表移除实例。
    ///
    /// @param id 实例 id
    private void unpin(@NotNull String id) {
        List<String> pinned = new ArrayList<>(context.prefs().pinnedInstanceIds());
        if (pinned.remove(id)) {
            context.prefs().setPinnedInstanceIds(List.copyOf(pinned));
            render();
        }
    }

    /// 打开实例目录。
    ///
    /// @param id 实例 id
    private void openInstanceFolder(@NotNull String id) {
        HMCLGameRepository repository = context.repository().get();
        if (repository == null) {
            return;
        }
        try {
            openFolder(repository.getVersionRoot(id));
        } catch (Throwable e) {
            LOG.warning("Failed to open instance folder " + id, e);
        }
    }

    /// 打开置顶管理对话框。
    private void openManageDialog() {
        if (managing) {
            return;
        }
        HMCLGameRepository repository = context.repository().get();
        if (repository == null) {
            return;
        }

        List<String> order = new ArrayList<>(context.prefs().pinnedInstanceIds());
        order.removeIf(id -> !versions.containsKey(id));

        Label hint = BuiltinWidgets.caption("");
        VBox list = new VBox(4);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(280);

        String[] selected = new String[1];
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.getChildren().clear();

            List<String> ids = new ArrayList<>(order);
            for (String id : versions.keySet()) {
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }

            for (String id : ids) {
                CheckBox box = new CheckBox(id);
                box.setSelected(order.contains(id));
                box.setMaxWidth(Double.MAX_VALUE);
                box.setOnAction(event -> {
                    if (box.isSelected()) {
                        if (order.size() >= MAX_PINNED) {
                            box.setSelected(false);
                            hint.setText(i18n("community.pinned.dialog.limit", MAX_PINNED));
                            return;
                        }
                        if (!order.contains(id)) {
                            order.add(id);
                        }
                    } else {
                        order.remove(id);
                    }
                    selected[0] = id;
                    hint.setText("");
                    rebuild[0].run();
                });
                list.getChildren().add(box);
            }
        };
        rebuild[0].run();

        JFXButton up = BuiltinWidgets.borderButton(i18n("community.pinned.up"), () -> move(order, selected, -1, rebuild));
        JFXButton down = BuiltinWidgets.borderButton(i18n("community.pinned.down"), () -> move(order, selected, 1, rebuild));
        HBox tools = new HBox(8, up, down, hint);
        tools.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(8, BuiltinWidgets.caption("community.pinned.dialog.hint", MAX_PINNED), tools, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox root = BuiltinWidgets.dialogRoot(
                i18n("community.pinned.dialog.title"), body, 440);
        JFXButton ok = BuiltinWidgets.raisedButton(i18n("button.ok"), () -> {
            context.prefs().setPinnedInstanceIds(List.copyOf(order));
            managing = false;
            root.fireEvent(new DialogCloseEvent());
            render();
        });
        JFXButton cancel = BuiltinWidgets.borderButton(i18n("button.cancel"), () -> {
            managing = false;
            root.fireEvent(new DialogCloseEvent());
        });
        body.getChildren().add(BuiltinWidgets.dialogActions(ok, cancel));
        root.addEventHandler(DialogCloseEvent.CLOSE, event -> managing = false);

        managing = true;
        dialog(root);
    }

    /// 在置顶列表中上移或下移选中项。
    ///
    /// @param order 当前顺序
    /// @param selected 选中 id 的单元素数组
    /// @param delta 移动方向
    /// @param rebuild 重建回调
    private static void move(@NotNull List<String> order, @NotNull String[] selected,
                             int delta, @NotNull Runnable[] rebuild) {
        String id = selected[0];
        if (id == null) {
            return;
        }
        int index = order.indexOf(id);
        int target = index + delta;
        if (index < 0 || target < 0 || target >= order.size()) {
            return;
        }
        order.remove(index);
        order.add(target, id);
        rebuild[0].run();
    }
}
