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
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.community.ui.AccountActions;
import org.jackhuang.hmcl.community.ui.ManagementPage;
import org.jackhuang.hmcl.community.ui.RoundedHitArea;
import org.jackhuang.hmcl.community.ui.m3.M3Settings;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.account.AccountAdvancedListItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 首页“当前账户”卡：当前账户行、可展开账户列表与新建/管理入口。
///
/// 卡片固定显示在主页左栏，不参与 `dashboard.json` 的组件排序。当前账户行点击
/// 进入管理页；右侧箭头只负责展开/收起账户列表，避免点击语义冲突。
///
/// 线程：构造、菜单与动画都只在 JavaFX 线程执行。
@NotNullByDefault
public final class HomeAccountCard extends VBox {

    /// 展开列表最多显示 5 行，超过后由内部滚动条接管。
    private static final double ROW_HEIGHT = 48;
    private static final double MAX_LIST_HEIGHT = ROW_HEIGHT * 5 + 8;
    private static final double LIST_SPACING = 2;
    private static final Duration EXPAND_DURATION = Duration.millis(200);
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final AccountAdvancedListItem currentItem = new AccountAdvancedListItem();
    private final JFXButton toggleButton = new JFXButton();
    private final Tooltip toggleTooltip = new Tooltip(i18n("community.home.account.expand"));
    private final VBox accountList = new VBox(LIST_SPACING);
    private final ScrollPane accountScroll = new ScrollPane(accountList);
    private final JFXButton newAccountButton;
    private final ContextMenu newAccountMenu = new ContextMenu();
    private final ListChangeListener<Account> accountsListener = change -> rebuildAccountList();
    private final ChangeListener<Account> selectionListener = (observable, oldValue, newValue) -> updateSelection();

    private @Nullable Timeline expansion;
    private boolean expanded;
    private boolean disposed;

    /// 构造账户卡。
    public HomeAccountCard() {
        FXUtils.checkFxUserThread();

        getStyleClass().addAll("card", "community-account-card");
        setMaxWidth(Double.MAX_VALUE);
        setMinHeight(Region.USE_PREF_SIZE);
        setSpacing(8);

        Label title = new Label(i18n("community.home.account.title"));
        title.getStyleClass().addAll("title-label", "community-title");

        currentItem.setPrefHeight(ROW_HEIGHT);
        currentItem.setMaxWidth(Double.MAX_VALUE);
        currentItem.accountProperty().bind(Accounts.selectedAccountProperty());
        currentItem.setOnAction(event -> openManagement());
        HBox.setHgrow(currentItem, Priority.ALWAYS);

        toggleButton.getStyleClass().add("community-account-toggle");
        toggleButton.setFocusTraversable(false);
        toggleButton.setMinSize(32, 32);
        toggleButton.setPrefSize(32, 32);
        toggleButton.setGraphic(SVG.ARROW_DROP_DOWN.createIcon(18));
        toggleButton.setAccessibleText(i18n("community.home.account.expand"));
        Tooltip.install(toggleButton, toggleTooltip);
        toggleButton.setOnAction(event -> setExpanded(!expanded));
        RoundedHitArea.install(toggleButton);

        HBox currentRow = new HBox(4, currentItem, toggleButton);
        currentRow.setAlignment(Pos.CENTER_LEFT);
        currentRow.getStyleClass().add("community-account-current");

        accountList.setFillWidth(true);
        accountList.setSpacing(LIST_SPACING);
        accountScroll.setFitToWidth(true);
        accountScroll.setPannable(true);
        accountScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        accountScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        accountScroll.setMinHeight(0);
        accountScroll.setPrefHeight(Region.USE_COMPUTED_SIZE);
        accountScroll.setMaxHeight(0);
        accountScroll.setVisible(false);
        accountScroll.setManaged(false);
        accountScroll.getStyleClass().add("community-account-scroll");

        newAccountButton = BuiltinWidgets.borderButton(i18n("community.home.account.new"), this::showNewAccountMenu);
        newAccountMenu.setOnShowing(event -> rebuildNewAccountMenu());
        JFXButton manageButton = BuiltinWidgets.borderButton(i18n("community.home.account.manage"), this::openManagement);

        getChildren().addAll(title, currentRow, accountScroll,
                BuiltinWidgets.cardFooter(newAccountButton, manageButton));

        Accounts.getAccounts().addListener(accountsListener);
        Accounts.selectedAccountProperty().addListener(selectionListener);
        rebuildAccountList();
    }

