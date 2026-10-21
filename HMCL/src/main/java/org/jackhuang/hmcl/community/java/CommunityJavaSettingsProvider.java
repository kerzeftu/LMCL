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

import javafx.scene.Parent;
import org.jackhuang.hmcl.community.ui.SettingsSection;
import org.jackhuang.hmcl.community.ui.SettingsSectionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.Supplier;

/// 在社区版设置面板中注册“Java”分组。
///
/// 分组顺序 25（下载源 15、数据 20、关于 30 之间）。
@NotNullByDefault
public final class CommunityJavaSettingsProvider implements SettingsSectionProvider {

    /// 分组 id。
    public static final String SECTION_ID = "java";

    /// 分组显示名 i18n key。
    public static final String I18N_KEY = "community.settings.tab.java";

    /// 分组顺序。
    public static final int ORDER = 25;

    /// 构造 provider。
    public CommunityJavaSettingsProvider() {
    }

    /// 返回 Java 分组。
    ///
    /// @return 只含一个分组的不可变列表
    @Override
    @NotNull
    @Unmodifiable
    public List<SettingsSection> sections() {
        Supplier<@NotNull Parent> content = CommunityJavaSettingsPane::new;
        return List.of(new SettingsSection(SECTION_ID, I18N_KEY, ORDER, content));
    }
}
