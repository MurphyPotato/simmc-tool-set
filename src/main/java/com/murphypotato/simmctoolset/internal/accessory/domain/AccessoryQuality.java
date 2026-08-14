package com.murphypotato.simmctoolset.internal.accessory.domain;

import com.google.gson.annotations.SerializedName;

import java.util.Arrays;
import java.util.Optional;

public enum AccessoryQuality {
    @SerializedName("dim")
    DIM("dim", "黯淡", 3),
    @SerializedName("fine")
    FINE("fine", "精工", 5),
    @SerializedName("divine")
    DIVINE("divine", "神铸", 8);

    private final String id;
    private final String label;
    private final int maxLevel;

    AccessoryQuality(String id, String label, int maxLevel) {
        this.id = id;
        this.label = label;
        this.maxLevel = maxLevel;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public int totalAffixSlots(int level) {
        int clamped = Math.max(0, Math.min(level, maxLevel));
        if (this == DIM) return clamped >= 3 ? 2 : 1;
        if (this == FINE) return 1 + (clamped >= 3 ? 1 : 0) + (clamped >= 5 ? 1 : 0);
        return 1 + (clamped >= 3 ? 1 : 0) + (clamped >= 5 ? 1 : 0) + (clamped >= 8 ? 1 : 0);
    }

    public static Optional<AccessoryQuality> fromTitle(String title) {
        return Arrays.stream(values()).filter(quality -> title.contains(quality.label)).findFirst();
    }
}
