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
package org.jackhuang.hmcl.community.jvm;

import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

/// JVM 参数向导的**唯一写入入口**。
///
/// 所有写入都经过 [GameSettings.Instance#jvmOptionsProperty()]，
/// 由上游 `HMCLGameRepository` 的实例设置监听器自动落盘，
/// 与“用户手动编辑实例设置”完全同源。
///
/// 保护：
/// - 实例设置为只读（schema 不支持）时返回 [#Status#READ_ONLY_INSTANCE]，不写；
/// - 只合并/移除“向导生成的片段”，不覆盖用户手填的其它参数；
/// - “清除”只在当前值与生成值完全一致时执行，避免误删用户后来追加的参数。
@NotNullByDefault
public final class JvmArgWriter {

    private JvmArgWriter() {
    }

    /// 写入结果状态。
    public enum Status {
        /// 已写入。
        APPLIED,
        /// 实例设置为只读，未写入。
        READ_ONLY_INSTANCE,
        /// 拿不到实例设置对象，未写入。
        SETTING_UNAVAILABLE
    }

    /// 写入结果。
    ///
    /// @param status 状态
    /// @param generated 本次生成（或期望清除的）参数串
    public record Result(@NotNull Status status, @NotNull String generated) {

        /// 是否成功写入。
        ///
        /// @return `status == APPLIED`
        public boolean applied() {
            return status == Status.APPLIED;
        }
    }

    /// 按档位生成要写入的 `jvmOptions` 片段。
    ///
    /// @param preset 档位
    /// @param heapMb 堆大小（MiB）
    /// @param extraArgs 额外参数（如 JEP 346 三项）；为空表示不追加
    /// @return 参数串；`UPSTREAM` / `CUSTOM` 返回空串
    @NotNull
    public static String compose(@NotNull JvmPreset preset, int heapMb, @Nullable @Unmodifiable List<String> extraArgs) {
        if (preset != JvmPreset.G1 && preset != JvmPreset.ZGC) {
            return "";
        }
        int heap = Math.max(256, heapMb);
        StringBuilder builder = new StringBuilder()
                .append("-Xms").append(heap).append('m')
                .append(' ')
                .append("-Xmx").append(heap).append('m');
        if (preset == JvmPreset.ZGC) {
            builder.append(" -XX:+UseZGC");
        }
        if (extraArgs != null) {
            for (String arg : extraArgs) {
                if (arg != null && !arg.isBlank()) {
                    builder.append(' ').append(arg.trim());
                }
            }
        }
        return builder.toString();
    }

    /// 读取实例当前的 `jvmOptions`。
    ///
    /// @param repository 实例仓库
    /// @param instanceId 实例 id
    /// @return 当前值；拿不到设置时 `null`
    @Nullable
    public static String read(@NotNull HMCLGameRepository repository, @NotNull String instanceId) {
        GameSettings.Instance setting = repository.getInstanceGameSettingsOrCreate(instanceId);
        return setting == null ? null : setting.jvmOptionsProperty().getValue();
    }

    /// 写入向导生成的参数片段。
    ///
    /// @param repository 实例仓库
    /// @param instanceId 实例 id
    /// @param preset 档位
    /// @param heapMb 堆大小（MiB）
    /// @param extraArgs 额外参数（JEP 346），可为 `null`
    /// @param previousGenerated 上一次向导生成的片段；用于先移除再写入，可为 `null`
    /// @return 写入结果
    @NotNull
    public static Result apply(
            @NotNull HMCLGameRepository repository,
            @NotNull String instanceId,
            @NotNull JvmPreset preset,
            int heapMb,
            @Nullable @Unmodifiable List<String> extraArgs,
            @Nullable String previousGenerated
    ) {
        String generated = compose(preset, heapMb, extraArgs);
        if (repository.isInstanceGameSettingsReadOnly(instanceId)) {
            return new Result(Status.READ_ONLY_INSTANCE, generated);
        }
        GameSettings.Instance setting = repository.getInstanceGameSettingsOrCreate(instanceId);
        if (setting == null) {
            return new Result(Status.SETTING_UNAVAILABLE, generated);
        }
        String current = setting.jvmOptionsProperty().getValue();
        String merged = merge(current, previousGenerated, generated);
        if (!merged.equals(current == null ? "" : current)) {
            setting.jvmOptionsProperty().setValue(merged);
        }
        return new Result(Status.APPLIED, generated);
    }

