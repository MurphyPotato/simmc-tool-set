package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable one-batch result, including the usage transition it consumed. */
public record EvaluatedBatch(
    CraftPlan plan,
    int crafts,
    ElementAmounts theoreticalElements,
    Map<Element, BigDecimal> effectiveElements,
    Map<String, Integer> beforeUsage,
    Map<String, Integer> afterUsage,
    boolean feasible,
    int impurity,
    int excess,
    Map<String, Integer> extraMaterials,
    BigDecimal efficiency
) {
    public EvaluatedBatch {
        if (plan == null || crafts <= 0) throw new IllegalArgumentException("批次无效");
        theoreticalElements = theoreticalElements == null ? ElementAmounts.zero() : theoreticalElements;
        effectiveElements = immutableElements(effectiveElements);
        beforeUsage = immutableUsage(beforeUsage);
        afterUsage = immutableUsage(afterUsage);
        extraMaterials = immutableUsage(extraMaterials);
        efficiency = efficiency == null ? BigDecimal.ZERO : efficiency;
    }

    private static Map<Element, BigDecimal> immutableElements(Map<Element, BigDecimal> values) {
        Map<Element, BigDecimal> copy = new LinkedHashMap<>();
        for (Element element : Element.values()) copy.put(element, values == null ? BigDecimal.ZERO : values.getOrDefault(element, BigDecimal.ZERO));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Integer> immutableUsage(Map<String, Integer> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
    }
}
