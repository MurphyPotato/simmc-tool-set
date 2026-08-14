package com.murphypotato.simmctoolset.internal.map.cache;

import com.murphypotato.simmctoolset.internal.map.network.HttpValidators;

import java.time.Instant;
import java.util.Objects;

public record IconCacheEntry(byte[] png, HttpValidators validators, Instant lastAccess) {
    public IconCacheEntry {
        png = Objects.requireNonNull(png, "png").clone();
        validators = Objects.requireNonNullElse(validators, HttpValidators.EMPTY);
        Objects.requireNonNull(lastAccess, "lastAccess");
    }
    @Override public byte[] png() { return png.clone(); }
}
