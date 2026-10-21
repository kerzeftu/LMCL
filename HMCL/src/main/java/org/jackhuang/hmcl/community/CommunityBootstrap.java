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

import org.jackhuang.hmcl.EntryPoint;
import org.jackhuang.hmcl.community.crash.CrashArchiveListener;
import org.jackhuang.hmcl.community.env.CommunityPaths;
import org.jackhuang.hmcl.community.java.CommunityJavaMirror;
import org.jackhuang.hmcl.community.memory.MemoryReclaimer;
import org.jackhuang.hmcl.community.play.PlayRecordTracker;
import org.jackhuang.hmcl.community.snapshot.SnapshotService;
import org.jackhuang.hmcl.util.SwingUtils;
import org.jetbrains.annotations.NotNullByDefault;

/// LMCL 的进程入口（shadowJar 的 `Main-Class`）。
///
/// 在**任何上游类初始化之前**钉住进程级属性，然后转交给真正的
/// [org.jackhuang.hmcl.EntryPoint]。
///
/// **不能调用** `org.jackhuang.hmcl.Main.main`：上游 `UpdateHandler.isNestedApplication()`
/// 用“`Main.main` 之后是否还有栈帧”判断是否被旧版启动器嵌套调用；本类作为外层入口
/// 必然在 `Main.main` 之下，会被误判并触发 `performMigration()`（尝试自更新，
/// 未签名 jar 直接报 `Signature is missing`）。这里只复刻 `Main` 的路径检查，
/// Java 版本已由 `HMCLBoot`/本类 class 版本保证 ≥17。
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

        // Java 列表镜像（步骤 10）：必须在 DiscoFetchJavaListTask 类初始化之前注入；
        // 镜像未部署（或用户已显式 -D）时不写入，行为与上游一致。
        try {
            CommunityJavaMirror.install();
        } catch (Throwable ignored) {
        }

        // 崩溃归档监听器（步骤 09）：只注册回调，不读取任何上游状态；
        // 失败不能阻塞启动（此时 LOG 可能尚未初始化，故不能记日志）。
        try {
            CrashArchiveListener.install();
        } catch (Throwable ignored) {
        }

        // 内存回收基座（步骤 12）：只创建守护调度线程并监听启动事件，
        // 设置与堆指标延迟到真正需要时再读，避免提前触发 Metadata 初始化。
        try {
            MemoryReclaimer.install();
        } catch (Throwable ignored) {
        }

        // 游玩记录（步骤 15）：恢复上次进程被杀时留下的会话；必须在 UI 之前完成，
        // 以免首页日历先读到旧数据。只读写本地 JSON，失败不阻塞启动。
        try {
            PlayRecordTracker.recoverNow();
        } catch (Throwable ignored) {
        }

        // 实例快照（步骤 16）：清理上次强杀留下的临时目录与无引用对象；
        // 只读本地目录，失败不阻塞启动。
        try {
            SnapshotService.cleanupAtStartup();
        } catch (Throwable ignored) {
        }

        checkDirectoryPath();
        EntryPoint.main(args);
    }

    /// 复刻上游 `Main.checkDirectoryPath()`：路径中的 `!` 会让 JavaFX 资源加载失败。
    ///
    /// 直接调用 `Main.main` 会触发嵌套应用误判，因此只保留这一项必要检查；
    /// Java 版本检查由 stub 与本类 class 版本（Java 17）共同保证。
    private static void checkDirectoryPath() {
        String currentDir = System.getProperty("user.dir", "");
        String jarPath = thisJarPath();
        if (!currentDir.contains("!") && (jarPath == null || !jarPath.contains("!"))) {
            return;
        }

        String message = "Exclamation mark(!) is not allowed in the working path or the launcher path.\n"
                + "Working path: " + currentDir + "\n"
                + "Launcher path: " + jarPath;
        SwingUtils.initLookAndFeel();
        System.err.println(message);
        SwingUtils.showErrorDialog(message);
        System.exit(1);
    }

    /// 当前启动器 jar/exe 的路径，取不到时返回 `null`。
    ///
    /// @return 路径或 `null`
    @org.jetbrains.annotations.Nullable
    private static String thisJarPath() {
        try {
            java.security.ProtectionDomain domain = CommunityBootstrap.class.getProtectionDomain();
            java.security.CodeSource source = domain == null ? null : domain.getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            return java.nio.file.Paths.get(source.getLocation().toURI())
                    .toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return null;
        }
    }
}
