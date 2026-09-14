package com.murphypotato.simmctoolset.internal.scroll;

import com.murphypotato.simmctoolset.internal.scroll.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ScrollDomainPlannerTest {
    @Test
    void polynomialKeepsInterceptAndIndependentConstants() {
        assertEquals(7.16298240, MaterialDecay.cumulativeFactor(0), 1e-8);
        assertEquals(1.05914545, MaterialDecay.incremental(1, 0, 1), 1e-8);
        assertEquals(15.47987925, MaterialDecay.incremental(3, 3, 5), 1e-8);
    }

    @Test
    void incrementsTelescopeWithoutEarlyRounding() {
        BigDecimal whole = MaterialDecay.incremental(BigDecimal.ONE, 0, 8);
        BigDecimal pieces = MaterialDecay.incremental(BigDecimal.ONE, 0, 3)
            .add(MaterialDecay.incremental(BigDecimal.ONE, 3, 5));
        assertEquals(whole, pieces);
        assertEquals(0d, MaterialDecay.incremental(5, 12, 0));
        assertTrue(MaterialDecay.cumulativeFactor(-10) < MaterialDecay.cumulativeFactor(0));
    }

    @Test
    void invalidCountsAreRejectedButNegativeCurveResultsAreNotClamped() {
        assertThrows(IllegalArgumentException.class, () -> MaterialDecay.incremental(1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> MaterialDecay.incremental(1, 1, -1));
        assertTrue(MaterialDecay.incremental(1, 0, 1) > 0);
    }

    @Test
    void evaluatorUsesSharedUsageAndManualEvaluationDoesNotSearch() {
        Material ore = material("铜", 1, 0);
        ScrollRecipe recipe = new ScrollRecipe("一金", "主材", amounts(1, 0));
        CraftPlan vector = new CraftPlan("铜", Map.of("铜", 1), amounts(1, 0),
            0, 0, 1, 1, 1, 0);
        EvaluatedPlan plan = DecayPlanner.evaluate(
            List.of(new RotationBatch(vector, 1), new RotationBatch(vector, 1)),
            recipe, List.of(ore), Map.of("铜", 0));
        assertEquals(2, plan.plannedCrafts());
        assertEquals(2, plan.afterUsage().get("铜"));
        assertEquals(plan.batches().get(0).afterUsage().get("铜") + 1,
            plan.batches().get(1).afterUsage().get("铜"));
        assertEquals(2, plan.theoreticalElements().get(Element.METAL));
    }

    @Test
    void plannerRespectsExclusionsInputCapBudgetAndCancellation() {
        Material good = material("好", 2, 0);
        Material excluded = material("不要", 0, 10);
        ScrollRecipe recipe = new ScrollRecipe("两金", "主材", amounts(2, 0));
        ScrollPlanningRequest request = new ScrollPlanningRequest(recipe, List.of(good, excluded),
            4, Map.of(), Map.of("好", 4), Set.of("不要"), SearchBudget.FAST);
        PlanningResult result = DecayPlanner.plan(request);
        assertEquals(PlanningStatus.COMPLETE, result.status());
        assertTrue(result.plan().batches().stream().noneMatch(b -> b.plan().materials().containsKey("不要")));
        assertTrue(result.plan().batches().stream().allMatch(b -> ArcaneSolver.batchInputCount(
            new RotationBatch(b.plan(), b.crafts())) <= ArcaneSolver.MAX_BATCH_INPUTS));

        AtomicBoolean cancelled = new AtomicBoolean(true);
        ScrollPlanningRequest cancelledRequest = new ScrollPlanningRequest(recipe, List.of(good),
            4, Map.of(), Map.of(), Set.of(), SearchBudget.BALANCED, cancelled::get);
        assertThrows(DecayPlanner.PlanningCancelledException.class, () -> DecayPlanner.plan(cancelledRequest));
    }

    @Test
    void incompleteManualTargetIsNotReportedFeasible() {
        Material weak = material("弱", 1, 0);
        ScrollRecipe recipe = new ScrollRecipe("六金", "主材", amounts(6, 0));
        CraftPlan vector = new CraftPlan("weak", Map.of("弱", 1), amounts(1, 0),
            0, 0, 1, 1, 1, 0);
        EvaluatedPlan result = DecayPlanner.evaluate(List.of(new RotationBatch(vector, 1)),
            recipe, List.of(weak), Map.of());
        assertFalse(result.feasible());
        assertEquals(1, result.plannedCrafts());
    }

    private static Material material(String name, int metal, int wood) {
        return new Material(name, amounts(metal, wood), 1 + name.hashCode() & 0x7fffffff);
    }
    private static ElementAmounts amounts(int metal, int wood) {
        int[] values = new int[Element.values().length];
        values[Element.METAL.ordinal()] = metal;
        values[Element.WOOD.ordinal()] = wood;
        return new ElementAmounts(values);
    }
}
