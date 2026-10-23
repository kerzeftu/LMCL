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
package org.jackhuang.hmcl.community.quote;

import org.jackhuang.hmcl.task.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 每日一言的读取、联网与降级逻辑。
///
/// 约定：
/// - [DailyQuoteService#refresh(boolean, Consumer)] 在 io 线程读取配置/缓存/网络，回调切回 FX；
/// - 自动刷新只由主页的计时器在“可见 + 窗口聚焦”时调用；
/// - 网络失败不抛给 UI，回退内置静态语料，并设置退避时间；关闭联网时使用缓存或内置语料；
/// - 多个启用源每次随机选择一个；缓存 TTL 为 10 分钟。
@NotNullByDefault
public final class DailyQuoteService {

    /// 刷新结果。
    ///
    /// @param quote 实际展示的短句
    /// @param fromNetwork 是否来自本次联网
    /// @param fromCache 是否来自本地缓存
    /// @param error 网络/解析错误；成功后为 `null`
    public record Result(
            @NotNull DailyQuote quote,
            boolean fromNetwork,
            boolean fromCache,
            @Nullable Throwable error) {
    }

    /// 一次请求使用的配置代数与抓取结果。
    private record FetchOutcome(long generation, @NotNull Result result) {
    }

    private final DailyQuoteStore store;
    private final Function<QuoteSource, QuoteProvider> providerFactory;
    private final Clock clock;
    private final AtomicBoolean inFlight = new AtomicBoolean();

    private volatile DailyQuoteSettings.Config config;
    private volatile long generation;
    private volatile int failures;
    private volatile Instant nextAttempt = Instant.EPOCH;

    /// 使用全局存储、默认 provider 与系统时钟。
    public DailyQuoteService() {
        // 构造发生在 FX 线程；这里只使用内存默认值，磁盘读取放到 reloadConfigAsync/refresh。
        this(DailyQuoteStore.get(), QuoteProviderFactory::create, Clock.systemDefaultZone(),
                DailyQuoteSettings.defaults());
    }

    /// 注入依赖，供测试使用。
    ///
    /// @param store 缓存
    /// @param providerFactory provider 工厂
    /// @param clock 时钟
    /// @param config 初始配置
    DailyQuoteService(
            @NotNull DailyQuoteStore store,
            @NotNull Function<QuoteSource, QuoteProvider> providerFactory,
            @NotNull Clock clock,
            @NotNull DailyQuoteSettings.Config config) {
        this.store = store;
        this.providerFactory = providerFactory;
        this.clock = clock;
        this.config = config;
    }

    /// 当前配置快照。
    ///
    /// @return 配置
    @NotNull
    public DailyQuoteSettings.Config config() {
        return config;
    }

    /// 重新读取设置；设置页保存后由主页调用。
    ///
    /// 进行中的请求会因代数变化被丢弃，不会覆盖新配置。
    public synchronized void reloadConfig() {
        DailyQuoteSettings.Config loaded = DailyQuoteSettings.load();
        // 主页每次聚焦、刷新前都会调用本方法；配置未变时不能重置退避，
        // 否则一次失败后每次回到前台都会立刻重试，破坏 10/20/40/60 分钟退避。
        if (loaded.equals(config)) {
            return;
        }
        config = loaded;
        generation++;
        failures = 0;
        nextAttempt = Instant.EPOCH;
    }

    /// 初始化展示用短句：按当天日期从内置语料选择，不发网络请求。
    ///
    /// @return 内置短句
    @NotNull
    public DailyQuote initialQuote() {
        return BuiltinQuotes.forDate(LocalDate.now(clock));
    }

    /// 在 io 线程重新读取设置，然后回到 FX 线程回调。
    ///
    /// @param callback 接收最新配置；失败时不会回调
    public void reloadConfigAsync(@NotNull Consumer<DailyQuoteSettings.Config> callback) {
        Objects.requireNonNull(callback, "callback");
        CompletableFuture.supplyAsync(() -> {
            reloadConfig();
            return config;
        }, Schedulers.io()).whenCompleteAsync((loaded, error) -> {
            if (error != null) {
                LOG.warning("Failed to reload daily quote settings", error);
                return;
            }
            if (loaded != null) {
                callback.accept(loaded);
            }
        }, Schedulers.javafx());
    }

