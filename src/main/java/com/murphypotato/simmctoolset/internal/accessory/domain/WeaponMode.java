package com.murphypotato.simmctoolset.internal.accessory.domain;

import com.google.gson.annotations.SerializedName;

public enum WeaponMode {
    @SerializedName("bow")
    BOW("弓套"),
    @SerializedName("sword")
    SWORD("剑套");

    private final String label;

    WeaponMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
