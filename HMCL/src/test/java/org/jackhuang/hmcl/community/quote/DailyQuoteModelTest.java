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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// 每日一言数据模型测试。
public final class DailyQuoteModelTest {

    @Test
    public void formatsDisplayLine() {
        DailyQuote full = new DailyQuote(
                "  test line  ",
                "Author",
                "Source",
                QuoteKind.POETRY,
                QuoteOrigin.ONLINE,
                "id",
                "Name");
        assertEquals("test line — Author《Source》", full.displayLine());
        assertEquals("Name", full.originName());

        DailyQuote textOnly = new DailyQuote(
                "only text",
                null,
                null,
                QuoteKind.MC,
                QuoteOrigin.BUILTIN,
                null,
                null);
        assertEquals("only text", textOnly.displayLine());
        assertEquals("Built-in", textOnly.originName());
    }

    @Test
    public void rejectsInvalidSources() {
        assertThrows(IllegalArgumentException.class,
                () -> new QuoteSource("Hitokoto", QuoteSourceType.HITOKOTO, "Name", null, true));
        assertThrows(IllegalArgumentException.class,
                () -> new QuoteSource("custom", QuoteSourceType.CUSTOM, "Name", null, true));
        assertThrows(IllegalArgumentException.class,
                () -> new QuoteSource("custom", QuoteSourceType.CUSTOM, "Name", "ftp://example.com", true));
        assertThrows(IllegalArgumentException.class,
                () -> new QuoteSource("custom", QuoteSourceType.CUSTOM, " ", "https://example.com", true));

        QuoteSource source = new QuoteSource("custom", QuoteSourceType.CUSTOM, "Name", "https://example.com/api", false);
        assertEquals("example.com/api", source.host());
        assertTrue(source.withEnabled(true).enabled());
    }

    @Test
    public void filtersEnabledSources() {
        QuoteSource hitokoto = new QuoteSource("hitokoto", QuoteSourceType.HITOKOTO, "Hitokoto", null, true);
        QuoteSource custom = new QuoteSource("custom", QuoteSourceType.CUSTOM, "Custom", "https://example.com", false);
        DailyQuoteSettings.Config config = new DailyQuoteSettings.Config(true, true, List.of(hitokoto, custom));
        assertEquals(List.of(hitokoto), config.enabledSources());
        assertFalse(config.withOnline(false).online());
    }

    @Test
    public void checksCacheFreshness() {
        Instant now = Instant.parse("2026-10-24T00:00:00Z");
        DailyQuote quote = BuiltinQuotes.fallback();
        assertTrue(new DailyQuoteStore.Cached(quote, now.minusSeconds(60)).isFresh(now));
        assertFalse(new DailyQuoteStore.Cached(quote, now.minusSeconds(11 * 60)).isFresh(now));
        assertFalse(new DailyQuoteStore.Cached(quote, now.plusSeconds(3 * 60)).isFresh(now));
    }

    @Test
    public void loadsBuiltinCorpus() {
        assertEquals(120, BuiltinQuotes.all().size());
        assertEquals(60, BuiltinQuotes.all().stream().filter(q -> q.kind() == QuoteKind.MC).count());
        assertEquals(60, BuiltinQuotes.all().stream().filter(q -> q.kind() == QuoteKind.POETRY).count());
        DailyQuote quote = BuiltinQuotes.forDate(java.time.LocalDate.of(2026, 10, 24));
        assertEquals(quote, BuiltinQuotes.forDate(java.time.LocalDate.of(2026, 10, 24)));
        assertFalse(quote.text().isBlank());
    }
}
