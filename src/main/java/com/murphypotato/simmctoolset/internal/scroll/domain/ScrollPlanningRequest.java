package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Immutable input to the nonlinear multi-batch planner. */
public record ScrollPlanningRequest(
    ScrollRecipe recipe,
    java.util.List<Material> materials,
    int desiredCrafts,
    Map<String, Integer> currentUsage,
    Map<String, Integer> materialBudget,
    SearchBudget budget,
    BooleanSupplier cancellation
) {
    public ScrollPlanningRequest {
        Objects.requireNonNull(recipe, "recipe");
        materials = java.util.List.copyOf(materials == null ? java.util.List.of() : materials);
        if (desiredCrafts < 0) throw new IllegalArgumentException("目标制作数量不能为负数");
        currentUsage = immutableNonNegative(currentUsage, "当前用量");
        materialBudget = immutableNonNegative(materialBudget, "材料预算");
        budget = budget == null ? SearchBudget.BALANCED : budget;
        cancellation = cancellation == null ? () -> false : cancellation;
    }

    public ScrollPlanningRequest(
        ScrollRecipe recipe, java.util.List<Material> materials, int desiredCrafts,
        Map<String, Integer> currentUsage, SearchBudget budget
    ) {
        this(recipe, materials, desiredCrafts, currentUsage, Map.of(), budget, () -> false);
    }

    public ScrollPlanningRequest(
        ScrollRecipe recipe, java.util.List<Material> materials, int desiredCrafts,
        Map<String, Integer> currentUsage, Map<String, Integer> materialBudget, SearchBudget budget
    ) {
        this(recipe, materials, desiredCrafts, currentUsage, materialBudget, budget, () -> false);
    }

    private static Map<String, Integer> immutableNonNegative(Map<String, Integer> values, String label) {
        java.util.LinkedHashMap<String, Integer> copy = new java.util.LinkedHashMap<>();
        if (values != null) {
            values.forEach((name, amount) -> {
                if (name == null || name.isBlank() || amount == null || amount < 0) {
                    throw new IllegalArgumentException(label + "无效");
                }
                copy.put(name, amount);
            });
        }
        return Map.copyOf(copy);
    }
}
