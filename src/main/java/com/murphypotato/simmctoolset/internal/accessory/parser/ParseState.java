package com.murphypotato.simmctoolset.internal.accessory.parser;

import com.google.gson.annotations.SerializedName;

public enum ParseState {
    @SerializedName("accepted")
    ACCEPTED,
    @SerializedName("needs-review")
    NEEDS_REVIEW
}
