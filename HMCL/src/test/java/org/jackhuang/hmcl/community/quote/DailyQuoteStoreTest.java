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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 每日一言缓存测试。
public final class DailyQuoteStoreTest {

    @TempDir
    private Path tempDir;

    @Test
    public void savesAndLoadsCache() {
        DailyQuoteStore store = new DailyQuoteStore(tempDir.resolve("quote.json"));
        DailyQuote quote = new DailyQuote(
                "line",
                "Author",
                "Source",
                QuoteKind.POETRY,
                QuoteOrigin.ONLINE,
                "hitokoto",
                "Hitokoto");
        Instant fetchedAt = Instant.parse("2026-10-24T00:00:00Z");
        assertTrue(store.save(new DailyQuoteStore.Cached(quote, fetchedAt)));

        DailyQuoteStore.Cached loaded = store.load();
        assertEquals(quote, loaded.quote());
        assertEquals(fetchedAt, loaded.fetchedAt());
    }

    @Test
    public void ignoresCorruptCache() throws Exception {
        DailyQuoteStore store = new DailyQuoteStore(tempDir.resolve("quote.json"));
        Files.writeString(store.path(), "{not json", java.nio.charset.StandardCharsets.UTF_8);
        assertNull(store.load());

        Files.writeString(store.path(), "{\"quote\":{\"text\":\"x\"}}", java.nio.charset.StandardCharsets.UTF_8);
        assertNull(store.load());
    }

    @Test
    public void clearsCache() {
        DailyQuoteStore store = new DailyQuoteStore(tempDir.resolve("quote.json"));
        store.save(new DailyQuoteStore.Cached(BuiltinQuotes.fallback(), Instant.now()));
        assertTrue(Files.isRegularFile(store.path()));
        store.clear();
        assertTrue(Files.notExists(store.path()));
    }
}
