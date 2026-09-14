package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.util.List;
import java.util.Map;

/** Immutable user-named plan snapshot; it never stores daily M values. */
public record PresetPlan(String name, String recipe, List<RotationBatch> batches) {
    public PresetPlan {
        if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > 20) {
            throw new IllegalArgumentException("预设名称必须为 1-20 个 Unicode 字符");
        }
        if (recipe == null || recipe.isBlank()) throw new IllegalArgumentException("预设卷轴无效");
        batches = List.copyOf(batches == null ? List.of() : batches);
        for (RotationBatch batch : batches) {
            if (batch == null || batch.plan() == null || batch.crafts() < 0) {
                throw new IllegalArgumentException("预设批次无效");
            }
            batch.plan().materials().forEach((material, count) -> {
                if (material == null || material.isBlank() || count == null || count < 0) {
                    throw new IllegalArgumentException("预设材料数量无效");
                }
            });
        }
    }
}
