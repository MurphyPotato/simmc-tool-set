package com.murphypotato.simmctoolset.internal.accessory.domain;

public enum PlanVariant {
    EXPECTED("最高期望"),
    STABLE("稳定输出");

    private final String label;

    PlanVariant(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
