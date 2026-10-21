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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.community.env.CommunityPaths;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// 快照磁盘布局的唯一读写入口。
///
/// 布局：
/// ```
/// <root>/scopes.json
/// <root>/<scopeKey>/<snapshotId>/manifest.json
/// <root>/objects/<sha前2>/<sha>
/// <root>/snapshot.lock
/// ```
/// 所有写操作先写同目录临时文件/目录，再原子重命名；对象内容寻址、可跨快照复用。
@NotNullByDefault
public final class SnapshotStore {

    /// 锁等待上限（毫秒）。
    public static final long DEFAULT_LOCK_TIMEOUT_MILLIS = 3_000L;

    private static final DateTimeFormatter ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Path root;
    private final Object indexLock = new Object();

    /// 使用默认根目录构造。
    public SnapshotStore() {
        this(CommunityPaths.localHome().resolve("community/snapshots"));
    }

    /// 使用指定根目录构造（测试用）。
    ///
    /// @param root 根目录
    public SnapshotStore(@NotNull Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /// 返回根目录。
    ///
    /// @return 根目录
    @NotNull
    public Path root() {
        return root;
    }

    /// 返回作用域目录。
    ///
    /// @param scopeKey 作用域 key
    /// @return 作用域目录
    @NotNull
    public Path scopeDirectory(@NotNull String scopeKey) {
        return root.resolve(scopeKey);
    }

    /// 返回对象目录。
    ///
    /// @return 对象目录
    @NotNull
    public Path objectsDirectory() {
        return root.resolve("objects");
    }

    /// 返回指定内容的对象路径。
    ///
    /// @param sha256 内容 SHA-256
    /// @return 对象路径
    @NotNull
    public Path objectPath(@NotNull String sha256) {
        return objectsDirectory().resolve(sha256.substring(0, 2)).resolve(sha256);
    }

    /// 返回回收站目录（删除的对象先移到这里）。
    ///
    /// @return 回收站目录
    @NotNull
    public Path trashDirectory() {
        return root.resolve("trash-objects");
    }

    /// 返回指定作用域的锁文件。
    ///
    /// @param scopeKey 作用域 key
    /// @return 锁文件
    @NotNull
    public Path lockFile(@NotNull String scopeKey) {
        return scopeDirectory(scopeKey).resolve("snapshot.lock");
    }

    /// 确保根目录存在。
    ///
    /// @throws IOException 创建失败
    public void ensureDirectories() throws IOException {
        Files.createDirectories(root);
        Files.createDirectories(objectsDirectory());
        Files.createDirectories(trashDirectory());
    }

    /// 在 3 秒内获取作用域文件锁。
    ///
    /// @param scopeKey 作用域 key
    /// @param timeoutMillis 超时
    /// @return 可关闭的锁
    /// @throws IOException 超时或 IO 失败
    @NotNull
    public LockHandle lock(@NotNull String scopeKey, long timeoutMillis) throws IOException {
        ensureDirectories();
        Files.createDirectories(scopeDirectory(scopeKey));
        Path file = lockFile(scopeKey);
        FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        long deadline = System.nanoTime() + Math.max(1L, timeoutMillis) * 1_000_000L;
        try {
            while (true) {
                try {
                    FileLock lock = channel.tryLock();
                    if (lock != null) {
                        return new LockHandle(channel, lock);
                    }
                } catch (OverlappingFileLockException ignored) {
                    // 同一 JVM 内另一个 store 实例正持有锁，继续等待。
                }
                if (System.nanoTime() >= deadline) {
                    throw new IOException("Timed out waiting for snapshot lock: " + file);
                }
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for snapshot lock", e);
                }
            }
        } catch (Throwable e) {
            try {
                channel.close();
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /// 列出作用域下的快照，按时间倒序。
    ///
    /// @param scopeKey 作用域 key
    /// @return 快照摘要
    @NotNull
    public List<SnapshotInfo> list(@NotNull String scopeKey) {
        List<SnapshotInfo> result = new ArrayList<>();
        Path directory = scopeDirectory(scopeKey);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (!Files.isDirectory(path) || path.getFileName().toString().startsWith(".")) {
                    continue;
                }
                SnapshotInfo info = readInfo(scopeKey, path.getFileName().toString());
                if (info != null) {
                    result.add(info);
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to list snapshots in " + directory, e);
        }
        result.sort(Comparator.comparing(SnapshotInfo::at).reversed());
        return List.copyOf(result);
    }

    /// 读取一份 manifest。
    ///
    /// @param scopeKey 作用域 key
    /// @param snapshotId 快照 id
    /// @return 清单，或 `null`
    @Nullable
    public SnapshotManifest readManifest(@NotNull String scopeKey, @NotNull String snapshotId) {
        Path path = scopeDirectory(scopeKey).resolve(snapshotId).resolve("manifest.json");
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return SnapshotManifest.fromJson(
                    JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            LOG.warning("Failed to read snapshot manifest " + path, e);
            return null;
        }
    }

    /// 读取快照摘要。
    ///
    /// @param scopeKey 作用域 key
    /// @param snapshotId 快照 id
    /// @return 摘要，或 `null`
    @Nullable
    public SnapshotInfo readInfo(@NotNull String scopeKey, @NotNull String snapshotId) {
        SnapshotManifest manifest = readManifest(scopeKey, snapshotId);
        if (manifest == null) {
            return null;
        }
        long bytes = 0;
        for (SnapshotFileEntry file : manifest.files()) {
            bytes += Math.max(0L, file.size());
        }
        return new SnapshotInfo(
                manifest.snapshotId(),
                Instant.ofEpochMilli(manifest.createdAt()),
                manifest.reason(),
                manifest.files().size(),
                bytes,
                manifest.mode() == SnapshotMode.MANIFEST_ONLY,
                manifest.reason() == SnapshotReason.PRE_RESTORE);
    }

    /// 写入一份快照：先写临时目录，再原子改名为正式目录。
    ///
    /// @param scope 作用域
    /// @param manifest 清单
    /// @return 快照目录
    /// @throws IOException 写入失败
    @NotNull
    public Path createSnapshot(@NotNull SnapshotScope scope, @NotNull SnapshotManifest manifest) throws IOException {
        Files.createDirectories(scopeDirectory(scope.scopeKey()));
        Path target = scopeDirectory(scope.scopeKey()).resolve(manifest.snapshotId());
        Path temp = scopeDirectory(scope.scopeKey())
                .resolve(".tmp-" + manifest.snapshotId() + "-" + UUID.randomUUID());
        try {
            Files.createDirectories(temp);
            writeAtomic(temp.resolve("manifest.json"),
                    manifest.toJson().toString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target);
            } catch (FileAlreadyExistsException e) {
                throw new IOException("Snapshot already exists: " + manifest.snapshotId(), e);
            }
        } catch (Throwable e) {
            deleteRecursively(temp);
            throw e;
        }
        updateScopeIndex(scope, manifest.snapshotId());
        return target;
    }

    /// 删除一份快照目录。
    ///
    /// @param scopeKey 作用域 key
    /// @param snapshotId 快照 id
    /// @throws IOException 删除失败
    public void deleteSnapshot(@NotNull String scopeKey, @NotNull String snapshotId) throws IOException {
        deleteRecursively(scopeDirectory(scopeKey).resolve(snapshotId));
    }

    /// 把一个对象移入回收站。
    ///
    /// @param object 对象路径
    /// @throws IOException 移动失败
    public void trashObject(@NotNull Path object) throws IOException {
        if (!Files.exists(object)) {
            return;
        }
        Files.createDirectories(trashDirectory());
        Path target = trashDirectory().resolve(object.getFileName().toString() + "-" + UUID.randomUUID());
        try {
            Files.move(object, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(object, target);
        }
    }

    /// 清理回收站与残留临时目录。
    public void cleanupTrash() {
        try {
            deleteRecursively(trashDirectory());
            Files.createDirectories(trashDirectory());
        } catch (IOException e) {
            LOG.warning("Failed to clean snapshot trash", e);
        }
    }

    /// 清理崩溃残留的 `.tmp-*` 目录。
    public void cleanupTempDirectories() {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (DirectoryStream<Path> scopes = Files.newDirectoryStream(root)) {
            for (Path scope : scopes) {
                if (!Files.isDirectory(scope)) {
                    continue;
                }
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(scope, ".tmp-*")) {
                    for (Path temp : stream) {
                        deleteRecursively(temp);
                    }
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to clean temporary snapshot directories", e);
        }
    }

    /// 计算作用域内所有快照已物化文件的字节数。
    ///
    /// 仅统计 `object` 非空的条目；manifest-only 快照不占内容空间，否则它会在
    /// 写入后立刻被字节上限清理掉（此时用户既得不到内容，也看不到清单）。
    ///
    /// @param scopeKey 作用域 key
    /// @return 已物化字节数
    public long usageBytes(@NotNull String scopeKey) {
        long total = 0;
        for (SnapshotInfo info : list(scopeKey)) {
            SnapshotManifest manifest = readManifest(scopeKey, info.id());
            if (manifest == null) {
                continue;
            }
            for (SnapshotFileEntry file : manifest.files()) {
                if (file.object() != null) {
                    total += Math.max(0L, file.size());
                }
            }
        }
        return total;
    }

    /// 获得一个对象：已存在则直接复用；否则硬链接（优先）或复制源文件。
    ///
    /// @param source 源文件
    /// @param sha256 已计算的 SHA-256
    /// @param preferLink 是否优先硬链接
    /// @return 物化结果
    /// @throws IOException 复制/链接失败
    @NotNull
    public Materialized materialize(@NotNull Path source, @NotNull String sha256, boolean preferLink)
            throws IOException {
        Path object = objectPath(sha256);
        if (Files.isRegularFile(object)) {
            try {
                if (DigestUtils.digestToString("SHA-256", object).equals(sha256)) {
                    return new Materialized(object, false);
                }
            } catch (IOException e) {
                LOG.warning("Failed to verify existing snapshot object " + object, e);
            }
            try {
                Files.deleteIfExists(object);
            } catch (IOException e) {
                throw new IOException("Corrupt snapshot object cannot be replaced: " + object, e);
            }
        }
        Files.createDirectories(object.getParent());
        Path temp = object.resolveSibling("." + object.getFileName() + ".tmp-" + UUID.randomUUID());
        boolean linked = false;
        try {
            if (preferLink) {
                try {
                    Files.createLink(temp, source);
                    linked = true;
                } catch (IOException | UnsupportedOperationException | SecurityException e) {
                    LOG.debug("Hard link not supported for " + source + ", falling back to copy");
                }
            }
            if (!Files.exists(temp)) {
                Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, object, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, object, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Materialized(object, linked);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    /// 校验对象存在且内容 SHA-256 匹配。
    ///
    /// @param sha256 期望的 SHA-256
    /// @return 是否有效
    public boolean verifyObject(@NotNull String sha256) {
        Path object = objectPath(sha256);
        if (!Files.isRegularFile(object)) {
            return false;
        }
        try {
            return DigestUtils.digestToString("SHA-256", object).equals(sha256);
        } catch (IOException e) {
            LOG.warning("Failed to verify snapshot object " + object, e);
            return false;
        }
    }

    /// 删除所有快照都没有引用的对象。
    public void collectGarbage() {
        if (!Files.isDirectory(root)) {
            return;
        }
        Set<String> referenced = new HashSet<>();
        try (DirectoryStream<Path> scopes = Files.newDirectoryStream(root)) {
            for (Path scope : scopes) {
                if (!Files.isDirectory(scope) || scope.getFileName().toString().startsWith(".")) {
                    continue;
                }
                try (DirectoryStream<Path> snapshots = Files.newDirectoryStream(scope)) {
                    for (Path snapshot : snapshots) {
                        if (!Files.isDirectory(snapshot) || snapshot.getFileName().toString().startsWith(".")) {
                            continue;
                        }
                        SnapshotManifest manifest = readManifest(
                                scope.getFileName().toString(), snapshot.getFileName().toString());
                        if (manifest != null) {
                            for (SnapshotFileEntry file : manifest.files()) {
                                if (file.object() != null) {
                                    referenced.add(file.object());
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to collect snapshot garbage", e);
            return;
        }

        if (!Files.isDirectory(objectsDirectory())) {
            cleanupTrash();
            return;
        }
        try (Stream<Path> stream = Files.walk(objectsDirectory())) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String relative = objectsDirectory().relativize(path).toString().replace('\\', '/');
                if (!referenced.contains(relative)) {
                    try {
                        trashObject(path);
                    } catch (IOException e) {
                        LOG.warning("Failed to trash unreferenced object " + path, e);
                    }
                }
            }
        } catch (IOException e) {
            LOG.warning("Failed to walk snapshot objects", e);
        }
        cleanupTrash();
    }

    /// 生成快照 id（UTC 时间戳，冲突时追加序号）。
    ///
    /// @param scopeKey 作用域 key
    /// @return 唯一 id
    @NotNull
    public String nextSnapshotId(@NotNull String scopeKey) {
        String base = ID_FORMAT.format(Instant.now());
        String candidate = base;
        int index = 1;
        while (Files.exists(scopeDirectory(scopeKey).resolve(candidate))) {
            candidate = base + "-" + index++;
        }
        return candidate;
    }

    /// 更新 `scopes.json`，记录作用域与最后一次快照。
    ///
    /// @param scope 作用域
    /// @param lastSnapshotId 最后一次快照 id
    @NotNull
    private Path updateScopeIndex(@NotNull SnapshotScope scope, @NotNull String lastSnapshotId) {
        synchronized (indexLock) {
            Path path = root.resolve("scopes.json");
            JsonObject rootObject = new JsonObject();
            try {
                if (Files.isRegularFile(path)) {
                    rootObject = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
                            .getAsJsonObject();
                }
            } catch (IOException | RuntimeException e) {
                LOG.warning("Failed to read snapshot scope index, rewriting", e);
            }
            JsonObject object = rootObject.get(scope.scopeKey()) instanceof JsonObject existing
                    ? existing
                    : new JsonObject();
            object.addProperty("runDirectory", scope.runDirectory().toString());
            object.addProperty("label", scope.label());
            object.addProperty("lastSnapshotId", lastSnapshotId);
            object.addProperty("updatedAt", Instant.now().toEpochMilli());
            JsonArray ids = new JsonArray();
            for (String id : scope.instanceIds()) {
                ids.add(id);
            }
            object.add("instanceIds", ids);
            rootObject.add(scope.scopeKey(), object);
            try {
                writeAtomic(path, rootObject.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                LOG.warning("Failed to write snapshot scope index", e);
            }
            return path;
        }
    }

    /// 原子写入文件。
    ///
    /// @param target 目标
    /// @param data 内容
    /// @throws IOException 写入失败
    public static void writeAtomic(@NotNull Path target, @NotNull byte[] data) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling("." + target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.write(temp, data, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    /// 递归删除；不存在时返回。
    ///
    /// @param path 路径
    /// @throws IOException 删除失败
    public static void deleteRecursively(@NotNull Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path item : paths) {
                Files.deleteIfExists(item);
            }
        }
    }

    /// 文件锁句柄。
    @NotNullByDefault
    public static final class LockHandle implements AutoCloseable {

        private final FileChannel channel;
        private final FileLock lock;

        private LockHandle(@NotNull FileChannel channel, @NotNull FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() throws IOException {
            try {
                lock.release();
            } finally {
                channel.close();
            }
        }
    }

    /// 物化结果。
    ///
    /// @param path 对象路径
    /// @param linked 是否成功使用硬链接
    @NotNullByDefault
    public record Materialized(@NotNull Path path, boolean linked) {
    }
}