    /// 移除指定元素（冲突列表的“一键移除”）。
    ///
    /// @param repository 实例仓库
    /// @param instanceId 实例 id
    /// @param arg 要移除的参数
    /// @return 是否写入
    public static boolean removeArg(@NotNull HMCLGameRepository repository, @NotNull String instanceId, @NotNull String arg) {
        if (repository.isInstanceGameSettingsReadOnly(instanceId)) {
            return false;
        }
        GameSettings.Instance setting = repository.getInstanceGameSettingsOrCreate(instanceId);
        if (setting == null) {
            return false;
        }
        String current = setting.jvmOptionsProperty().getValue();
        String next = removeToken(current, arg);
        if (next.equals(current == null ? "" : current)) {
            return false;
        }
        setting.jvmOptionsProperty().setValue(next);
        return true;
    }

    /// 清除向导生成的参数片段。
    ///
    /// 仅当当前值与 `generated` 完全一致时才清空；否则返回 `false`，由 UI 禁用按钮。
    ///
    /// @param repository 实例仓库
    /// @param instanceId 实例 id
    /// @param generated 向导生成值
    /// @return 是否执行了清除
    public static boolean clear(
            @NotNull HMCLGameRepository repository,
            @NotNull String instanceId,
            @Nullable String generated
    ) {
        if (generated == null || generated.isBlank()) {
            return false;
        }
        if (repository.isInstanceGameSettingsReadOnly(instanceId)) {
            return false;
        }
        GameSettings.Instance setting = repository.getInstanceGameSettingsOrCreate(instanceId);
        if (setting == null) {
            return false;
        }
        String current = setting.jvmOptionsProperty().getValue();
        if (current == null || !current.trim().equals(generated.trim())) {
            return false;
        }
        setting.jvmOptionsProperty().setValue("");
        return true;
    }

    /// 合并“用户已有参数”与“向导生成片段”。
    ///
    /// 先移除上一次生成片段，再追加新片段；用户手填的其它参数保持原样。
    ///
    /// @param current 当前参数串
    /// @param previousGenerated 上一次生成片段，可为 `null`
    /// @param generated 新生成片段
    /// @return 合并后的参数串
    @NotNull
    public static String merge(@Nullable String current, @Nullable String previousGenerated, @NotNull String generated) {
        String base = current == null ? "" : current.trim();
        if (previousGenerated != null && !previousGenerated.isBlank()) {
            base = strip(base, previousGenerated);
        }
        if (generated.isBlank()) {
            return base;
        }
        return base.isBlank() ? generated : base + " " + generated;
    }

    /// 从参数串中按 token 移除一段生成片段（保持其余 token 顺序）。
    ///
    /// @param current 当前参数串
    /// @param generated 生成片段
    /// @return 移除后的参数串
    @NotNull
    public static String strip(@Nullable String current, @NotNull String generated) {
        List<String> currentTokens = new ArrayList<>(JvmArgValidator.tokenize(current));
        List<String> generatedTokens = JvmArgValidator.tokenize(generated);
        if (generatedTokens.isEmpty()) {
            return String.join(" ", currentTokens);
        }
        for (String token : generatedTokens) {
            currentTokens.remove(token);
        }
        return String.join(" ", currentTokens);
    }

    /// 移除参数串中的某一个 token（所有出现位置）。
    ///
    /// @param current 当前参数串
    /// @param arg 要移除的 token
    /// @return 移除后的参数串
    @NotNull
    public static String removeToken(@Nullable String current, @NotNull String arg) {
        List<String> tokens = new ArrayList<>(JvmArgValidator.tokenize(current));
        tokens.removeIf(arg::equals);
        return String.join(" ", tokens);
    }
}
