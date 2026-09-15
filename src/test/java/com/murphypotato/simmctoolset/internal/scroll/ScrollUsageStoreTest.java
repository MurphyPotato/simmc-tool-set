package com.murphypotato.simmctoolset.internal.scroll;

import com.murphypotato.simmctoolset.internal.scroll.config.ScrollUsageStore;
import com.murphypotato.simmctoolset.internal.scroll.domain.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ScrollUsageStoreTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-09-15T16:30:00Z");

    private static UsageCommitRequest request(UUID tx, UUID player, LocalDate date, long rev, Map<String,Integer> use) {
        return new UsageCommitRequest(tx, player, date, rev, "test-scroll", List.of(
                new UsagePlanInput(1, use)), 1, use, use.values().stream().mapToInt(Integer::intValue).sum(),
                use.values().stream().mapToInt(Integer::intValue).sum(), true, false);
    }

    @Test
    void isolatesPlayersAndBeijingDates() throws Exception {
        Path dir = Files.createTempDirectory("usage-store");
        MutableClock clock = new MutableClock(NOW, ZoneId.of("Asia/Shanghai"));
        ScrollUsageStore store = new ScrollUsageStore(dir.resolve("usage.json"), clock);
        LocalDate date = LocalDate.of(2026, 9, 16);
        store.commit(request(UUID.randomUUID(), A, date, 0, Map.of("魂土", 3)));
        assertEquals(3, store.snapshot(A).totals().get("魂土"));
        assertTrue(store.snapshot(B).totals().isEmpty());
        clock.setInstant(Instant.parse("2026-09-16T16:30:00Z"));
        assertEquals(LocalDate.of(2026, 9, 17), store.snapshot(A).beijingDate());
        assertTrue(store.snapshot(A).totals().isEmpty());
        assertEquals(1, store.history(A).size());
    }

    @Test
    void commitIsIdempotentAndPersistsAcrossReload() throws Exception {
        Path dir = Files.createTempDirectory("usage-store");
        Path file = dir.resolve("usage.json");
        MutableClock clock = new MutableClock(NOW, ZoneId.of("Asia/Shanghai"));
        LocalDate date = LocalDate.of(2026, 9, 16);
        UUID tx = UUID.randomUUID();
        ScrollUsageStore store = new ScrollUsageStore(file, clock);
        var first = store.commit(request(tx, A, date, 0, Map.of("魂土", 2)));
        var duplicate = store.commit(request(tx, A, date, 0, Map.of("魂土", 2)));
        assertFalse(first.duplicate());
        assertTrue(duplicate.duplicate());
        assertEquals(2, store.snapshot(A).totals().get("魂土"));
        ScrollUsageStore reloaded = new ScrollUsageStore(file, clock);
        assertEquals(2, reloaded.snapshot(A).totals().get("魂土"));
        assertEquals(1, reloaded.history(A).size());
    }

    @Test
    void editRequiresAcknowledgementAndAuditsChange() throws Exception {
        Path dir = Files.createTempDirectory("usage-store");
        MutableClock clock = new MutableClock(NOW, ZoneId.of("Asia/Shanghai"));
        ScrollUsageStore store = new ScrollUsageStore(dir.resolve("usage.json"), clock);
        LocalDate date = LocalDate.of(2026, 9, 16);
        store.commit(request(UUID.randomUUID(), A, date, 0, Map.of("魂土", 4)));
        assertThrows(IllegalArgumentException.class, () ->
                store.edit(A, date, 1, Map.of("魂土", 7), 7, false, "mistake"));
        var edited = store.edit(A, date, 1, Map.of("魂土", 7), 7, true, "correct server count");
        assertEquals(7, edited.totals().get("魂土"));
        assertEquals(1, store.audits(A).size());
        assertEquals(4, store.audits(A).getFirst().oldTotals().get("魂土"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant; private final ZoneId zone;
        MutableClock(Instant instant, ZoneId zone) { this.instant = instant; this.zone = zone; }
        void setInstant(Instant value) { instant = value; }
        public ZoneId getZone() { return zone; }
        public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }
        public Instant instant() { return instant; }
    }
}
