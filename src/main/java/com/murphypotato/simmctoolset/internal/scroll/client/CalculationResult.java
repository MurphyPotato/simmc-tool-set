package com.murphypotato.simmctoolset.internal.scroll.client;

import com.murphypotato.simmctoolset.internal.scroll.domain.CraftPlan;
import com.murphypotato.simmctoolset.internal.scroll.domain.ScrollRecipe;
import com.murphypotato.simmctoolset.internal.scroll.domain.PlanningResult;

import java.util.List;

public record CalculationResult(
    ScrollRecipe recipe,
    List<CraftPlan> plans,
    int quantity,
    boolean includeMainMaterial,
    int repeatThreshold,
    long elapsedNanos,
    PlanningResult planning
) {
    public CalculationResult(ScrollRecipe recipe, List<CraftPlan> plans, int quantity,
                             boolean includeMainMaterial, int repeatThreshold, long elapsedNanos) {
        this(recipe, plans, quantity, includeMainMaterial, repeatThreshold, elapsedNanos, null);
    }
    public CalculationResult {
        plans = List.copyOf(plans);
    }

    public boolean timedOut() {
        return planning != null && planning.status() == com.murphypotato.simmctoolset.internal.scroll.domain.PlanningStatus.TIMED_OUT;
    }
}
