/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.decorator;

import com.jfoenix.controls.JFXSnackbar;
import com.jfoenix.controls.JFXSnackbarLayout;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.glavo.monetfx.ColorScheme;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDnD;
import org.jackhuang.hmcl.community.config.CommunityConfigDnD;
import org.jackhuang.hmcl.community.ui.CommunitySideBar;
import org.jackhuang.hmcl.community.ui.CommunityStyles;
import org.jackhuang.hmcl.community.ui.ManagementPage;
import org.jackhuang.hmcl.community.ui.OtherPage;
import org.jackhuang.hmcl.community.ui.m3.GlassBackdrop;
import org.jackhuang.hmcl.community.ui.m3.M3DynamicColors;
import org.jackhuang.hmcl.community.ui.m3.M3Host;
import org.jackhuang.hmcl.community.ui.m3.M3Settings;
import org.jackhuang.hmcl.community.ui.transfer.SettingsImportDialog;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogUtils;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.account.AddAuthlibInjectorServerPane;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane.AnimationProducer;
import org.jackhuang.hmcl.ui.construct.JFXDialogPane;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.download.DownloadPage;
import org.jackhuang.hmcl.ui.main.LauncherSettingsPage;
import org.jackhuang.hmcl.ui.main.RootPage;
import org.jackhuang.hmcl.ui.wizard.Refreshable;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;

public class DecoratorController {
    private final Decorator decorator;
    private final Navigator navigator;
    private final CommunitySideBar sidebar;
    private final HBox shell;

    /// 当前挂在外壳上的 M3 宿主；Classic 主题下为 `null`。
    private @Nullable M3Host m3Host;

    /// 标题栏节点；仅 M3 主题下添加 `lmcl-m3-title-bar` 样式类。
    private @Nullable Node titleBar;

    /// 安装标题栏样式前的内联样式，移除时恢复。
    private @Nullable String titleBarBaseStyle;

    /// 主题色变化时刷新标题栏的 M3 动态取色。
    private final InvalidationListener titleBarColorListener = observable -> applyTitleBarColors();

