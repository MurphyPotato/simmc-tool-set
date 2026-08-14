package com.murphypotato.simmctoolset.internal.scroll.client;

import com.murphypotato.simmctoolset.internal.scroll.domain.CraftPlan;
import com.murphypotato.simmctoolset.internal.scroll.domain.ScrollRecipe;

import java.util.List;

public record CalculationResult(
    ScrollRecipe recipe,
    List<CraftPlan> plans,
    int quantity,
    boolean includeMainMaterial,
    int repeatThreshold,
    long elapsedNanos
) {
    public CalculationResult {
        plans = List.copyOf(plans);
    }
}
