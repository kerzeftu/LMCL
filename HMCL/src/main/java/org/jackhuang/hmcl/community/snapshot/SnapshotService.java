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

import org.jackhuang.hmcl.community.launch.CommunityLaunchHooks;
import org.jackhuang.hmcl.community.launch.LaunchTracker;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 快照对 UI 的唯一入口。
///
/// 所有阻塞操作都提交到 [Schedulers#io()]；调用方在 JavaFX 线程拿到 future 后自行切回。
/// 同一作用域的采集/回滚通过进程内集合与文件锁串行。
@NotNullByDefault
public final class SnapshotService {

    private static final SnapshotStore STORE = new SnapshotStore();
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();

    private SnapshotService() {
    }

    /// 返回快照根目录。
    ///
    /// @return 根目录
    @NotNull
    public static Path root() {
        return STORE.root();
    }

    /// 清理启动残留：临时目录、回收站与无引用对象。
    public static void cleanupAtStartup() {
        try {
            STORE.cleanupTempDirectories();
            STORE.collectGarbage();
        } catch (Throwable e) {
            LOG.warning("Failed to clean snapshot store at startup", e);
        }
    }

    /// 异步采集一份快照。
    ///
    /// @param versionId 实例 id
    /// @param reason 原因
    /// @return 采集结果
    @NotNull
    public static CompletableFuture<SnapshotResult> capture(
            @NotNull String versionId,
            @NotNull SnapshotReason reason) {
        return CompletableFuture.supplyAsync(() -> captureNow(versionId, reason), Schedulers.io());
    }

    /// 同步采集（供测试使用）。
    ///
    /// @param versionId 实例 id
    /// @param reason 原因
    /// @return 采集结果
    @NotNull
    public static SnapshotResult captureNow(@NotNull String versionId, @NotNull SnapshotReason reason) {
        SnapshotScope scope = scopeOf(versionId);
        if (scope == null) {
            return SnapshotResult.skipped("not_found");
        }
        return captureScopeNow(scope, reason, System.currentTimeMillis());
    }

    /// 异步采集一个已经解析好的作用域（供自动监听器使用，避免会话清理后丢失仓库）。
    ///
    /// @param scope 作用域
    /// @param reason 原因
    /// @param successfulLaunchAt 成功启动判定时间
    /// @return 采集结果
    @NotNull
    public static CompletableFuture<SnapshotResult> capture(
            @NotNull SnapshotScope scope,
            @NotNull SnapshotReason reason,
            long successfulLaunchAt) {
        return CompletableFuture.supplyAsync(
                () -> captureScopeNow(scope, reason, successfulLaunchAt), Schedulers.io());
    }

    /// 同步采集一个已经解析好的作用域。
    ///
    /// @param scope 作用域
    /// @param reason 原因
    /// @param successfulLaunchAt 成功启动判定时间
    /// @return 采集结果
    @NotNull
    public static SnapshotResult captureScopeNow(
            @NotNull SnapshotScope scope,
            @NotNull SnapshotReason reason,
            long successfulLaunchAt) {
        if (!BUSY.add(scope.scopeKey())) {
            return SnapshotResult.skipped("busy");
        }
        try {
            SnapshotSettings settings = SnapshotSettings.load();
            if (reason == SnapshotReason.AUTO_START && !settings.autoCaptureOnSuccess()) {
                return SnapshotResult.skipped("disabled");
            }
            int threshold = LaunchTracker.loadDefaultThreshold();
            return new SnapshotCapture(STORE, settings).capture(
                    scope, reason, successfulLaunchAt, threshold,
                    reason != SnapshotReason.AUTO_START);
        } finally {
            BUSY.remove(scope.scopeKey());
        }
    }

    /// 列出某个实例当前作用域下的快照。
    ///
    /// @param versionId 实例 id
    /// @return 摘要，按时间倒序
    @NotNull
    public static List<SnapshotInfo> list(@NotNull String versionId) {
        SnapshotScope scope = scopeOf(versionId);
        if (scope == null) {
            return List.of();
        }
        return STORE.list(scope.scopeKey());
    }

