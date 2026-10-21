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

import org.jackhuang.hmcl.community.CommunityConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// 把社区 Java 列表镜像注入上游的 `hmcl.discoapi.override` 属性。
///
/// 为什么必须由 Bootstrap 做：`DiscoFetchJavaListTask.API_ROOT` 是
/// `static final`，在类初始化时读一次系统属性；一旦该类被加载就无法再改。
///
/// 约束：只补缺省，**不覆盖**用户显式传入的 `-Dhmcl.discoapi.override=...`
/// （便于排查与自建镜像）。
@NotNullByDefault
public final class CommunityJavaMirror {

    private CommunityJavaMirror() {
    }

    /// 幂等安装：镜像已配置且用户未显式指定属性时，写入系统属性。
    public static void install() {
        if (System.getProperty(CommunityConfig.PROP_DISCO_API) != null)
            return;

        String url = CommunityConfig.javaMirrorUrl();
        if (url != null)
            System.setProperty(CommunityConfig.PROP_DISCO_API, url);
    }

    /// 返回当前生效的 Disco API 根地址。
    ///
    /// 优先取系统属性（用户显式覆盖或本类注入），否则取上游默认值。
    ///
    /// @return API 根地址
    @NotNull
    public static String effectiveApiRoot() {
        String override = System.getProperty(CommunityConfig.PROP_DISCO_API);
        return override != null ? override : CommunityConfig.UPSTREAM_DISCO_API;
    }

    /// 当前是否由本类注入了镜像地址。
    ///
    /// @return 已注入返回 `true`
    public static boolean isInjected() {
        return CommunityConfig.javaMirrorUrl() != null
                && CommunityConfig.DISCO_API_OVERRIDE.equals(System.getProperty(CommunityConfig.PROP_DISCO_API));
    }

    /// 返回显式覆盖的系统属性值（仅用于展示/排查）。
    ///
    /// @return 属性值或 `null`
    @Nullable
    public static String explicitOverride() {
        return System.getProperty(CommunityConfig.PROP_DISCO_API);
    }
}
