package com.murphypotato.simmctoolset.internal.scroll.domain;

public record Material(String name, ElementAmounts elements, int sortRank) {
    public Material {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("材料名称不能为空");
        if (elements == null || elements.total() <= 0) throw new IllegalArgumentException("材料必须包含元素：" + name);
        if (sortRank < 0) throw new IllegalArgumentException("材料排序键无效：" + name);
    }
}
