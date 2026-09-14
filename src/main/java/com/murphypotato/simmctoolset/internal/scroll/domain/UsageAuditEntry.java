package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UsageAuditEntry(UUID transactionId, UUID playerId, Instant timestamp,
        Map<String, Integer> oldTotals, Map<String, Integer> newTotals, String reason) {
    public UsageAuditEntry {
        oldTotals = Map.copyOf(oldTotals == null ? Map.of() : oldTotals);
        newTotals = Map.copyOf(newTotals == null ? Map.of() : newTotals);
    }
}
