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

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.CommunityConfig;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.jackhuang.hmcl.download.java.disco.DiscoJavaDistribution;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// 社区版的 Java 发行版偏好（`community-settings.json` 的 `java` 段落）。
///
/// 这是步骤 14 的 `java-recommend` 卡片读取偏好的**唯一入口**：
/// 卡片不得自己解析 `community-settings.json`，写偏好也必须走
/// [#setDistribution(DiscoJavaDistribution)]。
///
/// 语义：
/// - `preferredDistribution` 取值是 [DiscoJavaDistribution] 的枚举名或 `"ANY"`；
///   缺字段 / 非法值一律回退到 [#DEFAULT_DISTRIBUTION]（Zulu）；
/// - `mirrorEnabled` 只是**展示**开关：镜像属性在 Bootstrap 已经注入，
///   运行期无法撤销，本类不做“热切换”的假象；
/// - 读取失败（JSON 损坏等）返回默认值，不向上抛异常。
@NotNullByDefault
public final class JavaPreference {

    /// 偏好所在的设置段名。
    public static final String SECTION = "java";

    /// 偏好字段名。
    public static final String KEY_DISTRIBUTION = "preferredDistribution";

    /// 镜像展示字段名。
    public static final String KEY_MIRROR_ENABLED = "mirrorEnabled";

    /// “不指定发行版”的存储值。
    public static final String VALUE_ANY = "ANY";

    /// 默认偏好发行版。
    public static final DiscoJavaDistribution DEFAULT_DISTRIBUTION = DiscoJavaDistribution.ZULU;

    private JavaPreference() {
    }

    /// 偏好快照。
    ///
    /// @param distribution 偏好发行版；`null` 表示“不指定”（面板按平台自行选择）
    /// @param mirrorEnabled 是否展示“镜像已启用”的说明
    public record Preference(@Nullable DiscoJavaDistribution distribution, boolean mirrorEnabled) {

        /// 返回用于存储的字符串值。
        ///
        /// @return 枚举名或 `"ANY"`
        @NotNull
        public String storageValue() {
            return distribution == null ? VALUE_ANY : distribution.name();
        }

        /// 返回对应的 UI 选项。
        ///
        /// @return 选项
        @NotNull
        public JavaChoice choice() {
            return JavaChoice.of(distribution);
        }
    }

    /// 读取全局偏好。
    ///
    /// @return 偏好快照，永不返回 `null`
    @NotNull
    public static Preference get() {
        return get(CommunitySettings.get());
    }

    /// 从指定设置实例读取偏好（测试/迁移用）。
    ///
    /// @param settings 设置实例
    /// @return 偏好快照
    @NotNull
    static Preference get(@NotNull CommunitySettings settings) {
        JsonObject section;
        try {
            section = settings.section(SECTION);
        } catch (RuntimeException e) {
            return defaultPreference();
        }

        String value = JsonUtils.getString(section, KEY_DISTRIBUTION);
        DiscoJavaDistribution distribution;
        if (value == null || value.isBlank()) {
            distribution = DEFAULT_DISTRIBUTION;
        } else if (VALUE_ANY.equalsIgnoreCase(value.trim())) {
            distribution = null;
        } else {
            distribution = DiscoJavaDistribution.of(value.trim());
            if (distribution == null)
                distribution = DEFAULT_DISTRIBUTION;
        }

        boolean mirrorEnabled = JsonUtils.getBoolean(section, KEY_MIRROR_ENABLED,
                CommunityConfig.isJavaMirrorConfigured());

        return new Preference(distribution, mirrorEnabled);
    }

    /// 写入侵偏好并立即落盘（只改 `java` 段，不动其它段）。
    ///
    /// @param distribution 目标发行版；`null` 表示不指定
    public static void setDistribution(@Nullable DiscoJavaDistribution distribution) {
        setDistribution(CommunitySettings.get(), distribution);
    }

    /// 从指定设置实例写偏好（测试用）。
    ///
    /// @param settings 设置实例
    /// @param distribution 目标发行版；`null` 表示不指定
    static void setDistribution(@NotNull CommunitySettings settings, @Nullable DiscoJavaDistribution distribution) {
        JsonObject section = settings.section(SECTION).deepCopy();
        section.addProperty(KEY_DISTRIBUTION, distribution == null ? VALUE_ANY : distribution.name());
        if (!section.has(KEY_MIRROR_ENABLED))
            section.addProperty(KEY_MIRROR_ENABLED, CommunityConfig.isJavaMirrorConfigured());
        settings.putSection(SECTION, section);
    }

    /// 写入完整偏好（发行版 + 镜像展示开关）并立即落盘。
    ///
    /// @param preference 目标偏好；不得为 `null`
    public static void set(@NotNull Preference preference) {
        set(CommunitySettings.get(), preference);
    }

    /// 从指定设置实例写入完整偏好（测试/导入用）。
    ///
    /// @param settings 设置实例
    /// @param preference 目标偏好
    static void set(@NotNull CommunitySettings settings, @NotNull Preference preference) {
        JsonObject section = settings.section(SECTION).deepCopy();
        section.addProperty(KEY_DISTRIBUTION, preference.storageValue());
        section.addProperty(KEY_MIRROR_ENABLED, preference.mirrorEnabled());
        settings.putSection(SECTION, section);
    }

    /// 返回默认偏好（默认 Zulu，镜像按部署配置决定）。
    ///
    /// @return 默认偏好
    @NotNull
    public static Preference defaultPreference() {
        return new Preference(DEFAULT_DISTRIBUTION, CommunityConfig.isJavaMirrorConfigured());
    }
}
