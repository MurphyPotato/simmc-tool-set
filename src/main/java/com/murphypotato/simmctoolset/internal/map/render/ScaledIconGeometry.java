package com.murphypotato.simmctoolset.internal.map.render;

import com.murphypotato.simmctoolset.internal.map.model.IconMarker;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Pure screen-space geometry for fixed and zoom-scaled map icons. */
public final class ScaledIconGeometry {
    private static final double MAXIMUM_FACTOR = 2.0;
    private static final double MINIMUM_RENDERED_SIDE = 2.0;
    private static final double MAXIMUM_RASTER_EXTENT = Integer.MAX_VALUE - 1.0;

    public record Plan(double left, double top, double right, double bottom,
                       double factor, boolean hasPositiveArea) { }

    private ScaledIconGeometry() { }

    public static Optional<Plan> plan(String layerId, IconMarker icon,
                                      ScreenPoint projectedPoint, double viewScale) {
        Objects.requireNonNull(layerId, "layerId");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(projectedPoint, "projectedPoint");
        if (!Double.isFinite(viewScale) || viewScale <= 0) return Optional.empty();
        double width = icon.size().x();
        double height = icon.size().z();
        boolean hasPositiveArea = Math.abs(width) > 0 && Math.abs(height) > 0;
        double factor = 1.0;
        if (maximumFactor(layerId) > 1 && hasPositiveArea) {
            double smallerSide = Math.min(Math.abs(width), Math.abs(height));
            double minimumFactor = Math.min(MAXIMUM_FACTOR, MINIMUM_RENDERED_SIDE / smallerSide);
            factor = Math.max(minimumFactor, Math.min(MAXIMUM_FACTOR, viewScale));
        }
        RelativeGeometry relative = relativeGeometry(icon, factor).orElse(null);
        if (relative == null) return Optional.empty();
        double x1 = projectedPoint.x() - relative.anchorX();
        double y1 = projectedPoint.y() - relative.anchorY();
        double x2 = x1 + relative.width();
        double y2 = y1 + relative.height();
        if (!safeRasterCoordinate(x1) || !safeRasterCoordinate(y1)
                || !safeRasterCoordinate(x2) || !safeRasterCoordinate(y2)) {
            return Optional.empty();
        }
        return Optional.of(new Plan(Math.min(x1, x2), Math.min(y1, y2),
                Math.max(x1, x2), Math.max(y1, y2), factor, hasPositiveArea));
    }

    public static OptionalDouble maximumReach(String layerId, IconMarker icon) {
        Objects.requireNonNull(layerId, "layerId");
        Objects.requireNonNull(icon, "icon");
        boolean hasPositiveArea = Math.abs(icon.size().x()) > 0 && Math.abs(icon.size().z()) > 0;
        double factor = hasPositiveArea ? maximumFactor(layerId) : 1.0;
        RelativeGeometry relative = relativeGeometry(icon, factor).orElse(null);
        if (relative == null) return OptionalDouble.empty();
        return OptionalDouble.of(Math.max(
                Math.max(Math.abs(relative.anchorX()), Math.abs(relative.farX())),
                Math.max(Math.abs(relative.anchorY()), Math.abs(relative.farY()))));
    }

    public static double maximumFactor(String layerId) {
        return "transport_gateways".equals(layerId) || "religion".equals(layerId)
                ? MAXIMUM_FACTOR : 1.0;
    }

    private static Optional<RelativeGeometry> relativeGeometry(IconMarker icon, double factor) {
        if (!Double.isFinite(factor) || factor <= 0) return Optional.empty();
        double width = icon.size().x() * factor;
        double height = icon.size().z() * factor;
        double anchorX = icon.anchor().x() * factor;
        double anchorY = icon.anchor().z() * factor;
        double farX = width - anchorX;
        double farY = height - anchorY;
        if (!safeRasterExtent(width) || !safeRasterExtent(height)
                || !safeRasterExtent(anchorX) || !safeRasterExtent(anchorY)
                || !safeRasterExtent(farX) || !safeRasterExtent(farY)) {
            return Optional.empty();
        }
        return Optional.of(new RelativeGeometry(width, height, anchorX, anchorY, farX, farY));
    }

    private static boolean safeRasterExtent(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAXIMUM_RASTER_EXTENT;
    }

    private static boolean safeRasterCoordinate(double value) {
        return Double.isFinite(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    private record RelativeGeometry(double width, double height, double anchorX, double anchorY,
                                    double farX, double farY) { }
}
