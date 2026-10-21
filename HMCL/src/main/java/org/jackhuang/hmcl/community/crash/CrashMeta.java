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

/// 写入归档 `meta.json` 的摘要（步骤 09 §3.6）。
///
/// @param versionId 实例 id
/// @param gameVersion 游戏版本（可空时传空串）
/// @param phase 崩溃阶段
/// @param quickCrash 是否为启动期崩溃
/// @param summary i18n key（不是成品文案）
/// @param rules 命中的上游规则名
/// @param keywords 未命中规则时的关键词
/// @param snapshotId 快照指针（16 未实现时为 `null`）
/// @param exported 是否导出过诊断包
/// @param at 归档时间
@NotNullByDefault
public record CrashMeta(
        @NotNull String versionId,
        @NotNull String gameVersion,
        @NotNull LaunchPhase phase,
        boolean quickCrash,
        @NotNull String summary,
        @NotNull List<String> rules,
        @NotNull List<String> keywords,
        @Nullable String snapshotId,
        boolean exported,
        @NotNull Instant at) {
}
