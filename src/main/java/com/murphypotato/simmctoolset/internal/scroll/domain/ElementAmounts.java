package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ElementAmounts {
    private final int[] values;

    public ElementAmounts(int[] values) {
        if (values.length != Element.values().length) throw new IllegalArgumentException("元素数量不完整");
        if (Arrays.stream(values).anyMatch(value -> value < 0)) throw new IllegalArgumentException("元素数量不能为负数");
        this.values = values.clone();
    }

    public static ElementAmounts zero() {
        return new ElementAmounts(new int[Element.values().length]);
    }

    public int get(Element element) {
        return values[element.ordinal()];
    }

    public int total() {
        return Arrays.stream(values).sum();
    }

    public ElementAmounts plus(ElementAmounts other) {
        int[] result = values.clone();
        for (Element element : Element.values()) result[element.ordinal()] += other.get(element);
        return new ElementAmounts(result);
    }

    public int[] toArray() {
        return values.clone();
    }

    public Map<String, Integer> positiveByLabel() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Element element : Element.values()) {
            int value = get(element);
            if (value > 0) result.put(element.label(), value);
        }
        return result;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ElementAmounts amounts && Arrays.equals(values, amounts.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return positiveByLabel().toString();
    }
}
