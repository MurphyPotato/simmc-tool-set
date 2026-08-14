package com.murphypotato.simmctoolset.internal.map.model;

public record MapPoint(double x, double z) {
    public MapPoint {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Map coordinates must be finite");
        }
    }
}
