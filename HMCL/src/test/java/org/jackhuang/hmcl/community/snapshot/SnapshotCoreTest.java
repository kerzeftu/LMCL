package org.jackhuang.hmcl.community.snapshot;

import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SnapshotCoreTest {

    @TempDir
    Path temp;

    private record Fixture(Path run, SnapshotStore store, SnapshotSettings settings,
                           SnapshotScope scope, SnapshotCapture capture, SnapshotRestore restore) {
    }

    private Fixture fixture() throws Exception {
        Path run = temp.resolve("run");
        Files.createDirectories(run.resolve("mods"));
        Path base = temp.resolve("base");
        Files.createDirectories(base);
        Path settingsFile = temp.resolve("settings.json");
        SnapshotSettings settings = new SnapshotSettings(new CommunitySettings(settingsFile));
        settings.setEnabled(true);
        SnapshotStore store = new SnapshotStore(temp.resolve("snapshots"));
        SnapshotScope scope = SnapshotScope.of(run, base, run, List.of("1"));
        return new Fixture(run, store, settings, scope,
                new SnapshotCapture(store, settings), new SnapshotRestore(store, settings));
    }

    @Test
    public void captureCreatesHardlinkAndRestoreRemovesAddedFile() throws Exception {
        Fixture f = fixture();
        Path mod = f.run().resolve("mods/a.jar");
        Files.writeString(mod, "one");

        SnapshotResult result = f.capture().capture(f.scope(), SnapshotReason.AUTO_START,
                System.currentTimeMillis(), 120, true);
        assertFalse(result.skipped(), result.skipReason());
        SnapshotManifest manifest = f.store().readManifest(f.scope().scopeKey(), result.snapshotId());
        assertNotNull(manifest);
        SnapshotFileEntry entry = manifest.files().stream()
                .filter(it -> it.path().equals("mods/a.jar")).findFirst().orElseThrow();
        assertTrue(Files.isSameFile(f.store().objectPath(entry.sha256()), mod));

        Path replacement = f.run().resolve("mods/.a.jar.new");
        Files.writeString(replacement, "two");
        Files.move(replacement, mod, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(f.run().resolve("mods/b.jar"), "new");
        RestoreResult restored = f.restore().restore(f.scope(), manifest, 120);
        assertEquals(1, restored.restored());
        assertEquals(1, restored.deleted());
        assertEquals("one", Files.readString(mod));
        assertFalse(Files.exists(f.run().resolve("mods/b.jar")));
        assertNotNull(restored.preRestoreSnapshotId());
    }

    @Test
    public void budgetExceededFallsBackToManifestOnly() throws Exception {
        Fixture f = fixture();
        f.settings().setMaxBytesPerScope(16L * 1024 * 1024);
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(
                f.run().resolve("mods/big.jar").toFile(), "rw")) {
            file.setLength(17L * 1024 * 1024);
        }

        SnapshotResult result = f.capture().capture(f.scope(), SnapshotReason.AUTO_START,
                System.currentTimeMillis(), 120, true);
        assertFalse(result.skipped(), result.skipReason());
        SnapshotManifest manifest = f.store().readManifest(f.scope().scopeKey(), result.snapshotId());
        assertNotNull(manifest);
        assertEquals(SnapshotMode.MANIFEST_ONLY, manifest.mode());
        assertTrue(manifest.files().stream().allMatch(it -> it.object() == null));
    }

    @Test
    public void missingObjectDoesNotOverwriteCurrentFile() throws Exception {
        Fixture f = fixture();
        Path mod = f.run().resolve("mods/a.jar");
        Files.writeString(mod, "one");
        SnapshotResult result = f.capture().capture(f.scope(), SnapshotReason.AUTO_START,
                System.currentTimeMillis(), 120, true);
        SnapshotManifest manifest = f.store().readManifest(f.scope().scopeKey(), result.snapshotId());
        SnapshotFileEntry entry = manifest.files().get(0);
        Files.delete(f.store().objectPath(entry.sha256()));
        Files.writeString(mod, "two");

        RestoreResult restored = f.restore().restore(f.scope(), manifest, 120);
        assertTrue(restored.failed() >= 1);
        assertEquals("two", Files.readString(mod));
    }

    @Test
    public void listingMissingStoreReturnsEmpty() throws Exception {
        SnapshotStore store = new SnapshotStore(temp.resolve("absent"));
        assertTrue(store.list("missing").isEmpty());
    }

    @Test
    public void hashCachePersistsAndInvalidatesOnMtime() throws Exception {
        Path file = temp.resolve("mods/a.jar");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "one");
        long size = Files.size(file);
        long mtime = Files.getLastModifiedTime(file).toMillis();
        SnapshotHashCache cache = SnapshotHashCache.load(temp.resolve("cache-root"), 10);
        cache.put(file, size, mtime, "abc");
        cache.save();
        SnapshotHashCache reloaded = SnapshotHashCache.load(temp.resolve("cache-root"), 10);
        assertEquals("abc", reloaded.get(file, size, mtime));
        assertNull(reloaded.get(file, size, mtime + 1));
    }

    @Test
    public void garbageCollectionRemovesUnreferencedObject() throws Exception {
        Fixture f = fixture();
        String sha = "f".repeat(64);
        Path object = f.store().objectPath(sha);
        Files.createDirectories(object.getParent());
        Files.writeString(object, "orphan");
        f.store().collectGarbage();
        assertFalse(Files.exists(object));
    }

    @Test
    public void manifestOnlyRestoreKeepsFileAndReportsFailure() throws Exception {
        Fixture f = fixture();
        Path mod = f.run().resolve("mods/a.jar");
        Files.writeString(mod, "current");
        SnapshotManifest manifest = new SnapshotManifest(
                SnapshotManifest.SCHEMA, "s-manifest", f.scope().scopeKey(), "version-root",
                f.run().toString(), List.of("1"), System.currentTimeMillis(), System.currentTimeMillis(),
                SnapshotReason.AUTO_START, SnapshotMode.MANIFEST_ONLY, 120,
                List.of(new SnapshotFileEntry("mods/a.jar", 7, "f".repeat(64), null, "none", 0)),
                List.of(), new com.google.gson.JsonObject());
        RestoreResult restored = f.restore().restore(f.scope(), manifest, 120);
        assertTrue(restored.failedPaths().contains("mods/a.jar"));
        assertEquals("current", Files.readString(mod));
    }

    @Test
    public void retentionKeepsConfiguredNumber() throws Exception {
        Fixture f = fixture();
        f.settings().setKeep(2);
        Files.writeString(f.run().resolve("mods/a.jar"), "one");
        for (int i = 0; i < 4; i++) {
            SnapshotResult result = f.capture().capture(f.scope(), SnapshotReason.AUTO_START,
                    System.currentTimeMillis(), 120, true);
            assertFalse(result.skipped(), result.skipReason());
        }
        assertTrue(f.store().list(f.scope().scopeKey()).size() <= 2);
    }
}
