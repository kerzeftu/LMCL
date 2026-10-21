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
package org.jackhuang.hmcl.community.java;

import com.jfoenix.controls.JFXComboBox;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LinePane;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// 社区设置里的“Java”分组：只放发行版偏好与镜像说明。
///
/// 真正的列表/安装入口在首页仪表盘的 `java-recommend` 卡片（[JavaRecommendPane]）里，
/// 避免设置页承担网络请求。
@NotNullByDefault
public final class CommunityJavaSettingsPane extends VBox {

    /// 构造分组内容。
    public CommunityJavaSettingsPane() {
        getStyleClass().add("community-java-settings");
        setSpacing(8);

        ComponentList list = new ComponentList();

        LinePane preferenceRow = new LinePane();
        preferenceRow.setTitle(i18n("community.java.preference"));
        preferenceRow.setSubtitle(i18n("community.java.preference.hint"));

        JFXComboBox<JavaChoice> preferenceBox = new JFXComboBox<>();
        preferenceBox.setItems(FXCollections.observableArrayList(JavaChoice.values()));
        preferenceBox.setConverter(FXUtils.stringConverter(choice -> i18n(choice.i18nKey())));
        preferenceBox.getSelectionModel().select(JavaPreference.get().choice());
        FXUtils.setLimitWidth(preferenceBox, 180);
        preferenceBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null)
                JavaPreference.setDistribution(newValue.distribution());
        });
        preferenceRow.setRight(preferenceBox);

        list.getContent().add(preferenceRow);

        Label mirrorNote = new Label(i18n("community.java.mirror.note", CommunityJavaMirror.effectiveApiRoot()));
        mirrorNote.getStyleClass().addAll("hint", "info");
        mirrorNote.setWrapText(true);

        Label limitNote = new Label(i18n("community.java.limit.upstream_dialog"));
        limitNote.getStyleClass().add("hint");
        limitNote.setWrapText(true);

        getChildren().setAll(list, mirrorNote, limitNote);
    }
}