    /// 展开或收起账户列表。
    ///
    /// @param value `true` 展开，`false` 收起
    private void setExpanded(boolean value) {
        if (disposed || expanded == value) {
            return;
        }
        expanded = value;
        toggleButton.setGraphic((value ? SVG.ARROW_DROP_UP : SVG.ARROW_DROP_DOWN).createIcon(18));
        String accessible = i18n(value ? "community.home.account.collapse" : "community.home.account.expand");
        toggleButton.setAccessibleText(accessible);
        toggleTooltip.setText(accessible);

        if (expansion != null) {
            expansion.stop();
            expansion = null;
        }

        if (value) {
            accountScroll.setVisible(true);
            accountScroll.setManaged(true);
        }

        double target = value ? expandedHeight() : 0;
        if (animationDisabled()) {
            applyExpandedHeight(target);
            return;
        }

        double from = accountScroll.getMaxHeight();
        if (!value && (from <= 0 || Double.isNaN(from))) {
            from = accountScroll.getHeight();
        }
        if (from < 0 || Double.isNaN(from)) {
            from = 0;
        }

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(accountScroll.maxHeightProperty(), from, Interpolator.EASE_BOTH)),
                new KeyFrame(EXPAND_DURATION,
                        new KeyValue(accountScroll.maxHeightProperty(), target, Interpolator.EASE_BOTH)));
        timeline.setOnFinished(event -> {
            expansion = null;
            applyExpandedHeight(target);
        });
        expansion = timeline;
        timeline.play();
    }

    /// 动画结束后固定最终高度；收起时同时从布局中移除。
    private void applyExpandedHeight(double height) {
        accountScroll.setMaxHeight(Math.max(0, height));
        if (!expanded) {
            accountScroll.setVisible(false);
            accountScroll.setManaged(false);
        }
    }

    /// 展开高度取内容高度，最多 5 行。
    private double expandedHeight() {
        if (accountList.getChildren().isEmpty()) {
            return 0;
        }
        double content = accountList.prefHeight(-1);
        if (content <= 0 || Double.isNaN(content)) {
            content = accountList.getChildren().size() * ROW_HEIGHT
                    + Math.max(0, accountList.getChildren().size() - 1) * LIST_SPACING;
        }
        return Math.min(content + 2, MAX_LIST_HEIGHT);
    }

    /// 动画开关同时考虑启动器“关闭动画”和 M3 reduced motion。
    private static boolean animationDisabled() {
        return SettingsManager.settings().animationDisabledProperty().get() || M3Settings.isReducedMotion();
    }

    /// 重建账户列表；账户数量变化时保持展开高度与当前选中态。
    private void rebuildAccountList() {
        accountList.getChildren().clear();
        for (Account account : Accounts.getAccounts()) {
            AccountAdvancedListItem item = new AccountAdvancedListItem(account);
            item.getStyleClass().add("community-account-entry");
            item.setPrefHeight(ROW_HEIGHT);
            item.setMaxWidth(Double.MAX_VALUE);
            item.setOnAction(event -> {
                Accounts.setSelectedAccount(account);
                setExpanded(false);
            });
            updateSelection(item, account);
            accountList.getChildren().add(item);
        }

        if (accountList.getChildren().isEmpty()) {
            Label empty = BuiltinWidgets.caption(i18n("community.home.account.empty"));
            empty.getStyleClass().add("community-account-empty");
            empty.setMinHeight(ROW_HEIGHT);
            empty.setAlignment(Pos.CENTER_LEFT);
            accountList.getChildren().add(empty);
        }

        if (expanded) {
            accountScroll.setMaxHeight(expandedHeight());
        }
    }

    /// 更新单个账户行的选中样式。
    private void updateSelection(@NotNull AccountAdvancedListItem item, @NotNull Account account) {
        item.pseudoClassStateChanged(SELECTED, account == Accounts.getSelectedAccount());
    }

    /// 刷新全部账户行的选中样式。
    private void updateSelection() {
        for (Node node : accountList.getChildren()) {
            if (node instanceof AccountAdvancedListItem item && item.accountProperty().get() != null) {
                updateSelection(item, item.accountProperty().get());
            }
        }
    }

    /// 打开管理页的账户 tab。
    private void openManagement() {
        ManagementPage page = ManagementPage.getInstance();
        page.showAccounts();
        Controllers.navigate(page);
    }

    /// 在按钮下方打开新建账户菜单。
    private void showNewAccountMenu() {
        newAccountMenu.show(newAccountButton, Side.BOTTOM, 0, 4);
    }

    /// 每次弹出前重建菜单，认证服务器列表可能已变化。
    private void rebuildNewAccountMenu() {
        newAccountMenu.getItems().clear();

        MenuItem microsoft = new MenuItem(i18n("community.home.account.new.microsoft"));
        microsoft.setOnAction(event -> AccountActions.openMicrosoft());
        MenuItem offline = new MenuItem(i18n("community.home.account.new.offline"));
        offline.setOnAction(event -> AccountActions.openOffline());
        newAccountMenu.getItems().addAll(microsoft, offline);

        if (!SettingsManager.getAuthlibInjectorServers().isEmpty()) {
            newAccountMenu.getItems().add(new SeparatorMenuItem());
            for (AuthlibInjectorServer server : SettingsManager.getAuthlibInjectorServers()) {
                MenuItem item = new MenuItem(server.getName());
                item.setOnAction(event -> AccountActions.openAuthServerAccount(server));
                newAccountMenu.getItems().add(item);
            }
        }

        newAccountMenu.getItems().add(new SeparatorMenuItem());
        MenuItem addServer = new MenuItem(i18n("community.home.account.new.add_server"));
        addServer.setOnAction(event -> AccountActions.openAddAuthServer());
        newAccountMenu.getItems().add(addServer);
    }

    /// 释放监听器与动画；可重复调用。
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (expansion != null) {
            expansion.stop();
            expansion = null;
        }
        newAccountMenu.hide();
        Accounts.getAccounts().removeListener(accountsListener);
        Accounts.selectedAccountProperty().removeListener(selectionListener);
        currentItem.accountProperty().unbind();
    }
}
