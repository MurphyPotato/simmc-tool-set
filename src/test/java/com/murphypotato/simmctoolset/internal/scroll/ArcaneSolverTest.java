package com.murphypotato.simmctoolset.internal.scroll;

import com.murphypotato.simmctoolset.internal.scroll.domain.ArcaneSolver;
import com.murphypotato.simmctoolset.internal.scroll.domain.CraftPlan;
import com.murphypotato.simmctoolset.internal.scroll.domain.Element;
import com.murphypotato.simmctoolset.internal.scroll.domain.ElementAmounts;
import com.murphypotato.simmctoolset.internal.scroll.domain.Material;
import com.murphypotato.simmctoolset.internal.scroll.domain.RotationBatch;
import com.murphypotato.simmctoolset.internal.scroll.domain.ScrollRecipe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArcaneSolverTest {
    @Test
    void largeRequestsAreSplitByTheServerInputLimit() {
        CraftPlan plan = plan(Map.of("辅料", 6));

        List<RotationBatch> batches = ArcaneSolver.makeRotationSchedule(List.of(plan), 64, 64);

        assertEquals(List.of(45, 19), batches.stream().map(RotationBatch::crafts).toList());
        assertTrue(batches.stream().allMatch(ArcaneSolver::batchFitsInputLimit));
        assertEquals(315, ArcaneSolver.batchInputCount(batches.getFirst()));
        assertEquals(133, ArcaneSolver.batchInputCount(batches.getLast()));
    }

    @Test
    void boundaryQuantitiesPreserveAllRequestedCrafts() {
        CraftPlan plan = plan(Map.of("辅料", 4));
        for (int quantity : List.of(1, 4, 6, 10, 64)) {
            List<RotationBatch> batches = ArcaneSolver.makeRotationSchedule(List.of(plan), quantity, 64);
            assertEquals(quantity, batches.stream().mapToInt(RotationBatch::crafts).sum(), "quantity=" + quantity);
            assertTrue(batches.stream().allMatch(ArcaneSolver::batchFitsInputLimit), "quantity=" + quantity);
        }
    }

    @Test
    void impossibleSingleCraftPlansAreFilteredWithoutZeroProgress() {
        CraftPlan impossible = plan(Map.of("辅料", 320));
        assertEquals(0, ArcaneSolver.maxCraftsPerBatch(impossible, 64));
        assertTrue(ArcaneSolver.makeRotationSchedule(List.of(impossible), 64, 64).isEmpty());

        CraftPlan exactlyAtLimit = plan(Map.of("辅料", 319));
        List<RotationBatch> batches = ArcaneSolver.makeRotationSchedule(
            List.of(exactlyAtLimit), 2, 64
        );
        assertEquals(List.of(1, 1), batches.stream().map(RotationBatch::crafts).toList());
        assertTrue(batches.stream().allMatch(ArcaneSolver::batchFitsInputLimit));
    }

    @Test
    void batchMaterialsAndAggregateUseOnlyScheduledBatches() {
        CraftPlan first = plan(Map.of("甲", 2));
        CraftPlan second = plan(Map.of("乙", 3));
        List<RotationBatch> batches = ArcaneSolver.makeRotationSchedule(List.of(first, second), 10, 4);

        assertEquals(List.of(4, 4, 2), batches.stream().map(RotationBatch::crafts).toList());
        assertEquals(Map.of("甲", 12, "乙", 12, "主材", 10),
            ArcaneSolver.aggregateRotationMaterials(batches, true, "主材"));
        assertEquals(Map.of("甲", 8, "主材", 4),
            ArcaneSolver.scaleBatchMaterials(batches.getFirst(), true, "主材"));
    }

    @Test
    void impurityAndTargetExpansionCapsAreStrict() {
        ScrollRecipe recipe = recipe(2);
        Material impure = new Material("杂质过高", amounts(1, 8), 1);
        Material excessive = new Material("目标溢出", amounts(27, 0), 2);

        assertTrue(ArcaneSolver.findCraftPlans(recipe, List.of(impure)).isEmpty());
        assertTrue(ArcaneSolver.findCraftPlans(recipe, List.of(excessive)).isEmpty());
    }

    private static CraftPlan plan(Map<String, Integer> materials) {
        return new CraftPlan("plan-" + materials, materials, ElementAmounts.zero(), 0, 0,
            materials.values().stream().mapToInt(Integer::intValue).sum(),
            materials.values().stream().mapToInt(Integer::intValue).max().orElse(0),
            materials.size(), 0L);
    }

    private static ScrollRecipe recipe(int metal) {
        return new ScrollRecipe("测试卷轴", "主材", amounts(metal, 0));
    }

    private static ElementAmounts amounts(int metal, int wood) {
        int[] values = new int[Element.values().length];
        values[Element.METAL.ordinal()] = metal;
        values[Element.WOOD.ordinal()] = wood;
        return new ElementAmounts(values);
    }
}
