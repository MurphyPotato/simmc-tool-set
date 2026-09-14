package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UsageRecord(UUID transactionId, UUID playerId, LocalDate beijingDate, Instant timestamp,
        String recipe, List<UsagePlanInput> orderedPlanInputs, int requestedCrafts,
        Map<String, Integer> actualMaterialUsage, int beforeM, int afterM,
        boolean autoMode, boolean modified) {
    public UsageRecord {
        if (transactionId == null || playerId == null || beijingDate == null || timestamp == null) throw new IllegalArgumentException("记录身份不完整");
        orderedPlanInputs = List.copyOf(orderedPlanInputs == null ? List.of() : orderedPlanInputs);
        actualMaterialUsage = Map.copyOf(actualMaterialUsage == null ? Map.of() : actualMaterialUsage);
    }
}
