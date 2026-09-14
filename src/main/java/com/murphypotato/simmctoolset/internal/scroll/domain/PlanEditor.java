package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.util.ArrayList;
import java.util.List;

/** Pure helpers for editing an already calculated, frozen batch plan. */
public final class PlanEditor {
    private PlanEditor() {}

    public static List<RotationBatch> resize(List<RotationBatch> source, int requestedCount) {
        if (source == null || requestedCount < 1) throw new IllegalArgumentException("方案数量无效");
        List<RotationBatch> current = source.stream()
            .filter(batch -> batch != null && batch.crafts() > 0)
            .map(batch -> new RotationBatch(batch.plan(), batch.crafts()))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (current.isEmpty()) throw new IllegalArgumentException("没有可编辑方案");
        if (requestedCount > current.size()) return List.copyOf(current);
        if (requestedCount == current.size()) return List.copyOf(current);
        int removed = current.subList(requestedCount, current.size()).stream()
            .mapToInt(RotationBatch::crafts).sum();
        current.subList(requestedCount, current.size()).clear();
        int each = removed / current.size();
        int remainder = removed % current.size();
        for (int i = 0; i < current.size(); i++) {
            RotationBatch batch = current.get(i);
            current.set(i, new RotationBatch(batch.plan(), Math.addExact(batch.crafts(), each + (i < remainder ? 1 : 0))));
        }
        return List.copyOf(current);
    }

    public static List<RotationBatch> withCrafts(List<RotationBatch> source, int index, int crafts) {
        if (source == null || index < 0 || index >= source.size() || crafts < 1) {
            throw new IllegalArgumentException("方案数量无效");
        }
        List<RotationBatch> result = new ArrayList<>(source);
        RotationBatch old = result.get(index);
        result.set(index, new RotationBatch(old.plan(), crafts));
        return List.copyOf(result);
    }
}
