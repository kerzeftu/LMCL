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
package org.jackhuang.hmcl.community;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// LMCL 的部署侧常量（域名、镜像地址等）。
///
/// 这些值随部署迁移，集中放在这里，避免散落在 UI 与 Bootstrap 中。
@NotNullByDefault
public final class CommunityConfig {

    /// 上游 Disco API 根地址（`DiscoFetchJavaListTask.API_ROOT` 的默认值）。
    public static final String UPSTREAM_DISCO_API = "https://api.foojay.io/disco/v3.0";

    /// 上游读取的 Disco API 覆盖属性名。
    public static final String PROP_DISCO_API = "hmcl.discoapi.override";

    /// 社区 Java 列表镜像地址（反代 foojay disco v3 的 API 根）。
    ///
    /// 为空表示**镜像尚未部署**：此时不注入属性，行为与上游完全一致（走官方 `api.foojay.io`），
    /// 避免把用户导向一个不存在的端点。部署侧反向代理就绪后，只需把这里改成
    /// `https://java.<社区域名>/disco/v3.0` 即可；注入逻辑见
    /// [org.jackhuang.hmcl.community.java.CommunityJavaMirror]。
    public static final String DISCO_API_OVERRIDE = "";

    private CommunityConfig() {
    }

    /// 社区 Java 镜像是否已配置（即部署侧地址是否已填写）。
    ///
    /// @return 已配置返回 `true`
    public static boolean isJavaMirrorConfigured() {
        return !DISCO_API_OVERRIDE.isBlank();
    }

    /// 返回社区 Java 镜像地址；未配置时返回 `null`。
    ///
    /// @return 镜像地址或 `null`
    @Nullable
    public static String javaMirrorUrl() {
        return isJavaMirrorConfigured() ? DISCO_API_OVERRIDE : null;
    }
}
