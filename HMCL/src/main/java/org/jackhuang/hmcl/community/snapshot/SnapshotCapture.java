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
import org.jackhuang.hmcl.util.DigestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 一次快照采集。
///
/// 调用方应保证在 IO 线程执行；本类同步完成采集，不触碰 JavaFX。
@NotNullByDefault
public final class SnapshotCapture {

    /// 磁盘保留的最小可用空间。
    private static final long MIN_FREE_SPACE = 512L * 1024 * 1024;

    private final SnapshotStore store;
    private final SnapshotSettings settings;

    /// 构造采集器。
    ///
    /// @param store 存储
    /// @param settings 设置
    public SnapshotCapture(@NotNull SnapshotStore store, @NotNull SnapshotSettings settings) {
        this.store = store;
        this.settings = settings;
    }

    /// 采集一份快照。
    ///
    /// @param scope 作用域
    /// @param reason 原因
    /// @param successfulLaunchAt 成功启动判定时间（epoch millis）
    /// @param thresholdSeconds 成功阈值
    /// @param force 忽略“启用”开关（手动/回滚前采集）
    /// @return 采集结果
    @NotNull
    public SnapshotResult capture(
            @NotNull SnapshotScope scope,
            @NotNull SnapshotReason reason,
            long successfulLaunchAt,
            int thresholdSeconds,
            boolean force) {
        return capture(scope, reason, successfulLaunchAt, thresholdSeconds, force, null);
    }

    /// 采集，并保护一份正在被回滚读取的快照不被保留策略删除。
    ///
    /// @param scope 作用域
    /// @param reason 原因
    /// @param successfulLaunchAt 成功启动判定时间（epoch millis）
    /// @param thresholdSeconds 成功阈值
    /// @param force 忽略“启用”开关
    /// @param protectSnapshotId 不允许被清理的快照 id，可为 `null`
    /// @return 采集结果
    @NotNull
    public SnapshotResult capture(
            @NotNull SnapshotScope scope,
            @NotNull SnapshotReason reason,
            long successfulLaunchAt,
            int thresholdSeconds,
            boolean force,
            @Nullable String protectSnapshotId) {
        if (!force && !settings.enabled()) {
            return SnapshotResult.skipped("disabled");
        }
        for (String instanceId : scope.instanceIds()) {
            if (CommunityLaunchHooks.activeSessionCount(instanceId) > 0) {
                return SnapshotResult.skipped("game_running");
            }
        }

        try (SnapshotStore.LockHandle ignored =
                     store.lock(scope.scopeKey(), SnapshotStore.DEFAULT_LOCK_TIMEOUT_MILLIS)) {
            List<SnapshotScanner.Source> sources = SnapshotScanner.scan(scope.runDirectory(), settings);
            long totalSize = sources.stream().mapToLong(SnapshotScanner.Source::size).sum();
            if (!hasEnoughDiskSpace(scope.runDirectory(), totalSize)) {
                return SnapshotResult.skipped("disk_low");
            }

            SnapshotManifest previous = store.list(scope.scopeKey()).stream()
                    .map(info -> store.readManifest(scope.scopeKey(), info.id()))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            SnapshotHashCache hashCache = SnapshotHashCache.load(
                    store.root(), settings.hashCacheMaxEntries());
            Map<String, SnapshotFileEntry> previousBySha = new HashMap<>();
            if (previous != null) {
                for (SnapshotFileEntry entry : previous.files()) {
                    if (entry.object() != null) {
                        previousBySha.putIfAbsent(entry.sha256(), entry);
                    }
                }
            }

            long budget = Math.max(0L, settings.maxBytesPerScope() - store.usageBytes(scope.scopeKey()));
            long used = 0L;
            boolean manifestOnly = false;
            boolean copied = false;
            List<SnapshotFileEntry> files = new ArrayList<>();
            for (SnapshotScanner.Source source : sources) {
                String sha = hashCache.get(source.file(), source.size(), source.mtime());
                if (sha == null) {
                    sha = DigestUtils.digestToString("SHA-256", source.file());
                    hashCache.put(source.file(), source.size(), source.mtime(), sha);
                }
                SnapshotFileEntry previousEntry = previousBySha.get(sha);
                String object = null;
                String link = null;
                if (!manifestOnly) {
                    if (used + source.size() > budget) {
                        manifestOnly = true;
                    } else {
                        try {
                            SnapshotStore.Materialized materialized = store.materialize(
                                    source.file(), sha, !source.copyOnly());
                            object = store.objectsDirectory().relativize(materialized.path())
                                    .toString().replace('\\', '/');
                            if (materialized.linked()) {
                                link = "hardlink";
                            } else {
                                link = "copy";
                                copied = true;
                            }
                            used += source.size();
                        } catch (IOException e) {
                            LOG.warning("Failed to materialize snapshot file " + source.path(), e);
                        }
                    }
                }
                if (object == null && previousEntry != null && !manifestOnly
                        && previousEntry.object() != null
                        && java.nio.file.Files.isRegularFile(store.objectPath(sha))) {
                    object = previousEntry.object();
                    link = previousEntry.link();
                }
                files.add(new SnapshotFileEntry(
                        source.path(), source.size(), sha, object, link, source.mtime()));
            }

            SnapshotMode mode;
            if (manifestOnly) {
                mode = SnapshotMode.MANIFEST_ONLY;
            } else if (copied) {
                mode = SnapshotMode.COPY;
            } else {
                mode = SnapshotMode.HARDLINK;
            }

            String snapshotId = store.nextSnapshotId(scope.scopeKey());
            SnapshotManifest manifest = new SnapshotManifest(
                    SnapshotManifest.SCHEMA,
                    snapshotId,
                    scope.scopeKey(),
                    scope.label(),
                    scope.runDirectory().toString(),
                    scope.instanceIds(),
                    System.currentTimeMillis(),
                    successfulLaunchAt,
                    reason,
                    mode,
                    thresholdSeconds,
                    files,
                    SnapshotScanner.defaultExcluded(),
                    new com.google.gson.JsonObject());
            store.createSnapshot(scope, manifest);
            hashCache.save();
            retain(scope, protectSnapshotId);
            SnapshotInfo info = store.readInfo(scope.scopeKey(), snapshotId);
            if (info == null) {
                return SnapshotResult.skipped("error");
            }
            LOG.info("[CommunitySnapshot] captured " + snapshotId + " scope=" + scope.scopeKey()
                    + " files=" + files.size() + " bytes=" + used + " mode=" + mode.jsonName());
            return SnapshotResult.success(info);
        } catch (IOException e) {
            LOG.warning("Failed to capture snapshot for " + scope.runDirectory(), e);
            return SnapshotResult.skipped("lock_timeout");
        } catch (Throwable e) {
            LOG.warning("Unexpected failure while capturing snapshot", e);
            return SnapshotResult.skipped("error");
        }
    }

