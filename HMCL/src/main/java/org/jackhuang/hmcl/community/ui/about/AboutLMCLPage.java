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
 * Modified by LMCL team on 2026-10-22: About LMCL page.
 */
package org.jackhuang.hmcl.community.ui.about;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.community.CommunityMetadata;
import org.jackhuang.hmcl.community.update.CommunityUpdateDialog;
import org.jackhuang.hmcl.community.update.CommunityUpdateNotifier;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// “关于 LMCL”页面。
///
/// 内容顺序：产品信息、开发人员、上游 HMCL 致谢（全部条目）、捐赠者、
/// 更新检查、法律信息。致谢列表直接读取上游
/// `assets/about/thanks.json`，不在本页复制或筛选名单。
///
/// 线程：构造、渲染与按钮回调都在 FX 线程；数据读取经
/// [org.jackhuang.hmcl.task.Schedulers#io()]。
@NotNullByDefault
public final class AboutLMCLPage extends SpinnerPane {

    private static final String KERZE_AVATAR = "/assets/about/avatars/kerze.jpg";
    private static final String ICON_DESIGNER_AVATAR = "/assets/about/avatars/muzidai.jpg";

    private final WeakListenerHolder holder = new WeakListenerHolder();
    private final VBox content = new VBox();

    /// 构造页面并异步加载数据。
    public AboutLMCLPage() {
        content.getStyleClass().add("spinner-pane-content");
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        setContent(scrollPane);

        showSpinner();
        Schedulers.io().execute(() -> {
            AboutData data = AboutData.load();
            Schedulers.javafx().execute(() -> {
                hideSpinner();
                render(data);
            });
        });
    }

    /// 渲染页面内容。
    ///
    /// @param data 数据
    private void render(@NotNull AboutData data) {
        content.getChildren().setAll(
                ComponentList.createComponentListTitle(i18n("community.about.title")),
                buildAbout(),
                ComponentList.createComponentListTitle(i18n("community.about.developers")),
                buildDevelopers(),
                ComponentList.createComponentListTitle(i18n("community.about.thanks")),
                buildThanks(data.thanks()),
                ComponentList.createComponentListTitle(i18n("community.about.donors")),
                buildDonors(data.donors()),
                ComponentList.createComponentListTitle(i18n("community.about.update")),
                buildUpdateSection(),
                ComponentList.createComponentListTitle(i18n("about.legal")),
                buildLegal()
        );
    }

    /// 产品信息卡片。
    @NotNull
    private ComponentList buildAbout() {
        LineButton launcher = CommunityMetadata.WEBSITE.isBlank()
                ? new LineButton()
                : LineButton.createExternalLinkButton(CommunityMetadata.WEBSITE);
        launcher.setLargeTitle(true);
        launcher.setLeading(FXUtils.newBuiltinImage("/assets/img/icon.png"));
        launcher.setTitle(CommunityMetadata.fullName());
        launcher.setSubtitle(i18n("community.about.version_line",
                CommunityMetadata.version(), CommunityMetadata.baseline()));

        ComponentList list = new ComponentList();
        list.getContent().setAll(launcher);
        return list;
    }

    /// 开发人员卡片。
    @NotNull
    private ComponentList buildDevelopers() {
        LineButton developer = new LineButton();
        developer.setLargeTitle(true);
        setAvatar(developer, KERZE_AVATAR);
        developer.setTitle("指尖的宇宙 kerze");
        developer.setSubtitle(i18n("community.about.developer.role"));

        LineButton iconDesigner = new LineButton();
        iconDesigner.setLargeTitle(true);
        setAvatar(iconDesigner, ICON_DESIGNER_AVATAR);
        iconDesigner.setTitle("木子呆、法棍与仰望星空派");
        iconDesigner.setSubtitle(i18n("community.about.developer.icon"));

        ComponentList list = new ComponentList();
        list.getContent().setAll(developer, iconDesigner);
        return list;
    }

    /// 设置开发人员头像；资源缺失时回退到通用人员图标。
    ///
    /// @param button 目标按钮
    /// @param resource 内置头像路径
    private static void setAvatar(@NotNull LineButton button, @NotNull String resource) {
        Image image = loadImage(resource);
        if (image == null) {
            button.setLeading(SVG.PERSON.createIcon(32));
            return;
        }
        ImageContainer view = new ImageContainer(32, 32);
        view.setImage(image);
        view.setMouseTransparent(true);
        button.setLeading(view);
    }

    /// 上游致谢列表；不截断、不筛选。
    ///
    /// @param entries 致谢条目
    @NotNull
    private ComponentList buildThanks(@NotNull List<AboutData.Entry> entries) {
        ComponentList list = new ComponentList();
        if (entries.isEmpty()) {
            list.getContent().add(hint(i18n("community.about.thanks.empty")));
            return list;
        }

        for (AboutData.Entry entry : entries) {
            LineButton button = new LineButton();
            button.setLargeTitle(true);

            if (entry.externalLink() != null && !entry.externalLink().isBlank()) {
                button.setTrailingIcon(SVG.OPEN_IN_NEW);
                button.setOnAction(event -> FXUtils.openLink(entry.externalLink()));
            }

            if (entry.image() != null) {
                bindImage(button, entry.image());
            }

            if (entry.title() != null) {
                button.setTitle(entry.title());
            } else if (entry.titleLocalized() != null) {
                button.setTitle(i18n(entry.titleLocalized()));
            }

            if (entry.subtitle() != null) {
                button.setSubtitle(entry.subtitle());
            } else if (entry.subtitleLocalized() != null) {
                button.setSubtitle(i18n(entry.subtitleLocalized()));
            }

            list.getContent().add(button);
        }
        return list;
    }

