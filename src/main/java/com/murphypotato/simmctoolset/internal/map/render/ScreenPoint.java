package com.murphypotato.simmctoolset.internal.map.render;

public record ScreenPoint(double x, double y) {
    public ScreenPoint {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Screen coordinates must be finite");
        }
    }
}