    /// 是否允许自动刷新。
    ///
    /// @param now 当前时间
    /// @return 允许返回 `true`
    public boolean canAutoRefresh(@NotNull Instant now) {
        DailyQuoteSettings.Config snapshot = config;
        if (!snapshot.enabled() || !snapshot.online() || snapshot.enabledSources().isEmpty()) {
            return false;
        }
        return !inFlight.get() && !now.isBefore(nextAttempt);
    }

    /// 刷新一次。
    ///
    /// @param manual 是否由用户手动触发；手动触发跳过 TTL
    /// @param callback FX 线程回调
    public void refresh(boolean manual, @NotNull Consumer<Result> callback) {
        Objects.requireNonNull(callback, "callback");
        if (!inFlight.compareAndSet(false, true)) {
            // 已有请求在途时立即回当前内容，避免调用方（例如设置页按钮）
            // 一直等不到回调而保持禁用状态。
            callback.accept(new Result(fallback(), false, false, null));
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            // 设置读取也放在 io 线程，避免主页/设置页在 FX 线程做磁盘 IO。
            reloadConfig();
            long requestGeneration = generation;
            return new FetchOutcome(requestGeneration, fetch(config, manual));
        }, Schedulers.io()).whenCompleteAsync((outcome, error) -> {
            inFlight.set(false);
            Result result = outcome != null ? outcome.result() : null;
            if (outcome != null && outcome.generation() != generation) {
                return;
            }
            Result finalResult;
            if (error != null) {
                LOG.warning("Daily quote refresh failed", error);
                finalResult = new Result(fallback(), false, false, error);
            } else if (result == null) {
                finalResult = new Result(fallback(), false, false, new IllegalStateException("empty result"));
            } else {
                finalResult = result;
            }
            updateSchedule(finalResult);
            callback.accept(finalResult);
        }, Schedulers.javafx());
    }

    /// 网络失败或请求在途时的展示内容；使用内置静态语料。
    ///
    /// 不在这里读缓存：该方法可能由 FX 回调调用，磁盘读取应留在 io 线程。
    ///
    /// @return 短句
    @NotNull
    private DailyQuote fallback() {
        return initialQuote();
    }

    private @NotNull Result fetch(@NotNull DailyQuoteSettings.Config snapshot, boolean manual) {
        DailyQuoteStore.Cached cached;
        try {
            cached = store.load();
        } catch (Throwable e) {
            cached = null;
        }

        if (!snapshot.enabled()) {
            return new Result(cached != null ? cached.quote() : initialQuote(), false, cached != null, null);
        }

        if (!snapshot.online() || snapshot.enabledSources().isEmpty()) {
            return new Result(cached != null ? cached.quote() : initialQuote(), false, cached != null, null);
        }

        Instant now = Instant.now(clock);
        if (!manual && cached != null && cached.isFresh(now)) {
            return new Result(cached.quote(), false, true, null);
        }

        List<QuoteSource> sources = snapshot.enabledSources();
        QuoteSource source = sources.get(ThreadLocalRandom.current().nextInt(sources.size()));
        try {
            QuoteProvider provider = providerFactory.apply(source);
            DailyQuote quote = provider.fetch();
            try {
                store.save(new DailyQuoteStore.Cached(quote, now));
            } catch (Throwable e) {
                // 缓存写失败不应把已经获取成功的句子降级为失败。
                LOG.warning("Failed to cache daily quote", e);
            }
            return new Result(quote, true, false, null);
        } catch (Throwable e) {
            LOG.warning("Daily quote source failed: " + source.id(), e);
            // 用户要求失败时静默降级为自有静态 JSON（MC/诗词），不展示可能过期的在线缓存。
            return new Result(initialQuote(), false, false, e);
        }
    }

    private void updateSchedule(@NotNull Result result) {
        Instant now = Instant.now(clock);
        if (result.error() != null) {
            failures = Math.min(failures + 1, 4);
            nextAttempt = now.plus(backoff(failures)).plusSeconds(jitterSeconds());
            return;
        }
        failures = 0;
        nextAttempt = now.plus(DailyQuoteStore.TTL).plusSeconds(jitterSeconds());
    }

    private static @NotNull Duration backoff(int failures) {
        return switch (failures) {
            case 1 -> Duration.ofMinutes(10);
            case 2 -> Duration.ofMinutes(20);
            case 3 -> Duration.ofMinutes(40);
            default -> Duration.ofMinutes(60);
        };
    }

    private static long jitterSeconds() {
        return ThreadLocalRandom.current().nextLong(0, 61);
    }

}
