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

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 把运行目录恢复到一份快照。
///
/// 回滚前总是先采集 `pre-restore` 快照；单个文件失败不中断其余文件。
@NotNullByDefault
public final class SnapshotRestore {

    private final SnapshotStore store;
    private final SnapshotSettings settings;

    /// 构造恢复器。
    ///
    /// @param store 存储
    /// @param settings 设置
    public SnapshotRestore(@NotNull SnapshotStore store, @NotNull SnapshotSettings settings) {
        this.store = store;
        this.settings = settings;
    }

    /// 执行回滚。
    ///
    /// @param scope 作用域
    /// @param manifest 目标快照
    /// @param thresholdSeconds 成功阈值（传给回滚前快照）
    /// @return 回滚结果
    @NotNull
    public RestoreResult restore(
            @NotNull SnapshotScope scope,
            @NotNull SnapshotManifest manifest,
            int thresholdSeconds) {
        for (String instanceId : scope.instanceIds()) {
            if (CommunityLaunchHooks.activeSessionCount(instanceId) > 0) {
                return RestoreResult.aborted(List.of("game_running"));
            }
        }

        SnapshotResult preRestore = new SnapshotCapture(store, settings).capture(
                scope, SnapshotReason.PRE_RESTORE, System.currentTimeMillis(), thresholdSeconds,
                true, manifest.snapshotId());
        if (preRestore.skipped() || preRestore.snapshotId() == null) {
            return RestoreResult.aborted(List.of("pre_restore_failed"));
        }

        List<String> failed = new ArrayList<>();
        int restored = 0;
        int deleted = 0;
        SnapshotDiff diff = diff(scope, manifest);

        for (String path : diff.added()) {
            try {
                Path target = resolve(scope.runDirectory(), path);
                if (Files.deleteIfExists(target)) {
                    deleted++;
                }
            } catch (IOException | RuntimeException e) {
                failed.add(path);
                LOG.warning("Failed to delete added file during restore: " + path, e);
            }
        }

        for (SnapshotFileEntry entry : manifest.files()) {
            if (entry.object() == null) {
                failed.add(entry.path());
                continue;
            }
            Path object = store.objectPath(entry.sha256());
            if (!store.verifyObject(entry.sha256())) {
                failed.add(entry.path());
                LOG.warning("Snapshot object is missing or corrupt: " + entry.sha256());
                continue;
            }
            try {
                Path target = resolve(scope.runDirectory(), entry.path());
                if (Files.isRegularFile(target)
                        && DigestUtils.digestToString("SHA-256", target).equals(entry.sha256())) {
                    continue;
                }
                copyAtomically(object, target);
                restored++;
            } catch (IOException | RuntimeException e) {
                failed.add(entry.path());
                LOG.warning("Failed to restore snapshot file: " + entry.path(), e);
            }
        }

        return new RestoreResult(restored, deleted, failed.size(), List.copyOf(failed),
                preRestore.snapshotId());
    }

    /// 计算当前运行目录与目标快照的差异。
    ///
    /// @param scope 作用域
    /// @param manifest 目标快照
    /// @return 差异
    @NotNull
    public SnapshotDiff diff(@NotNull SnapshotScope scope, @NotNull SnapshotManifest manifest) {
        Map<String, SnapshotFileEntry> target = new HashMap<>();
        for (SnapshotFileEntry entry : manifest.files()) {
            target.put(entry.path(), entry);
        }
        Map<String, SnapshotScanner.Source> current = new HashMap<>();
        for (SnapshotScanner.Source source : SnapshotScanner.scan(scope.runDirectory(), settings)) {
            current.put(source.path(), source);
        }

        List<String> added = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        List<String> invalid = new ArrayList<>();

        for (String path : current.keySet()) {
            if (!target.containsKey(path)) {
                added.add(path);
            }
        }
        for (SnapshotFileEntry entry : manifest.files()) {
            SnapshotScanner.Source source = current.get(entry.path());
            if (source == null) {
                missing.add(entry.path());
                continue;
            }
            if (entry.object() == null || !store.verifyObject(entry.sha256())) {
                invalid.add(entry.path());
                continue;
            }
            try {
                String sha = DigestUtils.digestToString("SHA-256", source.file());
                if (!sha.equals(entry.sha256())) {
                    changed.add(entry.path());
                }
            } catch (IOException e) {
                changed.add(entry.path());
            }
        }
        added.sort(String::compareTo);
        missing.sort(String::compareTo);
        changed.sort(String::compareTo);
        invalid.sort(String::compareTo);
        return new SnapshotDiff(List.copyOf(added), List.copyOf(missing),
                List.copyOf(changed), List.copyOf(invalid));
    }

    /// 解析相对路径，并再次确认没有逃出运行目录。
    @NotNull
    private static Path resolve(@NotNull Path runDirectory, @NotNull String relative) throws IOException {
        Path root = runDirectory.toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Snapshot path escapes run directory: " + relative);
        }
        return target;
    }

    /// 复制对象到目标路径：先写同目录临时文件，再原子替换。
    private static void copyAtomically(@NotNull Path object, @NotNull Path target) throws IOException {
        if (Files.isDirectory(target)) {
            throw new IOException("Target is a directory: " + target);
        }
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling("." + target.getFileName() + ".lmcl-restore-" + UUID.randomUUID());
        try {
            Files.copy(object, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.setLastModifiedTime(target, FileTime.fromMillis(Files.getLastModifiedTime(object).toMillis()));
            } catch (IOException ignored) {
            }
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }
}
