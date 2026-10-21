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
package org.jackhuang.hmcl.community.crash;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/// 崩溃历史索引条目（与 `crash-history.json` 的 `entries[]` 一一对应）。
///
/// @param archiveId 归档 id（= 归档目录名）
/// @param at 归档时间
/// @param versionId 实例 id
/// @param gameVersion 游戏版本
/// @param phase 启动期 / 运行期
/// @param reasonRule 命中的上游规则名（未命中为 `null`）
/// @param keywords 关键词兜底
/// @param snapshotId 快照指针
/// @param exported 是否导出过诊断包
/// @param dirName 归档目录名（详情/导出用）
@NotNullByDefault
public record CrashEntry(
        @NotNull String archiveId,
        @NotNull Instant at,
        @NotNull String versionId,
        @NotNull String gameVersion,
        @NotNull LaunchPhase phase,
        @Nullable String reasonRule,
        @NotNull List<String> keywords,
        @Nullable String snapshotId,
        boolean exported,
        @NotNull String dirName) {
}
