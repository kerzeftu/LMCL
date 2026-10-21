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
package org.jackhuang.hmcl.community.env;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/// 计算 LMCL 自己的两个数据根目录，并在上游 `Metadata` 类初始化之前把结果写入
/// `hmcl.dir` / `hmcl.home` 系统属性。
///
/// - 本地（工作区）根目录：`<启动器目录>/.lmcl`，不可写时降级为 `<用户根>/local`；
/// - 用户（共享）根目录：Windows `%APPDATA%/LMCL`、Linux/BSD `$XDG_DATA_HOME/LMCL` 或
///   `~/.local/share/LMCL`、macOS `~/Library/Application Support/LMCL`。
///
/// 显式覆盖优先：`-Dhmcl.dir` / `HMCL_LOCAL_HOME` / `-Dhmcl.home` / `HMCL_USER_HOME`
/// 任一存在时该根目录**完全不改**，用于测试与绿色部署。
///
/// 本类故意不引用 `org.jackhuang.hmcl.Metadata`：它必须在 `Metadata` 类初始化之前执行，
/// 引用会导致过早初始化、属性注水失效。也不依赖 JavaFX，可直接单测。
@NotNullByDefault
public final class CommunityPaths {

    /// 产品目录名；改动等于改变用户数据位置，发布后冻结。
    public static final String PRODUCT_DIR_NAME = "LMCL";

    /// 本地目录名（便携语义，放在启动器目录下）。
    public static final String LOCAL_DIR_NAME = ".lmcl";

    /// 本地根降级到用户根时使用的子目录名。
    public static final String LOCAL_FALLBACK_DIR_NAME = "local";

    /// 显式关闭路径计算的属性名（调试/极端场景）。
    public static final String PROP_PATHS_EXPLICIT = "hmcl.community.paths.explicit";

    /// 路径计算失败、回退到上游默认目录时置为 `true`。
    public static final String PROP_PATHS_FALLBACK = "community.paths.fallback";

    /// 上游读取的本地目录属性名。
    private static final String PROP_LOCAL = "hmcl.dir";

    /// 上游读取的用户目录属性名。
    private static final String PROP_USER = "hmcl.home";

    /// 上游读取的本地目录环境变量名。
    private static final String ENV_LOCAL = "HMCL_LOCAL_HOME";

    /// 上游读取的用户目录环境变量名。
    private static final String ENV_USER = "HMCL_USER_HOME";

    /// 本类独立日志，避免依赖上游 `Logger` 的类初始化。
    private static final Logger LOG = Logger.getLogger("lmcl.paths");

    /// 本类只提供静态方法。
    private CommunityPaths() {
    }

    /// 计算本地根目录：显式覆盖 → `<启动器目录>/.lmcl` → 不可写时 `<用户根>/local`。
    ///
    /// @return 绝对路径（不保证已创建）
    @NotNull
    public static Path localHome() {
        Path explicit = explicitLocalHome();
        if (explicit != null) {
            return explicit;
        }

        Path launcherDir = launcherDirectory();
        Path preferred = launcherDir.resolve(LOCAL_DIR_NAME).toAbsolutePath().normalize();
        if (ensureWritable(preferred)) {
            return preferred;
        }

        Path fallback = userHome().resolve(LOCAL_FALLBACK_DIR_NAME).toAbsolutePath().normalize();
        LOG.warning("Launcher directory is not writable: " + preferred + ", falling back to " + fallback);
        if (!ensureWritable(fallback)) {
            LOG.severe("Fallback local directory is not writable either: " + fallback);
        }
        return fallback;
    }

    /// 计算用户根目录：显式覆盖 → 各平台标准目录下的 [`PRODUCT_DIR_NAME`]。
    ///
    /// @return 绝对路径（不保证已创建）
    @NotNull
    public static Path userHome() {
        Path explicit = explicitUserHome();
        if (explicit != null) {
            return explicit;
        }
        return defaultUserHome(
                System.getProperty("os.name", ""),
                System.getProperty("user.home", "."),
                System.getenv("APPDATA"),
                System.getenv("XDG_DATA_HOME"));
    }

    /// 把两个根目录写入 `hmcl.dir` / `hmcl.home`，并尽力创建目录。
    ///
    /// 幂等；计算失败时设置 [`PROP_PATHS_FALLBACK`] 并保留上游默认，绝不向调用方抛异常。
    public static void applySystemProperties() {
        try {
            applyOne(PROP_LOCAL, ENV_LOCAL, localHome(), "local");
            applyOne(PROP_USER, ENV_USER, userHome(), "user");
        } catch (Throwable e) {
            System.setProperty(PROP_PATHS_FALLBACK, "true");
            LOG.log(Level.WARNING, "Failed to apply LMCL data directories, falling back to upstream defaults", e);
        }
    }

