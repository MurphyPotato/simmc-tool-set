package com.murphypotato.simmctoolset.internal.map.render;

import java.util.Optional;

/** Pure conversion from continuous render bounds to DrawContext arguments. */
public final class TextureRenderPlan {
    private TextureRenderPlan() {
    }

    public static Optional<Quad> fullTile(double left, double top, double right, double bottom,
                                          double u1, double u2, double v1, double v2) {
        double minimumX = Math.min(left, right);
        double maximumX = Math.max(left, right);
        double minimumY = Math.min(top, bottom);
        double maximumY = Math.max(top, bottom);
        if (!finite(minimumX, minimumY, maximumX, maximumY, u1, u2, v1, v2)) return Optional.empty();

        int x1 = floorToInt(minimumX);
        int y1 = floorToInt(minimumY);
        int x2 = ceilToInt(maximumX);
        int y2 = ceilToInt(maximumY);
        return quad(x1, y1, x2, y2, u1, u2, v1, v2);
    }

    public static Optional<Quad> clippedTile(double originalLeft, double originalTop,
                                             double originalRight, double originalBottom,
                                             double clippedLeft, double clippedTop,
                                             double clippedRight, double clippedBottom,
                                             double u1, double u2, double v1, double v2) {
        if (!finite(originalLeft, originalTop, originalRight, originalBottom,
                clippedLeft, clippedTop, clippedRight, clippedBottom, u1, u2, v1, v2)
                || originalRight <= originalLeft || originalBottom <= originalTop
                || clippedRight <= clippedLeft || clippedBottom <= clippedTop) {
            return Optional.empty();
        }

        int x1 = floorToInt(clippedLeft);
        int y1 = floorToInt(clippedTop);
        int x2 = ceilToInt(clippedRight);
        int y2 = ceilToInt(clippedBottom);
        double clippedU1 = interpolate(u1, u2, originalLeft, originalRight, clippedLeft);
        double clippedU2 = interpolate(u1, u2, originalLeft, originalRight, clippedRight);
        double clippedV1 = interpolate(v1, v2, originalTop, originalBottom, clippedTop);
        double clippedV2 = interpolate(v1, v2, originalTop, originalBottom, clippedBottom);
        return quad(x1, y1, x2, y2, clippedU1, clippedU2, clippedV1, clippedV2);
    }

    public static Optional<IconRaster> icon(double left, double top, double right, double bottom) {
        if (!finite(left, top, right, bottom) || right <= left || bottom <= top) return Optional.empty();

        int x = floorToInt(left);
        int y = floorToInt(top);
        int width = ceilToInt(right) - x;
        int height = ceilToInt(bottom) - y;
        if (width <= 0 || height <= 0) return Optional.empty();
        return Optional.of(new IconRaster(x, y, width, height,
                0, 0, width, height, width, height));
    }

    private static Optional<Quad> quad(int x1, int y1, int x2, int y2,
                                       double u1, double u2, double v1, double v2) {
        if (x2 <= x1 || y2 <= y1) return Optional.empty();
        return Optional.of(new Quad(x1, y1, x2, y2,
                (float) u1, (float) u2, (float) v1, (float) v2));
    }

    private static int floorToInt(double value) {
        return (int) Math.floor(value);
    }

    private static int ceilToInt(double value) {
        return (int) Math.ceil(value);
    }

    private static double interpolate(double startValue, double endValue,
                                      double startPosition, double endPosition, double position) {
        return startValue + (endValue - startValue)
                * ((position - startPosition) / (endPosition - startPosition));
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) return false;
        }
        return true;
    }

    public record Quad(int x1, int y1, int x2, int y2,
                       float u1, float u2, float v1, float v2) {
    }

    public record IconRaster(int x, int y, int width, int height,
                             int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                             int textureWidth, int textureHeight) {
    }
}
