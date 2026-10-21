/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jetbrains.annotations.NotNullByDefault;

/// 社区版对外展示的元信息；只读、无 IO、无异常。
///
/// 数据来源与 `Metadata.VERSION` 相同：jar 内 `assets/hmcl.properties`，
/// 因此不要为本类新增配置文件。
@NotNullByDefault
public final class CommunityMetadata {
    private CommunityMetadata() {
    }

    /// 源码仓库地址；发布前替换为真实 URL。
    public static final String SOURCE_URL = "https://github.com/lmcl-dev/lmcl"; // TODO(lmcl): 仓库定案后替换

    /// GPLv3 附加条款文件在仓库内的地址；发布前与 `SOURCE_URL` 同批替换。
    public static final String LICENSE_URL = SOURCE_URL + "/blob/main/LICENSE-ADDITIONAL-TERMS.md";

    /// 产品名（来自 `Metadata.NAME`）。
    public static String productName() {
        return Metadata.NAME;
    }

    /// 产品完整名（来自 `Metadata.FULL_NAME`）。
    public static String fullName() {
        return Metadata.FULL_NAME;
    }

    /// 当前版本号（构建期 `hmcl.version`，运行期可被 `hmcl.version.override` 覆盖）。
    public static String version() {
        return Metadata.VERSION;
    }

    /// 上游基线 tag，例如 `v3.16.4`；属性缺失时返回 `unknown`。
    public static String baseline() {
        return JarUtils.getAttribute("hmcl.community.baseline", "unknown");
    }

    /// 上游基线 commit id；属性缺失时返回 `unknown`。
    public static String baselineCommit() {
        return JarUtils.getAttribute("hmcl.community.baseline.commit", "unknown");
    }

    /// 源码地址。
    public static String sourceUrl() {
        return SOURCE_URL;
    }

    /// 附加条款地址。
    public static String licenseUrl() {
        return LICENSE_URL;
    }
}