    /// 返回启动器所在目录：jar/exe 的 `CodeSource` 父目录，取不到时退化为 `user.dir`。
    ///
    /// @return 绝对路径
    @NotNull
    public static Path launcherDirectory() {
        try {
            var location = CommunityPaths.class.getProtectionDomain().getCodeSource().getLocation();
            Path path = Path.of(location.toURI()).toAbsolutePath().normalize();
            return Files.isDirectory(path) || path.getParent() == null ? path : path.getParent();
        } catch (URISyntaxException | RuntimeException e) {
            return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        }
    }

    /// 纯函数：按平台规则计算用户根目录，便于测试。
    ///
    /// @param osName    `os.name`（大小写不敏感）
    /// @param userHome  用户主目录
    /// @param appdata   Windows `%APPDATA%`，可为 `null`
    /// @param xdgData   Linux/BSD `$XDG_DATA_HOME`，可为 `null`
    /// @return 绝对路径
    @NotNull
    static Path defaultUserHome(@NotNull String osName,
                                @NotNull String userHome,
                                @Nullable String appdata,
                                @Nullable String xdgData) {
        String os = osName.toLowerCase(Locale.ROOT);
        Path home = Path.of(userHome).toAbsolutePath();
        if (os.contains("win")) {
            Path base = appdata == null || appdata.isBlank() ? home : Path.of(appdata);
            return base.resolve(PRODUCT_DIR_NAME).toAbsolutePath().normalize();
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return home.resolve("Library").resolve("Application Support").resolve(PRODUCT_DIR_NAME)
                    .toAbsolutePath().normalize();
        }
        if (os.contains("linux") || os.contains("freebsd") || os.contains("unix")) {
            Path base = xdgData == null || xdgData.isBlank()
                    ? home.resolve(".local").resolve("share")
                    : Path.of(xdgData);
            return base.resolve(PRODUCT_DIR_NAME).toAbsolutePath().normalize();
        }
        return home.resolve(PRODUCT_DIR_NAME).toAbsolutePath().normalize();
    }

    /// 显式本地根（属性或环境变量），没有则返回 `null`。
    ///
    /// @return 绝对路径或 `null`
    @Nullable
    static Path explicitLocalHome() {
        return explicitPath(System.getProperty(PROP_LOCAL), System.getenv(ENV_LOCAL));
    }

    /// 显式用户根（属性或环境变量），没有则返回 `null`。
    ///
    /// @return 绝对路径或 `null`
    @Nullable
    static Path explicitUserHome() {
        return explicitPath(System.getProperty(PROP_USER), System.getenv(ENV_USER));
    }

    /// 解析显式覆盖值。
    ///
    /// @param property 系统属性值（优先）
    /// @param env      环境变量值（兜底）
    /// @return 绝对路径或 `null`
    @Nullable
    private static Path explicitPath(@Nullable String property, @Nullable String env) {
        String value = property != null && !property.isBlank() ? property : env;
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    /// 写入单个根目录属性；已有显式值时跳过。
    ///
    /// @param property 属性名
    /// @param env      环境变量名
    /// @param path     计算出的路径
    /// @param label    日志用标签
    private static void applyOne(String property, String env, Path path, String label) {
        if (System.getProperty(property) != null || System.getenv(env) != null) {
            return;
        }
        try {
            Files.createDirectories(path);
            hideOnWindows(path);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create " + label + " directory: " + path, e);
        }
        System.setProperty(property, path.toString());
        LOG.info("LMCL " + label + " home: " + path);
    }

    /// 创建目录并验证可写，成功返回 `true`。
    ///
    /// @param dir 目标目录
    /// @return 是否可写
    static boolean ensureWritable(Path dir) {
        try {
            Files.createDirectories(dir);
            Path probe = dir.resolve(".lmcl-write-test");
            Files.writeString(probe, "probe");
            Files.deleteIfExists(probe);
            hideOnWindows(dir);
            return true;
        } catch (FileSystemException e) {
            return false;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /// 在 Windows 上把数据目录标记为隐藏（失败静默）。
    ///
    /// @param dir 目录
    private static void hideOnWindows(Path dir) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return;
        }
        try {
            DosFileAttributeView view = Files.getFileAttributeView(dir, DosFileAttributeView.class);
            if (view != null) {
                view.setHidden(true);
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }
}
