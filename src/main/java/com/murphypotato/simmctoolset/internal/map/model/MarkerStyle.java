package com.murphypotato.simmctoolset.internal.map.model;

public record MarkerStyle(
        int strokeColor,
        int fillColor,
        double weight,
        double opacity,
        double fillOpacity
) {
    public MarkerStyle {
        strokeColor &= 0xFFFFFF;
        fillColor &= 0xFFFFFF;
        if (!Double.isFinite(weight) || weight < 0
                || !Double.isFinite(opacity) || opacity < 0 || opacity > 1
                || !Double.isFinite(fillOpacity) || fillOpacity < 0 || fillOpacity > 1) {
            throw new IllegalArgumentException("Invalid marker style");
        }
    }
}
