package com.murphypotato.simmctoolset.internal.map.render;

public record WorldViewport(double left, double top, double right, double bottom,
                            double centerX, double centerZ) {
    public WorldViewport {
        if (!Double.isFinite(left) || !Double.isFinite(top) || !Double.isFinite(right)
                || !Double.isFinite(bottom) || !Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("viewport values must be finite");
        }
        if (right < left || bottom < top) throw new IllegalArgumentException("viewport bounds are inverted");
        if (centerX < left || centerX > right || centerZ < top || centerZ > bottom) {
            throw new IllegalArgumentException("viewport center must be inside its bounds");
        }
    }
}
