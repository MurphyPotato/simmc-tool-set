package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public record UsagePlanInput(int quantity, Map<String, Integer> materials) {
    public UsagePlanInput {
        if (quantity < 0) throw new IllegalArgumentException("计划数量不能为负数");
        if (materials == null) throw new IllegalArgumentException("材料不能为空");
        var copy = new LinkedHashMap<String, Integer>();
        materials.forEach((name, count) -> {
            if (name == null || name.isBlank() || count == null || count < 0) {
                throw new IllegalArgumentException("材料数量必须为非负整数");
            }
            copy.put(name, count);
        });
        materials = Map.copyOf(copy);
    }
}
