package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable complete schedule and its exact nonlinear accounting. */
public record EvaluatedPlan(
    int desiredCrafts,
    int plannedCrafts,
    List<EvaluatedBatch> batches,
    Map<String, Integer> beforeUsage,
    Map<String, Integer> afterUsage,
    ElementAmounts theoreticalElements,
    Map<Element, BigDecimal> effectiveElements,
    boolean feasible,
    int impurity,
    int excess,
    Map<String, Integer> extraMaterials,
    BigDecimal efficiency,
    PlanningStatus status
) {
    public EvaluatedPlan {
        if (desiredCrafts < 0 || plannedCrafts < 0 || plannedCrafts > desiredCrafts) {
            throw new IllegalArgumentException("计划数量无效");
        }
        batches = List.copyOf(batches == null ? List.of() : batches);
        beforeUsage = immutableUsage(beforeUsage);
        afterUsage = immutableUsage(afterUsage);
        theoreticalElements = theoreticalElements == null ? ElementAmounts.zero() : theoreticalElements;
        effectiveElements = immutableElements(effectiveElements);
        extraMaterials = immutableUsage(extraMaterials);
        efficiency = efficiency == null ? BigDecimal.ZERO : efficiency;
        status = status == null ? PlanningStatus.NO_FEASIBLE_PLAN : status;
    }

    public boolean complete() { return plannedCrafts == desiredCrafts && feasible; }
    public boolean hasExtraMaterials() { return !extraMaterials.isEmpty(); }

    private static Map<Element, BigDecimal> immutableElements(Map<Element, BigDecimal> values) {
        Map<Element, BigDecimal> copy = new LinkedHashMap<>();
        for (Element element : Element.values()) copy.put(element, values == null ? BigDecimal.ZERO : values.getOrDefault(element, BigDecimal.ZERO));
        return Collections.unmodifiableMap(copy);
    }
    private static Map<String, Integer> immutableUsage(Map<String, Integer> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
    }
}
