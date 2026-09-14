package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record UsageSnapshot(UUID playerId, LocalDate beijingDate, long revision, int currentM,
        Map<String, Integer> totals, boolean readOnly, String warning) {
    public UsageSnapshot {
        totals = Map.copyOf(totals == null ? Map.of() : totals);
    }
}
