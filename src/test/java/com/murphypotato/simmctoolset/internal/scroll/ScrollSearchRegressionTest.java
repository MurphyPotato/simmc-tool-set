package com.murphypotato.simmctoolset.internal.scroll;

import com.murphypotato.simmctoolset.internal.scroll.domain.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScrollSearchRegressionTest {
    @Test
    void bundledScrollsReturnFeasiblePlansOrTheProvenImpurityConflictAcrossAllBudgets() {
        GameData data = GameData.load();
        java.util.List<String> failures = new java.util.ArrayList<>();
        for (SearchBudget budget : SearchBudget.values()) {
            long worst = 0;
            for (ScrollRecipe recipe : data.recipes()) {
                long start = System.nanoTime();
                PlanningResult result = DecayPlanner.plan(new ScrollPlanningRequest(
                    recipe, data.materials(), 1, Map.of(), budget));
                long elapsed = System.nanoTime() - start;
                worst = Math.max(worst, elapsed);
                if (recipe.name().equals("混乱射线卷轴")) {
                    // Every source of dark in the bundled table has dark/impurity
                    // <= 3. Requiring 24 dark forces impurity >= 8, independently
                    // of per-material decay at nonnegative contributions.
                    assertEquals(PlanningStatus.NO_FEASIBLE_PLAN, result.status());
                    assertFalse(result.explanation().isBlank());
                    assertTrue(result.plan().batches().isEmpty());
                    continue;
                }
                if (!result.plan().complete()) {
                    failures.add("recipe-index=" + data.recipes().indexOf(recipe) + "/" + budget + "/" + result.status());
                    System.out.println("FAILED recipe-index=" + data.recipes().indexOf(recipe) + ", name="
                        + recipe.name() + ", budget=" + budget);
                    continue;
                }
                assertTrue(result.plan().feasible());
                assertTrue(elapsed < budget.nanos() + 250_000_000L, "Deadline overshoot: " + recipe.name());
            }
            System.out.println("all-scrolls " + budget + ": " + (data.recipes().size() - 1)
                + " feasible + 1 proven ratio conflict, slowest-ms=" + worst / 1_000_000);
        }
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void healingMultiBatchSearchReplansUsingUpdatedDailyMaterialCounts() {
        GameData data = GameData.load();
        ScrollRecipe recipe = data.recipe("治愈术卷轴");
        for (SearchBudget budget : SearchBudget.values()) {
            for (int quantity : new int[]{16, 32, 64}) {
                long start = System.nanoTime();
                PlanningResult result = DecayPlanner.plan(new ScrollPlanningRequest(
                    recipe, data.materials(), quantity, Map.of(), budget));
                System.out.println("healing-" + quantity + "/" + budget + ": " + result.status() + " "
                    + result.plan().plannedCrafts() + "/" + quantity
                    + ", ms=" + (System.nanoTime() - start) / 1_000_000);
                assertTrue(result.plan().complete(), quantity + "/" + budget + "/" + result.status());
                Map<String, Integer> expectedUsage = new java.util.HashMap<>();
                for (EvaluatedBatch batch : result.plan().batches()) {
                    assertTrue(batch.feasible());
                    assertTrue(ArcaneSolver.batchFitsInputLimit(new RotationBatch(batch.plan(), batch.crafts())));
                    batch.plan().materials().forEach((name, count) ->
                        expectedUsage.merge(name, count * batch.crafts(), Integer::sum));
                }
                assertEquals(expectedUsage, result.plan().afterUsage());
            }
        }
    }

    @Test
    void healingScrollWithBundledMaterialsFindsExecutablePlanInFastMode() {
        GameData data = GameData.load();
        ScrollRecipe recipe = data.recipe("治愈术卷轴");
        long start = System.nanoTime();
        PlanningResult result = DecayPlanner.plan(new ScrollPlanningRequest(
            recipe, data.materials(), 1, Map.of(), SearchBudget.FAST));
        System.out.println("healing-fast: " + result.status() + ", crafts="
            + result.plan().plannedCrafts() + ", ms=" + (System.nanoTime() - start) / 1_000_000);
        assertTrue(result.plan().complete(), "Bundled healing recipe must yield a usable plan, not an empty timeout");
        assertEquals(1, result.plan().plannedCrafts());
        var effective = result.plan().effectiveElements();
        assertTrue(effective.get(Element.WOOD).doubleValue() >= 10);
        assertTrue(effective.get(Element.WATER).doubleValue() >= 12);
        assertTrue(effective.get(Element.LIGHT).doubleValue() >= 24);
        assertTrue(result.plan().impurity() < 8);
    }
}
