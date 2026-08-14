package com.murphypotato.simmctoolset.internal.map.render;

public record MapPoint2D(double x, double z) {
    public MapPoint2D {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Map coordinates must be finite");
        }
    }
}
