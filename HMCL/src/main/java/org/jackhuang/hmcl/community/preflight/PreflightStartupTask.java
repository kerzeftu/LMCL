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
package org.jackhuang.hmcl.community.preflight;

import javafx.application.Platform;
import org.jackhuang.hmcl.community.launch.LaunchContext;
import org.jackhuang.hmcl.community.launch.LaunchStartupTask;
import org.jackhuang.hmcl.community.ui.PreflightDialog;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 启动链上的体检任务（`order = 100`，插入在登录之前）。
///
/// 交互式启动时，存在未静音的 WARN/ERROR 会弹窗等待用户决定：
/// - “仍要启动”/“不再提示” → 继续；
/// - “返回修复”/关闭窗口 → 抛 [CancellationException] 取消本次启动。
///
/// 非交互式启动（生成启动脚本）只记录日志，不弹窗。
@NotNullByDefault
public final class PreflightStartupTask implements LaunchStartupTask {

    /// 任务 id。
    public static final String ID = "preflight";

    /// 弹窗等待上限（秒）；超时按“仍要启动”处理，避免无人值守时卡住启动。
    private static final long DIALOG_TIMEOUT_SECONDS = 60;

    /// 构造任务。
    public PreflightStartupTask() {
    }

    /// 返回任务 id。
    ///
    /// @return id
    @Override
    @NotNull
    public String id() {
        return ID;
    }

    /// 返回执行顺序。
    ///
    /// @return 100
    @Override
    public int order() {
        return 100;
    }

    /// 返回展示名。
    ///
    /// @return 展示名
    @Override
    @NotNull
    public String name() {
        return "Preflight";
    }

    /// 执行体检。
    ///
    /// @param context 启动上下文
    /// @return 异步任务
    @Override
    @NotNull
    public Task<?> run(@NotNull LaunchContext context) {
        return Task.runAsync("community: preflight", Schedulers.io(), () -> runNow(context));
    }

    /// 同步执行体检与交互。
    ///
    /// @param context 启动上下文
    /// @throws CancellationException 用户选择返回修复
    public void runNow(@NotNull LaunchContext context) throws CancellationException {
        PreflightReport report = PreflightService.runAndPersist(context);
        PreflightConfig config = PreflightConfig.load();
        List<CheckResult> visible = new ArrayList<>();
        for (CheckResult check : config.visibleChecks(report)) {
            if (check.severity() == Severity.WARN || check.severity() == Severity.ERROR) {
                visible.add(check);
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        if (!context.interactive()) {
            for (CheckResult check : visible) {
                LOG.warning("Preflight " + check.id() + ": " + check.code() + " " + check.details());
            }
            return;
        }

        CompletableFuture<Boolean> decision = new CompletableFuture<>();
        Platform.runLater(() -> new PreflightDialog(report).show(decision::complete));
        boolean proceed;
        try {
            proceed = decision.get(DIALOG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOG.warning("Preflight dialog timed out, continue launching");
            proceed = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proceed = true;
        } catch (Throwable e) {
            LOG.warning("Preflight dialog failed", e);
            proceed = true;
        }

        if (!proceed) {
            throw new CancellationException("preflight aborted by user");
        }
    }
}
