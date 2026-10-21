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

import org.jackhuang.hmcl.download.java.disco.DiscoJavaDistribution;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// “Java 推荐”面板与设置页共用的发行版选项。
///
/// 与 [DiscoJavaDistribution] 的差别只有一项：[#ANY] 表示“不指定”，
/// 面板按上游枚举原序选第一个当前平台可用的发行版。
@NotNullByDefault
public enum JavaChoice {

    /// Azul Zulu（默认偏好）。
    ZULU(DiscoJavaDistribution.ZULU),

    /// Eclipse Temurin（Adoptium）。
    TEMURIN(DiscoJavaDistribution.TEMURIN),

    /// BellSoft Liberica。
    LIBERICA(DiscoJavaDistribution.LIBERICA),

    /// Amazon Corretto。
    CORRETTO(DiscoJavaDistribution.CORRETTO),

    /// 不指定发行版。
    ANY(null);

    private final @Nullable DiscoJavaDistribution distribution;

    JavaChoice(@Nullable DiscoJavaDistribution distribution) {
        this.distribution = distribution;
    }

    /// 返回对应的上游枚举；[#ANY] 返回 `null`。
    ///
    /// @return 上游发行版或 `null`
    @Nullable
    public DiscoJavaDistribution distribution() {
        return distribution;
    }

    /// 返回 i18n key。
    ///
    /// @return `community.java.preference.<name>` 形式的小写 key
    public String i18nKey() {
        return "community.java.preference." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /// 把偏好值映射为选项；`null` 映射为 [#ANY]。
    ///
    /// @param distribution 偏好发行版，允许 `null`
    /// @return 对应选项
    public static JavaChoice of(@Nullable DiscoJavaDistribution distribution) {
        if (distribution == null)
            return ANY;
        for (JavaChoice choice : values()) {
            if (choice.distribution == distribution)
                return choice;
        }
        return ANY;
    }
}
