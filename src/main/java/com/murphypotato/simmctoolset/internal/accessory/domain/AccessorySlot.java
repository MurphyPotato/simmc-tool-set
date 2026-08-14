package com.murphypotato.simmctoolset.internal.accessory.domain;

import com.google.gson.annotations.SerializedName;

import java.util.Arrays;
import java.util.Optional;

public enum AccessorySlot {
    @SerializedName("mainRing")
    MAIN_RING("mainRing", "主戒指"),
    @SerializedName("subRing")
    SUB_RING("subRing", "副戒指"),
    @SerializedName("mainAmulet")
    MAIN_AMULET("mainAmulet", "主护符"),
    @SerializedName("subAmulet")
    SUB_AMULET("subAmulet", "副护符");

    private final String id;
    private final String label;

    AccessorySlot(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static Optional<AccessorySlot> fromLabel(String label) {
        return Arrays.stream(values()).filter(slot -> slot.label.equals(label)).findFirst();
    }
}