    /// 返回实例对应的作用域。
    ///
    /// @param versionId 实例 id
    /// @return 作用域，或 `null`
    @Nullable
    public static SnapshotScope scopeOf(@NotNull String versionId) {
        HMCLGameRepository repository = repositoryOf(versionId);
        if (repository == null) {
            return null;
        }
        try {
            Version version = repository.getVersion(versionId);
            if (version == null) {
                return null;
            }
            Path runDirectory = repository.getRunDirectory(versionId);
            List<String> instanceIds = new ArrayList<>();
            for (Version candidate : repository.getVersions()) {
                try {
                    if (runDirectory.equals(repository.getRunDirectory(candidate.getId()))) {
                        instanceIds.add(candidate.getId());
                    }
                } catch (RuntimeException ignored) {
                }
            }
            instanceIds.sort(Comparator.naturalOrder());
            return SnapshotScope.of(runDirectory, repository.getBaseDirectory(),
                    repository.getVersionRoot(versionId), instanceIds);
        } catch (RuntimeException e) {
            LOG.debug("Failed to resolve snapshot scope for " + versionId, e);
            return null;
        }
    }

    /// 异步回滚到指定快照。
    ///
    /// @param versionId 实例 id
    /// @param snapshotId 快照 id
    /// @return 回滚结果
    @NotNull
    public static CompletableFuture<RestoreResult> restore(
            @NotNull String versionId,
            @NotNull String snapshotId) {
        return CompletableFuture.supplyAsync(() -> {
            SnapshotScope scope = scopeOf(versionId);
            if (scope == null) {
                return RestoreResult.aborted(List.of("not_found"));
            }
            SnapshotManifest manifest = STORE.readManifest(scope.scopeKey(), snapshotId);
            if (manifest == null) {
                return RestoreResult.aborted(List.of("snapshot_not_found"));
            }
            if (!scope.scopeKey().equals(manifest.scopeKey())) {
                return RestoreResult.aborted(List.of("scope_mismatch"));
            }
            if (!BUSY.add(scope.scopeKey())) {
                return RestoreResult.aborted(List.of("busy"));
            }
            try {
                return new SnapshotRestore(STORE, SnapshotSettings.load())
                        .restore(scope, manifest, LaunchTracker.loadDefaultThreshold());
            } finally {
                BUSY.remove(scope.scopeKey());
            }
        }, Schedulers.io());
    }

    /// 异步计算差异。
    ///
    /// @param versionId 实例 id
    /// @param snapshotId 快照 id
    /// @return 差异
    @NotNull
    public static CompletableFuture<SnapshotDiff> diff(
            @NotNull String versionId,
            @NotNull String snapshotId) {
        return CompletableFuture.supplyAsync(() -> {
            SnapshotScope scope = scopeOf(versionId);
            if (scope == null) {
                return SnapshotDiff.empty();
            }
            SnapshotManifest manifest = STORE.readManifest(scope.scopeKey(), snapshotId);
            if (manifest == null) {
                return SnapshotDiff.empty();
            }
            return new SnapshotRestore(STORE, SnapshotSettings.load()).diff(scope, manifest);
        }, Schedulers.io());
    }

    /// 异步删除一份快照。
    ///
    /// @param snapshotId 快照 id
    /// @return 完成信号
    @NotNull
    public static CompletableFuture<Void> delete(@NotNull String snapshotId) {
        return CompletableFuture.runAsync(() -> {
            if (!Files.isDirectory(STORE.root())) {
                return;
            }
            try (DirectoryStream<Path> scopes = Files.newDirectoryStream(STORE.root())) {
                for (Path scope : scopes) {
                    if (!Files.isDirectory(scope) || scope.getFileName().toString().startsWith(".")) {
                        continue;
                    }
                    String scopeKey = scope.getFileName().toString();
                    if (STORE.readManifest(scopeKey, snapshotId) != null) {
                        STORE.deleteSnapshot(scopeKey, snapshotId);
                        STORE.collectGarbage();
                        return;
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to delete snapshot " + snapshotId, e);
            }
        }, Schedulers.io());
    }

    /// 该作用域是否正在采集/回滚。
    ///
    /// @param scopeKey 作用域 key
    /// @return 是否忙碌
    public static boolean isBusy(@NotNull String scopeKey) {
        return BUSY.contains(scopeKey);
    }

    /// 解析仓库：优先当前启动会话，其次上游选中仓库。
    @Nullable
    private static HMCLGameRepository repositoryOf(@NotNull String versionId) {
        CommunityLaunchHooks.LaunchSession session = CommunityLaunchHooks.sessionOf(versionId);
        if (session != null) {
            return session.repository();
        }
        try {
            return GameDirectoryManager.selectedRepositoryProperty().get();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
