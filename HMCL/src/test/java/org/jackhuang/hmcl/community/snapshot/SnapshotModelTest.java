package org.jackhuang.hmcl.community.snapshot;

import com.google.gson.JsonObject;
import org.jackhuang.hmcl.community.env.CommunitySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SnapshotModelTest {

    @TempDir
    Path temp;

    @Test
    public void manifestRoundTripPreservesUnknownFields() {
        JsonObject extra = new JsonObject();
        extra.addProperty("futureField", "kept");
        SnapshotManifest manifest = new SnapshotManifest(
                SnapshotManifest.SCHEMA, "s1", "scope1", "shared", "/run", List.of("a", "b"),
                1L, 2L, SnapshotReason.AUTO_START, SnapshotMode.HARDLINK, 120,
                List.of(new SnapshotFileEntry("mods/a.jar", 10, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "objects/ab/abc", "hardlink", 5)),
                List.of("saves/"), extra);
        SnapshotManifest parsed = SnapshotManifest.fromJson(manifest.toJson());
        assertNotNull(parsed);
        assertEquals("s1", parsed.snapshotId());
        assertEquals(SnapshotReason.AUTO_START, parsed.reason());
        assertEquals(SnapshotMode.HARDLINK, parsed.mode());
        assertEquals(1, parsed.files().size());
        assertEquals("kept", parsed.extra().get("futureField").getAsString());
    }

    @Test
    public void manifestRejectsUnsafePathsAndIds() {
        assertTrue(SnapshotManifest.isSafeRelativePath("mods/a.jar"));
        assertFalse(SnapshotManifest.isSafeRelativePath("../a.jar"));
        assertFalse(SnapshotManifest.isSafeRelativePath("/a.jar"));
        assertFalse(SnapshotManifest.isSafeRelativePath("mods\\a.jar"));
        assertTrue(SnapshotManifest.isSafeId("20260101-120000-000"));
        assertFalse(SnapshotManifest.isSafeId("../x"));
    }

    @Test
    public void scopeDistinguishesSharedRunDirectory() {
        Path base = temp.resolve("base");
        Path run = base.resolve("versions/a");
        SnapshotScope isolated = SnapshotScope.of(run, base, run, List.of("a"));
        assertEquals("version-root", isolated.label());
        assertFalse(isolated.shared());
        SnapshotScope shared = SnapshotScope.of(run, base, base.resolve("versions/b"), List.of("a", "b"));
        assertEquals("shared", shared.label());
        assertTrue(shared.shared());
        assertEquals(16, shared.scopeKey().length());
    }

    @Test
    public void settingsDefaultAndClamp() {
        SnapshotSettings settings = new SnapshotSettings(new CommunitySettings(temp.resolve("s.json")));
        assertTrue(settings.enabled());
        assertTrue(settings.autoCaptureOnSuccess());
        assertEquals(3, settings.keep());
        assertEquals(2L * 1024 * 1024 * 1024, settings.maxBytesPerScope());
        settings.setKeep(99);
        assertEquals(10, settings.keep());
        settings.setKeep(0);
        assertEquals(1, settings.keep());
        settings.setMaxBytesPerScope(1);
        assertEquals(16L * 1024 * 1024, settings.maxBytesPerScope());
        settings.setEnabled(false);
        assertFalse(settings.enabled());
    }
}
