package com.murphypotato.simmctoolset.internal.scroll.domain;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SearchDeadlineRegressionTest {
    @Test
    void deadlineDuringSearchNeverDiscardsAnAlreadyPublishedFeasibleCandidate() {
        var recipe = new ScrollRecipe("one", "main", new ElementAmounts(new int[]{1,0,0,0,0,0,0,0}));
        var material = new Material("metal", recipe.required(), 0);
        var request = new ScrollPlanningRequest(recipe, List.of(material), 2, Map.of(), SearchBudget.FAST);
        boolean timedOutAfterCandidate = false;
        for (int cutoff = 1; cutoff < 35; cutoff++) {
            AtomicInteger clockReads = new AtomicInteger();
            int limit = cutoff;
            PlanningResult result = DecayPlanner.plan(request,
                () -> clockReads.getAndIncrement() < limit ? 0L : SearchBudget.FAST.nanos());
            if (result.status() == PlanningStatus.TIMED_OUT && !result.candidatePlans().isEmpty()) {
                timedOutAfterCandidate = true;
                assertFalse(result.plan().batches().isEmpty(), "Found candidate was lost on timeout");
                assertTrue(result.plan().feasible(), "Diagnostic candidate must not be executable");
            }
        }
        assertTrue(timedOutAfterCandidate, "Fixture must exercise deadline after candidate discovery");
    }

    @Test
    void fixedPointSearchDoesNotApplyTheoreticalExcessCapToDecayedAmounts() {
        var recipe = new ScrollRecipe("five", "main", new ElementAmounts(new int[]{5,0,0,0,0,0,0,0}));
        var material = new Material("metal", new ElementAmounts(new int[]{1,0,0,0,0,0,0,0}), 0);
        var result = DecayPlanner.plan(new ScrollPlanningRequest(recipe, List.of(material), 1,
            Map.of("metal", 150), SearchBudget.FAST));
        assertTrue(result.plan().complete());
        assertEquals(31, result.plan().batches().getFirst().plan().materials().get("metal"));
        // Independently expanded polynomial: 31 * (1.0618608 - .0054307*150 - .00271535*31).
        assertEquals(0, result.plan().effectiveElements().get(Element.METAL)
            .compareTo(new java.math.BigDecimal("5.05547845")));
    }
}