    /// 捐赠者列表；当前为空态，不提供捐赠入口。
    ///
    /// @param donors 捐赠者
    @NotNull
    private ComponentList buildDonors(@NotNull List<AboutData.Donor> donors) {
        ComponentList list = new ComponentList();
        if (donors.isEmpty()) {
            list.getContent().add(hint(i18n("community.about.donors.empty")));
            return list;
        }

        for (AboutData.Donor donor : donors) {
            LineButton button = new LineButton();
            button.setLargeTitle(true);
            button.setTitle(donor.name());
            String note = donor.note();
            if (note != null && !note.isBlank()) {
                // 捐赠者备注允许直接写 i18n key，便于同一份数据在中英文界面复用。
                button.setSubtitle(I18n.hasKey(note) ? i18n(note) : note);
            }
            if (donor.link() != null && !donor.link().isBlank()) {
                button.setTrailingIcon(SVG.OPEN_IN_NEW);
                button.setOnAction(event -> FXUtils.openLink(donor.link()));
            }
            list.getContent().add(button);
        }
        return list;
    }

    /// 更新检查区。沿用步骤 05 的只读检查逻辑，不下载、不替换文件。
    @NotNull
    private ComponentList buildUpdateSection() {
        JFXButton check = new JFXButton(i18n("community.update.check"));
        check.getStyleClass().add("dialog-accept");
        check.setDisable(!CommunityUpdateNotifier.isConfigured());

        Label notConfigured = new Label(i18n("community.update.not_configured"));
        notConfigured.getStyleClass().add("subtitle-label");
        notConfigured.setWrapText(true);
        notConfigured.setVisible(!CommunityUpdateNotifier.isConfigured());
        notConfigured.setManaged(notConfigured.isVisible());

        check.setOnAction(event -> {
            if (!CommunityUpdateNotifier.isConfigured()) {
                return;
            }
            check.setDisable(true);
            check.setText(i18n("community.update.checking"));
            CommunityUpdateNotifier.checkAsync().whenComplete((result, error) ->
                    Schedulers.javafx().execute(() -> {
                        check.setDisable(false);
                        check.setText(i18n("community.update.check"));
                        if (error != null) {
                            LOG.warning("Update check failed", error);
                            return;
                        }
                        Optional<CommunityUpdateNotifier.UpdateInfo> info = result == null
                                ? Optional.empty() : result;
                        if (info.isPresent()) {
                            CommunityUpdateDialog.show(info.get());
                        } else {
                            Controllers.dialog(i18n("community.update.latest"));
                        }
                    }));
        });

        VBox box = new VBox(8, check, notConfigured);
        box.setPadding(new Insets(8, 0, 8, 0));

        ComponentList list = new ComponentList();
        list.getContent().setAll(box);
        return list;
    }

    /// 法律信息卡片。
    @NotNull
    private ComponentList buildLegal() {
        LineButton copyright = new LineButton();
        copyright.setLargeTitle(true);
        copyright.setTitle(i18n("about.copyright"));
        copyright.setSubtitle(i18n("community.about.copyright.statement"));

        LineButton claim = LineButton.createExternalLinkButton(Metadata.EULA_URL);
        claim.setLargeTitle(true);
        claim.setTitle(i18n("about.claim"));
        claim.setSubtitle(i18n("about.claim.statement"));

        LineButton upstream = LineButton.createExternalLinkButton("https://github.com/HMCL-dev/HMCL");
        upstream.setLargeTitle(true);
        upstream.setTitle(i18n("community.about.upstream"));
        upstream.setSubtitle(i18n("community.about.upstream.statement"));

        ComponentList list = new ComponentList();
        list.getContent().setAll(copyright, claim, upstream);
        return list;
    }

    /// 绑定头像；支持字符串与 `{light, dark}` 两种上游格式。
    ///
    /// @param button 目标按钮
    /// @param element 头像 JSON
    private void bindImage(@NotNull LineButton button, @NotNull JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            Image image = loadImage(element.getAsString());
            if (image != null) {
                ImageContainer view = new ImageContainer(32, 32);
                view.setImage(image);
                view.setMouseTransparent(true);
                button.setLeading(view);
            }
        } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.get("light") == null || obj.get("dark") == null) {
                return;
            }
            String light = obj.get("light").getAsString();
            String dark = obj.get("dark").getAsString();
            holder.add(FXUtils.onWeakChangeAndOperate(Themes.darkModeProperty(), darkMode -> {
                Image image = loadImage(darkMode ? dark : light);
                if (image == null) {
                    return;
                }
                ImageContainer view = new ImageContainer(32, 32);
                view.setImage(image);
                view.setMouseTransparent(true);
                button.setLeading(view);
            }));
        }
    }

    /// 加载内置或远程图片。
    ///
    /// @param url 路径或 URL
    /// @return 图片；失败返回 `null`
    @Nullable
    private static Image loadImage(@NotNull String url) {
        try {
            return url.startsWith("/") ? FXUtils.newBuiltinImage(url) : new Image(url);
        } catch (RuntimeException e) {
            LOG.warning("Failed to load about image: " + url, e);
            return null;
        }
    }

    /// 构造空态提示。
    ///
    /// @param text 文案
    /// @return 节点
    @NotNull
    private static Label hint(@NotNull String text) {
        Label label = new Label(text);
        label.getStyleClass().add("subtitle-label");
        label.setWrapText(true);
        label.setPadding(new Insets(12));
        return label;
    }
}
