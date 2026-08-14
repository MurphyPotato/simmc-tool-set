package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.util.Arrays;

public enum Element {
    METAL("金"),
    WOOD("木"),
    WATER("水"),
    FIRE("火"),
    EARTH("土"),
    LIGHTNING("雷"),
    LIGHT("光"),
    DARK("暗");

    private final String label;

    Element(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Element fromLabel(String label) {
        return Arrays.stream(values())
            .filter(element -> element.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未知元素：" + label));
    }
}
