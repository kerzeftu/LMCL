/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.community.crash.CrashArchiveListener;
import org.jackhuang.hmcl.community.env.CommunityPaths;
import org.jetbrains.annotations.NotNullByDefault;

/// LMCL 的进程入口（shadowJar 的 `Main-Class`）。
///
/// 在**任何上游类初始化之前**钉住进程级属性，然后原样转交给上游
/// [org.jackhuang.hmcl.Main]（保留 `HMCLBoot` 的目录检查与 Java 版本检查）。
///
/// 为什么需要本类：`Metadata` 在静态初始化时读取 `hmcl.update_source.override`，
/// 而 `Metadata` 又会被 `EntryPoint.main` 的 `http.agent` 赋值提前触发；
/// 因此在 `main` 的第一行之后就没有安全的注入点。
///
/// 本类先于 [org.jackhuang.hmcl.Main] 执行，且只使用字符串常量，
/// 不引用 `Metadata` / `IntegrityChecker`（避免提前触发它们的类初始化）。
@NotNullByDefault
public final class CommunityBootstrap {

    /// 上游 `Metadata` 读取的更新源属性名，见 `Metadata.java` 的 `HMCL_UPDATE_URL`。
    private static final String PROP_UPDATE_SOURCE = "hmcl.update_source.override";

    /// 上游 `IntegrityChecker` 读取的自完整性校验开关，见 `IntegrityChecker.java:48`。
    private static final String PROP_DISABLE_INTEGRITY = "hmcl.self_integrity_check.disable";

    /// 开发逃生口：本机调试时才允许外部 `-D` 覆盖上面两个属性，示例：
    /// `-Dhmcl.community.dev=true -Dhmcl.self_integrity_check.disable=true`。
    private static final String PROP_DEV = "hmcl.community.dev";

    /// 更新源路径；官方 `Metadata` 的默认值是 `<PUBLISH_URL>/api/update_link`，这里保持同构，
    /// 但域名固定为官网（见 [CommunityMetadata#WEBSITE]）。
    private static final String UPDATE_SOURCE_PATH = "/api/update_link";

    private CommunityBootstrap() {
    }

    /// 进程入口。
    ///
    /// @param args 原样转交给上游
    /// @throws Throwable 上游 [org.jackhuang.hmcl.Main#main] 声明的异常
    public static void main(String[] args) throws Throwable {
        boolean dev = Boolean.getBoolean(PROP_DEV);

        if (dev) {
            // 开发模式：不覆盖外部 -D，便于用 mitmproxy 验证"官方更新源不会被访问"。
            if (System.getProperty(PROP_UPDATE_SOURCE) == null) {
                System.setProperty(PROP_UPDATE_SOURCE, CommunityMetadata.WEBSITE + UPDATE_SOURCE_PATH);
            }
        } else {
            // 发行模式：更新源恒为官网；自完整性校验恒为关闭（未签名 → 守卫在联网前失败），
            // 外部 -D 覆盖一律无效（见步骤 05 §4.2）。
            System.setProperty(PROP_UPDATE_SOURCE, CommunityMetadata.WEBSITE + UPDATE_SOURCE_PATH);
            System.setProperty(PROP_DISABLE_INTEGRITY, "false");
        }

        // 路径属性必须同样早于 Metadata 初始化（步骤 06）：计算 LMCL 自己的两个数据根。
        // 显式覆盖（-Dhmcl.dir/-Dhmcl.home/HMCL_LOCAL_HOME/HMCL_USER_HOME）优先，不覆盖。
        if (!Boolean.getBoolean(CommunityPaths.PROP_PATHS_EXPLICIT)) {
            CommunityPaths.applySystemProperties();
        }

        // 崩溃归档监听器（步骤 09）：只注册回调，不读取任何上游状态；
        // 失败不能阻塞启动（此时 LOG 可能尚未初始化，故不能记日志）。
        try {
            CrashArchiveListener.install();
        } catch (Throwable ignored) {
        }

        Main.main(args);
    }
}
