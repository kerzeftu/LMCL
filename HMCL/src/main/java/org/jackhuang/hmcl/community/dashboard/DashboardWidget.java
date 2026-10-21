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
package org.jackhuang.hmcl.community.dashboard;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.task.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 仪表盘组件基类。子类实现 [DashboardWidget#createContent()] 与（可选）
/// [DashboardWidget#refreshAsync()]。
///
/// 生命周期：
/// - 构造发生在 FX 线程；`createContent()` 失败会被捕获并替换为错误卡片；
/// - 挂载后由网格调用一次 [DashboardWidget#requestRefresh()]；
/// - 隐藏/删除/退出时调用 [DashboardWidget#dispose()]，之后迟到的后台回调会被丢弃。
///
/// 线程纪律：组件内**禁止**自建线程/FX 线程同步 I/O；后台数据用 [DashboardWidget#loadAsync]。
@NotNullByDefault
public abstract class DashboardWidget extends VBox {

    /// 组件描述。
    private final DashboardWidgetDescriptor descriptor;

    /// 组件上下文。
    protected final DashboardContext context;

    /// 释放回调（监听器/handler）。
    private final List<Runnable> disposables = new ArrayList<>();

    /// 内容容器：`createContent()` 的结果放在这里，错误卡片也替换这里。
    private final VBox contentBox = new VBox();

    /// 是否已释放；迟到回调据此丢弃。
    protected volatile boolean disposed;

    /// 内容是否已构建（[DashboardWidget#initialize()]）。
    private boolean initialized;

    /// 是否有一次刷新正在进行（同一时刻最多一个）。
    private final AtomicBoolean refreshing = new AtomicBoolean();

    /// 在飞的 `loadAsync` 数量，用于判断刷新何时真正结束。
    private final AtomicInteger pendingLoads = new AtomicInteger();

    /// 创建组件骨架并立即构建内容。
    ///
    /// @param descriptor 组件描述
    /// @param context 组件上下文
    protected DashboardWidget(
            @NotNull DashboardWidgetDescriptor descriptor,
            @NotNull DashboardContext context) {
        this.descriptor = descriptor;
        this.context = context;

        getStyleClass().add("card");
        setSpacing(8);
        setFillWidth(true);
        setMinHeight(0);

        Label title = new Label(i18n(descriptor.nameKey()));
        title.getStyleClass().add("title-label");
        title.setWrapText(true);

        contentBox.setSpacing(8);
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        getChildren().addAll(title, contentBox);
        if (descriptor.descriptionKey() != null) {
            Label description = new Label(i18n(descriptor.descriptionKey()));
            description.getStyleClass().add("subtitle-label");
            description.setWrapText(true);
            getChildren().add(1, description);
        }

    }

    /// 首次挂载时构建内容。
    ///
    /// 推迟到构造完成之后，子类才可以在 `createContent()` 里安全读取自己的字段。
    public final void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Node content = createContent();
            contentBox.getChildren().setAll(content);
            VBox.setVgrow(content, Priority.ALWAYS);
        } catch (Throwable e) {
            DashboardErrors.show(contentBox, e);
        }
    }

    /// 返回组件描述。
    ///
    /// @return 描述
    @NotNull
    public final DashboardWidgetDescriptor descriptor() {
        return descriptor;
    }

    /// 创建静态内容（FX 线程；禁止 I/O、禁止 sleep）。
    ///
    /// @return 内容节点
    @NotNull
    protected abstract Node createContent();

    /// 异步刷新数据；默认空实现。
    protected void refreshAsync() {
    }

    /// 请求一次刷新；正在刷新时忽略（每组件并发刷新上限 1）。
    public final void requestRefresh() {
        if (disposed) {
            return;
        }
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            refreshAsync();
        } catch (Throwable e) {
            refreshing.set(false);
            DashboardErrors.show(contentBox, e);
        } finally {
            // refreshAsync 可能没有发起任何 loadAsync（同步组件），此时立即复位。
            if (pendingLoads.get() == 0) {
                refreshing.set(false);
            }
        }
    }

    /// 隐藏/删除/退出时调用；子类覆写必须调用 `super.dispose()`。
    public void dispose() {
        disposed = true;
        for (Runnable runnable : disposables) {
            try {
                runnable.run();
            } catch (Throwable e) {
                LOG.warning("Failed to dispose dashboard widget " + descriptor.id(), e);
            }
        }
        disposables.clear();
    }

    /// 登记随组件释放的 listener/handler。
    ///
    /// @param r 释放回调
    protected final void onDispose(@NotNull Runnable r) {
        disposables.add(r);
    }

    /// 后台执行 io，结果回到 FX 线程；异常统一转错误卡片。
    ///
    /// @param io 后台任务
    /// @param ui 使用结果的 FX 回调
    /// @param <T> 结果类型
    protected final <T> void loadAsync(@NotNull Supplier<T> io, @NotNull Consumer<T> ui) {
        pendingLoads.incrementAndGet();
        CompletableFuture.supplyAsync(io, Schedulers.io())
                .whenComplete((value, error) -> Schedulers.javafx().execute(() -> {
                    try {
                        if (disposed) {
                            return;
                        }
                        if (error != null) {
                            DashboardErrors.show(contentBox, error);
                        } else {
                            ui.accept(value);
                        }
                    } finally {
                        pendingLoads.decrementAndGet();
                        if (pendingLoads.get() == 0) {
                            refreshing.set(false);
                        }
                    }
                }));
    }

    /// 返回内容容器的只读引用，供子类追加/替换节点。
    ///
    /// @return 内容容器
    @NotNull
    protected final VBox contentBox() {
        return contentBox;
    }

    /// 在内容区展示错误卡片（子类可显式调用）。
    ///
    /// @param message 用户可读的错误说明
    protected final void showError(@NotNull String message) {
        DashboardErrors.show(contentBox, message);
    }
}
