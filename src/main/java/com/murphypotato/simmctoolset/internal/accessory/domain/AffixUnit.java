package com.murphypotato.simmctoolset.internal.accessory.domain;

import com.google.gson.annotations.SerializedName;

public enum AffixUnit {
    @SerializedName("none")
    NONE(""),
    @SerializedName("percent")
    PERCENT("%");

    private final String suffix;

    AffixUnit(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }
}