    public DecoratorController(Stage stage, Node mainPage) {
        decorator = new Decorator(stage);
        decorator.titleTransparentProperty().bind(Themes.titleBarTransparentProperty());

        sidebar = new CommunitySideBar();
        navigator = new Navigator();
        navigator.setOnNavigated(this::onNavigated);
        navigator.init(mainPage);
        sidebar.setActive(mainPage);

        shell = new HBox(sidebar, navigator);
        shell.setFillHeight(true);
        HBox.setHgrow(navigator, Priority.ALWAYS);
        navigator.setMaxWidth(Double.MAX_VALUE);
        applyVisualTheme();
        decorator.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                Platform.runLater(this::installTitleBarStyle);
            }
        });
        decorator.skinProperty().addListener((observable, oldSkin, newSkin) -> {
            removeTitleBarStyle();
            Platform.runLater(this::installTitleBarStyle);
        });
        Platform.runLater(this::installTitleBarStyle);
        decorator.onCloseNavButtonActionProperty().set(e -> close());
        decorator.onBackNavButtonActionProperty().set(e -> back());
        decorator.onRefreshNavButtonActionProperty().set(e -> refresh());

        setupAuthlibInjectorDnD();

        decorator.contentBackgroundProperty().bind(Themes.backgroundProperty());

        // pass key events to current dialog / current page
        decorator.addEventFilter(KeyEvent.ANY, e -> {
            if (!(e.getTarget() instanceof Node t)) {
                return;
            }

            Node newTarget;

            JFXDialogPane currentDialogPane = null;
            if (decorator.getDrawerWrapper() != null) {
                currentDialogPane = (JFXDialogPane) decorator.getDrawerWrapper().getProperties().get(DialogUtils.PROPERTY_DIALOG_PANE_INSTANCE);
            }

            if (currentDialogPane != null && currentDialogPane.peek().isPresent()) {
                newTarget = currentDialogPane.peek().get();
            } else {
                newTarget = navigator.getCurrentPage();
            }
            boolean needsRedirect = true;

            while (t != null) {
                if (t == newTarget) {
                    // current event target is in newTarget
                    needsRedirect = false;
                    break;
                }
                t = t.getParent();
            }
            if (!needsRedirect) {
                return;
            }

            e.consume();
            newTarget.fireEvent(e.copyFor(e.getSource(), newTarget));
        });

        // press ESC to go back
        onEscPressed(navigator, this::back);

        // https://github.com/HMCL-dev/HMCL/issues/4290
        if (OperatingSystem.CURRENT_OS != OperatingSystem.MACOS) {
            // press F11 to toggle full screen
            navigator.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.F11) {
                    stage.setFullScreen(!stage.isFullScreen());
                    e.consume();
                }
            });
        }

        navigator.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() == MouseButton.BACK) {
                back();
                e.consume();
            }
        });
    }

    /// Applies the selected visual theme to the launcher shell.
    ///
    /// The sidebar and navigator are kept alive, so changing the theme does not
    /// reset the current page or its scroll position. Must be called on the
    /// JavaFX application thread.
    public void applyVisualTheme() {
        if (m3Host != null) {
            // Detach the shell before creating the next host, so the shell never
            // belongs to two parents even if JavaFX's reparenting order changes.
            m3Host.setContent(null);
            m3Host.dispose();
            m3Host = null;
        }

        if (M3Settings.isM3Enabled()) {
            // Swap in an empty host before moving the shell. DecoratorSkin binds
            // the content list to its placeholder; reparenting the shell first
            // desynchronizes that binding and ContentBinding then removes an
            // element from an already-empty destination list (toIndex = 1).
            M3Host nextHost = new M3Host();
            decorator.getContent().setAll(nextHost);
            nextHost.setContent(shell);
            m3Host = nextHost;
            // Use the whole decorator as the backdrop host, so the sidebar, home
            // content and title bar all sample the same scene-space snapshot.
            GlassBackdrop.get().attach(decorator);
            Platform.runLater(this::installTitleBarStyle);
        } else {
            decorator.getContent().setAll(shell);
            GlassBackdrop.get().detach();
            removeTitleBarStyle();
        }
    }

    /// Applies the M3 surface color to the decorator title bar.
    private void installTitleBarStyle() {
        if (!M3Settings.isM3Enabled()) {
            removeTitleBarStyle();
            return;
        }
        if (titleBar != null) {
            return;
        }

        Node node = decorator.lookup(".jfx-tool-bar");
        if (!(node instanceof Parent parent)) {
            return;
        }

        CommunityStyles.apply(parent);
        node.getStyleClass().add("lmcl-m3-title-bar");
        titleBar = node;
        titleBarBaseStyle = node.getStyle();
        M3DynamicColors.get().schemeProperty().addListener(titleBarColorListener);
        applyTitleBarColors();
    }

    /// 将当前 M3 动态取色写入标题栏的 looked-up color 变量。
    ///
    /// 标题栏位于 M3 宿主之外，拿不到 m3fx 的 `-m3-color-*` 变量；这里读取
    /// [M3DynamicColors] 基于壁纸生成的方案，随壁纸/主题色变化更新。
    private void applyTitleBarColors() {
        if (!(titleBar instanceof Parent parent) || !M3Settings.isM3Enabled()) {
            return;
        }

        ColorScheme scheme = M3DynamicColors.get().getScheme();
        parent.setStyle(
                "-lmcl-title-surface: " + cssColor(scheme.getPrimaryContainer()) + "; "
                        + "-lmcl-title-on-surface: " + cssColor(scheme.getOnPrimaryContainer()) + "; "
                        + "-lmcl-title-outline: " + cssColor(scheme.getOutlineVariant()) + ";");
    }

    /// 将 JavaFX 颜色转换为可写入内联 CSS 的 rgba 字符串。
    private static String cssColor(Color color) {
        return String.format(
                Locale.ROOT,
                "rgba(%d,%d,%d,%.3f)",
                Math.round(color.getRed() * 255.0),
                Math.round(color.getGreen() * 255.0),
                Math.round(color.getBlue() * 255.0),
                color.getOpacity());
    }

    /// Removes the M3 title bar style; safe to call repeatedly.
    private void removeTitleBarStyle() {
        M3DynamicColors.get().schemeProperty().removeListener(titleBarColorListener);
        if (titleBar != null) {
            titleBar.getStyleClass().remove("lmcl-m3-title-bar");
            titleBar.setStyle(titleBarBaseStyle);
        }
        titleBar = null;
        titleBarBaseStyle = null;
    }

    public Decorator getDecorator() {
        return decorator;
    }

    // ==== Navigation ====

    public void navigate(Node node, AnimationProducer animationProducer, Duration duration, Interpolator interpolator) {
        navigator.navigate(node, animationProducer, duration, interpolator);
    }

    public BooleanProperty backableProperty() {
        return navigator.backableProperty();
    }

    private void close() {
        if (navigator.getCurrentPage() instanceof DecoratorPage) {
            DecoratorPage page = (DecoratorPage) navigator.getCurrentPage();

            if (page.isPageCloseable()) {
                page.closePage();
                return;
            }
        }
        navigator.clear();
    }

    private void back() {
        if (navigator.getCurrentPage() instanceof DecoratorPage) {
            DecoratorPage page = (DecoratorPage) navigator.getCurrentPage();

            if (page.back()) {
                if (navigator.canGoBack()) {
                    navigator.close();
                }
            }
        } else {
            if (navigator.canGoBack()) {
                navigator.close();
            }
        }
    }

    private void refresh() {
        if (navigator.getCurrentPage() instanceof Refreshable) {
            Refreshable refreshable = (Refreshable) navigator.getCurrentPage();

            if (refreshable.refreshableProperty().get())
                refreshable.refresh();
        }
    }

    private void onNavigated(Navigator.NavigationEvent event) {
        if (event.getSource() != this.navigator) return;
        Node to = event.getNode();

        sidebar.setActive(to);

        if (to instanceof Refreshable) {
            decorator.canRefreshProperty().bind(((Refreshable) to).refreshableProperty());
        } else {
            decorator.canRefreshProperty().unbind();
            decorator.canRefreshProperty().set(false);
        }

        // LMCL: the home page and the four top-level sections (management, settings,
        // other, download) must not show the top-left home/close shortcut. The back
        // arrow remains available on those sections; other pages are unaffected.
        boolean home = to instanceof RootPage;
        boolean topLevelSection = to instanceof ManagementPage
                || to instanceof LauncherSettingsPage
                || to instanceof OtherPage
                || to instanceof DownloadPage;
        boolean noCloseNav = home || topLevelSection;
        decorator.canCloseProperty().set(!noCloseNav && navigator.size() > 2);

        if (to instanceof DecoratorPage) {
            decorator.showCloseAsHomeProperty().set(!noCloseNav && !((DecoratorPage) to).isPageCloseable());
        } else {
            decorator.showCloseAsHomeProperty().set(!noCloseNav);
        }

        decorator.setNavigationDirection(event.getDirection());

        // state property should be updated at last.
        if (to instanceof DecoratorPage) {
            decorator.stateProperty().bind(((DecoratorPage) to).stateProperty());
        } else {
            decorator.stateProperty().unbind();
            decorator.stateProperty().set(new DecoratorPage.State("", null, navigator.canGoBack(), false, true));
        }

        if (to instanceof Region) {
            Region region = (Region) to;
            // Let root pane fix window size.
            StackPane parent = (StackPane) region.getParent();
            region.prefWidthProperty().bind(parent.widthProperty());
            region.prefHeightProperty().bind(parent.heightProperty());
        }
    }

    // ==== Dialog ====
    public void showDialog(Node node) {
        DialogUtils.show(decorator, node);
    }

    private void closeDialog(Node node) {
        DialogUtils.close(node);
    }

    public void showDialogLater(Node node) {
        DialogUtils.showLater(decorator, node);
    }

    // ==== Toast ====

    public void showToast(String content) {
        decorator.getSnackbar().fireEvent(new JFXSnackbar.SnackbarEvent(new JFXSnackbarLayout(content)));
    }

    // ==== Wizard ====

    public void startWizard(WizardProvider wizardProvider) {
        startWizard(wizardProvider, null);
    }

    public void startWizard(WizardProvider wizardProvider, String category) {
        FXUtils.checkFxUserThread();

        navigator.navigate(new DecoratorWizardDisplayer(wizardProvider, category),
                ContainerAnimations.FORWARD, Motion.SHORT4, Motion.EASE);
    }

    // ==== Authlib Injector DnD ====

    private void setupAuthlibInjectorDnD() {
        decorator.addEventFilter(DragEvent.DRAG_OVER, AuthlibInjectorDnD.dragOverHandler());
        decorator.addEventFilter(DragEvent.DRAG_DROPPED, AuthlibInjectorDnD.dragDroppedHandler(
                url -> Controllers.dialog(new AddAuthlibInjectorServerPane(url))));
        decorator.addEventFilter(DragEvent.DRAG_OVER, CommunityConfigDnD.dragOverHandler());
        decorator.addEventFilter(DragEvent.DRAG_DROPPED, CommunityConfigDnD.dragDroppedHandler(
                SettingsImportDialog::open));
    }
}
