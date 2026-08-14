package com.murphypotato.simmctoolset.internal.map.render;

public final class MapProjection {
    private MapProjection() {
    }

    public static long floorToTile(double worldCoordinate, int tileSize) {
        requireFinite(worldCoordinate, "worldCoordinate");
        requireTileSize(tileSize);
        double tile = Math.floor(worldCoordinate / tileSize);
        if (tile < -0x1.0p63 || tile >= 0x1.0p63) {
            throw new IllegalArgumentException("Tile coordinate is outside long range");
        }
        return (long) tile;
    }

    public static Bounds tileBounds(long tileX, long tileZ, int tileSize) {
        requireTileSize(tileSize);
        long minX;
        long minZ;
        long maxX;
        long maxZ;
        try {
            minX = Math.multiplyExact(tileX, (long) tileSize);
            minZ = Math.multiplyExact(tileZ, (long) tileSize);
            maxX = Math.addExact(minX, tileSize);
            maxZ = Math.addExact(minZ, tileSize);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Tile bounds overflow", exception);
        }
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    public static ScreenPoint worldToScreen(
            double worldX, double worldZ, double centerX, double centerZ,
            double scale, double viewportWidth, double viewportHeight
    ) {
        validateProjection(worldX, worldZ, centerX, centerZ, scale, viewportWidth, viewportHeight);
        return new ScreenPoint(
                (worldX - centerX) * scale + viewportWidth / 2.0,
                (worldZ - centerZ) * scale + viewportHeight / 2.0
        );
    }

    public static MapPoint2D screenToWorld(
            double screenX, double screenY, double centerX, double centerZ,
            double scale, double viewportWidth, double viewportHeight
    ) {
        validateProjection(screenX, screenY, centerX, centerZ, scale, viewportWidth, viewportHeight);
        return new MapPoint2D(
                (screenX - viewportWidth / 2.0) / scale + centerX,
                (screenY - viewportHeight / 2.0) / scale + centerZ
        );
    }

    private static void validateProjection(
            double x, double z, double centerX, double centerZ,
            double scale, double viewportWidth, double viewportHeight
    ) {
        requireFinite(x, "x");
        requireFinite(z, "z");
        requireFinite(centerX, "centerX");
        requireFinite(centerZ, "centerZ");
        if (!Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Scale must be positive and finite");
        }
        if (!Double.isFinite(viewportWidth) || viewportWidth <= 0
                || !Double.isFinite(viewportHeight) || viewportHeight <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive and finite");
        }
    }

    private static void requireTileSize(int tileSize) {
        if (tileSize <= 0) {
            throw new IllegalArgumentException("Tile size must be positive");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
