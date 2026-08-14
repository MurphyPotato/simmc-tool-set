package com.murphypotato.simmctoolset.internal.map.render;

public record TileRendererLimits(int maxConcurrentLoads, int maxCompletedUploads,
                                 int maxGpuTextures, long failureCooldownMillis) {
    public TileRendererLimits {
        if (maxConcurrentLoads < 1 || maxCompletedUploads < 1 || maxGpuTextures < 1
                || failureCooldownMillis < 0) throw new IllegalArgumentException("invalid tile renderer limits");
    }
}
