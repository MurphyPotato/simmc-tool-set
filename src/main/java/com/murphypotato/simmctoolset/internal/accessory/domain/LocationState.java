package com.murphypotato.simmctoolset.internal.accessory.domain;

import com.google.gson.annotations.SerializedName;

public enum LocationState {
    @SerializedName("verified")
    VERIFIED,
    @SerializedName("unverified")
    UNVERIFIED,
    @SerializedName("ambiguous")
    AMBIGUOUS
}
