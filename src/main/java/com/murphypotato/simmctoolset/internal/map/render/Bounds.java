package com.murphypotato.simmctoolset.internal.map.render;

public record Bounds(double minX, double minZ, double maxX, double maxZ) {
    public Bounds {
        if (!Double.isFinite(minX) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxZ)
                || minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Invalid bounds");
        }
    }

    public boolean intersects(Bounds other) {
        return maxX >= other.minX && minX <= other.maxX
                && maxZ >= other.minZ && minZ <= other.maxZ;
    }

    public boolean contains(double x, double z) {
        return Double.isFinite(x) && Double.isFinite(z)
                && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public double centerX() {
        return minX * 0.5 + maxX * 0.5;
    }

    public double centerZ() {
        return minZ * 0.5 + maxZ * 0.5;
    }
}
