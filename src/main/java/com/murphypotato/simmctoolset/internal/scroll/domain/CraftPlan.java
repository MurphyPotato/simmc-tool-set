package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CraftPlan(
    String id,
    Map<String, Integer> materials,
    ElementAmounts supplied,
    int impurityTotal,
    int targetExcessTotal,
    int materialTotal,
    int maxRepeat,
    int distinctMaterials,
    long score
) {
    public CraftPlan {
        materials = Collections.unmodifiableMap(new LinkedHashMap<>(materials));
    }
}
