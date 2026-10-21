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
package org.jackhuang.hmcl.community.snapshot;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

/// 快照产生的原因。
///
/// 持久化到 `manifest.json` 的字符串由 [SnapshotManifest] 手工转换，避免上游
/// `LowerCaseEnumTypeAdapterFactory` 把常量名写成 `auto_start` 之类的形式。
@NotNullByDefault
public enum SnapshotReason {

    /// 一次成功启动退出后的自动采集。
    AUTO_START("success"),

    /// 回滚前对当前状态的自动采集，用于撤销本次回滚。
    PRE_RESTORE("pre-restore"),

    /// 用户点击“标记当前为可用”。
    MANUAL("manual");

    private final String jsonName;

    SnapshotReason(@NotNull String jsonName) {
        this.jsonName = jsonName;
    }

    /// 返回 manifest 中的字符串值。
    ///
    /// @return 字符串值
    @NotNull
    public String jsonName() {
        return jsonName;
    }

    /// 解析 manifest 中的字符串值；未知值按 [MANUAL] 处理。
    ///
    /// @param value 字符串值
    /// @return 原因
    @NotNull
    public static SnapshotReason fromJson(@NotNull String value) {
        for (SnapshotReason reason : values()) {
            if (reason.jsonName.equalsIgnoreCase(value)) {
                return reason;
            }
        }
        return MANUAL;
    }
}
