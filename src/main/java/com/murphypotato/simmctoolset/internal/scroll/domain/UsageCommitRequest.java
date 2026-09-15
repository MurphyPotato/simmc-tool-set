package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UsageCommitRequest(
        UUID transactionId, UUID playerId, LocalDate expectedDate, long expectedRevision,
        String recipe, List<UsagePlanInput> orderedPlanInputs, int requestedCrafts,
        Map<String, Integer> actualMaterialUsage, int beforeM, int afterM,
        boolean autoMode, boolean modified, boolean acknowledgedInfeasible) {
    public UsageCommitRequest(
        UUID transactionId, UUID playerId, LocalDate expectedDate, long expectedRevision,
        String recipe, List<UsagePlanInput> orderedPlanInputs, int requestedCrafts,
        Map<String, Integer> actualMaterialUsage, int beforeM, int afterM,
        boolean autoMode, boolean modified) {
        this(transactionId, playerId, expectedDate, expectedRevision, recipe, orderedPlanInputs,
            requestedCrafts, actualMaterialUsage, beforeM, afterM, autoMode, modified, false);
    }

    public UsageCommitRequest {
        if (transactionId == null || playerId == null || expectedDate == null) throw new IllegalArgumentException("事务身份不完整");
        if (expectedRevision < 0 || recipe == null || recipe.isBlank() || requestedCrafts < 0) throw new IllegalArgumentException("事务参数无效");
        orderedPlanInputs = List.copyOf(orderedPlanInputs == null ? List.of() : orderedPlanInputs);
        actualMaterialUsage = nonnegative(actualMaterialUsage);
        if (beforeM < 0 || afterM < 0) throw new IllegalArgumentException("M 不能为负数");
    }
    public static Map<String, Integer> nonnegative(Map<String, Integer> values) {
        if (values == null) throw new IllegalArgumentException("材料不能为空");
        var copy = new java.util.LinkedHashMap<String, Integer>();
        values.forEach((name, count) -> {
            if (name == null || name.isBlank() || count == null || count < 0) throw new IllegalArgumentException("材料数量必须为非负整数");
            copy.put(name, count);
        });
        return Map.copyOf(copy);
    }
}
