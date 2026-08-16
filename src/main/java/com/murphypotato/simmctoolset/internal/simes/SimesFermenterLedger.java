package com.murphypotato.simmctoolset.internal.simes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded client-side material ledger; it never represents an unobserved server fact. */
final class SimesFermenterLedger {
    static final int MAX_ITEMS_PER_TYPE = 10;
    private final Map<String, Integer> items = new LinkedHashMap<>();

    void add(String key, int count) {
        if (key == null || key.isBlank() || count <= 0) return;
        int previous = items.getOrDefault(key, 0);
        items.put(key, Math.min(MAX_ITEMS_PER_TYPE, previous + count));
    }

    int remove(String key, int count) {
        if (key == null || count <= 0) return 0;
        int previous = items.getOrDefault(key, 0);
        int removed = Math.min(previous, count);
        if (previous == removed) items.remove(key);
        else items.put(key, previous - removed);
        return removed;
    }

    void replace(Map<String, Integer> values) {
        items.clear();
        if (values == null) return;
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && value > 0) {
                items.put(key, Math.min(MAX_ITEMS_PER_TYPE, value));
            }
        });
    }

    int count(String key) {
        return items.getOrDefault(key, 0);
    }

    Map<String, Integer> snapshot() {
        return Map.copyOf(items);
    }
}