    /// 清理超出保留份数/字节上限的非回滚前快照。
    ///
    /// @param scope 作用域
    private void retain(@NotNull SnapshotScope scope, @Nullable String protectSnapshotId) {
        List<SnapshotInfo> infos = new ArrayList<>(store.list(scope.scopeKey()));
        List<SnapshotInfo> candidates = infos.stream()
                .filter(info -> info.reason() != SnapshotReason.PRE_RESTORE)
                .filter(info -> !info.id().equals(protectSnapshotId))
                .sorted(Comparator.comparing(SnapshotInfo::at))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        int keep = settings.keep();
        while (candidates.size() > keep) {
            SnapshotInfo oldest = candidates.remove(0);
            deleteQuietly(scope.scopeKey(), oldest.id());
        }

        long maxBytes = settings.maxBytesPerScope();
        while (store.usageBytes(scope.scopeKey()) > maxBytes && !candidates.isEmpty()) {
            SnapshotInfo oldest = candidates.remove(0);
            deleteQuietly(scope.scopeKey(), oldest.id());
        }
        store.collectGarbage();
    }

    /// 删除快照，失败只记录日志。
    private void deleteQuietly(@NotNull String scopeKey, @NotNull String snapshotId) {
        try {
            store.deleteSnapshot(scopeKey, snapshotId);
        } catch (IOException e) {
            LOG.warning("Failed to delete old snapshot " + snapshotId, e);
        }
    }

    /// 判断磁盘是否有足够空间。
    private boolean hasEnoughDiskSpace(@NotNull Path runDirectory, long estimatedBytes) {
        try {
            FileStore fileStore = Files.getFileStore(runDirectory);
            long required = Math.max(MIN_FREE_SPACE, estimatedBytes);
            return fileStore.getUsableSpace() >= required;
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }
}
