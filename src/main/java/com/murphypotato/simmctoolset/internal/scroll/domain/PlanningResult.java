package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.util.List;

/** Search outcome; timeout returns its best-so-far plan, cancellation does not. */
public record PlanningResult(PlanningStatus status, EvaluatedPlan plan, List<CraftPlan> candidatePlans,
                             CraftPlan closestCandidate, String explanation) {
    public PlanningResult(PlanningStatus status, EvaluatedPlan plan, List<CraftPlan> candidatePlans) {
        this(status, plan, candidatePlans, null, "");
    }
    public PlanningResult {
        candidatePlans = List.copyOf(candidatePlans == null ? List.of() : candidatePlans);
    }
}
