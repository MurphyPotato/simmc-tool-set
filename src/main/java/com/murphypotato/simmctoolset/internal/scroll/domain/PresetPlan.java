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
        if (batches.isEmpty()) throw new IllegalArgumentException("预设至少需要一个批次");
        for (RotationBatch batch : batches) {
            if (batch == null || batch.plan() == null || batch.crafts() <= 0) {
                throw new IllegalArgumentException("预设批次无效");
            }
            batch.plan().materials().forEach((material, count) -> {
                if (material == null || material.isBlank() || count == null || count < 0) {
                    throw new IllegalArgumentException("预设材料数量无效");
                }
            });
        }
    }

    /**
     * Returns the total target craft count represented by this preset.
     *
     * <p>The list order is part of the preset contract: it is the execution
     * order used by the calculator and is retained by
     * {@link com.murphypotato.simmctoolset.internal.scroll.config.PresetStore}'s
     * JSON representation.</p>
     */
    public int totalCrafts() {
        return batches.stream().mapToInt(RotationBatch::crafts).sum();
    }

    /** Returns a new preset with the supplied ordered batch combination. */
    public PresetPlan withBatches(List<RotationBatch> orderedBatches) {
        return new PresetPlan(name, recipe, orderedBatches);
    }

    /** Returns a new preset with one batch's target quantity changed. */
    public PresetPlan withCrafts(int index, int crafts) {
        if (index < 0 || index >= batches.size() || crafts <= 0) {
            throw new IllegalArgumentException("预设批次索引或数量无效");
        }
        List<RotationBatch> edited = new java.util.ArrayList<>(batches);
        RotationBatch old = edited.get(index);
        edited.set(index, new RotationBatch(old.plan(), crafts));
        return withBatches(edited);
    }

    /**
     * Returns a new preset with one batch moved while preserving every batch's
     * own material vector and target quantity.
     */
    public PresetPlan moveBatch(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= batches.size()
            || toIndex < 0 || toIndex >= batches.size()) {
            throw new IllegalArgumentException("预设批次顺序无效");
        }
        if (fromIndex == toIndex) return this;
        List<RotationBatch> reordered = new java.util.ArrayList<>(batches);
        RotationBatch moved = reordered.remove(fromIndex);
        reordered.add(toIndex, moved);
        return withBatches(reordered);
    }
}
