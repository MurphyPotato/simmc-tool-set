package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record LoadoutResult(
    Map<AccessorySlot, AccessoryRecord> accessories,
    DamageBreakdown breakdown,
    int realItemCount
) {
    public LoadoutResult {
        EnumMap<AccessorySlot, AccessoryRecord> copy = new EnumMap<>(AccessorySlot.class);
        copy.putAll(accessories);
        accessories = Collections.unmodifiableMap(copy);
    }
}
