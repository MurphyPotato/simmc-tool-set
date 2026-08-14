package com.murphypotato.simmctoolset.internal.scroll.domain;

public record ScrollRecipe(String name, String mainMaterial, ElementAmounts required) {
    public ScrollRecipe {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("卷轴名称不能为空");
        if (mainMaterial == null || mainMaterial.isBlank()) throw new IllegalArgumentException("主材料不能为空：" + name);
        if (required == null || required.total() <= 0) throw new IllegalArgumentException("卷轴必须包含目标元素：" + name);
    }
}
