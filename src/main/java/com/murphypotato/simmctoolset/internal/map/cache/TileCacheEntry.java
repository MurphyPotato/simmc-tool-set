package com.murphypotato.simmctoolset.internal.map.cache;

import com.murphypotato.simmctoolset.internal.map.render.TileValidators;

import java.util.Objects;

public record TileCacheEntry(byte[] png, TileValidators validators, long lastCheckedMillis) {
    public TileCacheEntry {
        png = Objects.requireNonNull(png, "png").clone();
        validators = Objects.requireNonNullElse(validators, TileValidators.EMPTY);
        if (lastCheckedMillis < 0) throw new IllegalArgumentException("lastCheckedMillis must be non-negative");
    }

    @Override public byte[] png() { return png.clone(); }
}
