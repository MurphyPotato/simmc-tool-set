package com.murphypotato.simmctoolset.internal.map.render;

import java.util.Objects;

public record GpuTileRegion(TileKey key, Object handle, double u0, double v0, double u1, double v1) {
    public GpuTileRegion {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(handle, "handle");
        if (!(u0 >= 0 && v0 >= 0 && u1 <= 1 && v1 <= 1 && u0 < u1 && v0 < v1)) {
            throw new IllegalArgumentException("invalid texture region");
        }
    }
    public static GpuTileRegion full(TileKey key, Object handle) {
        return new GpuTileRegion(key, handle, 0, 0, 1, 1);
    }
}
