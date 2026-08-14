package com.murphypotato.simmctoolset.internal.map.render;

import com.murphypotato.simmctoolset.internal.map.model.SquaremapWorldSettings;

import java.util.Objects;

public final class TileZoomSelector {
    private static final double HYSTERESIS = 1.08;

    private TileZoomSelector() {}

    public static int initial(SquaremapWorldSettings settings, double worldScale) {
        Objects.requireNonNull(settings, "settings");
        validateScale(worldScale);

        int zoom = settings.minZoom();
        while (zoom < settings.maxZoom()
                && worldScale > baseBoundary(settings, zoom + 1)) {
            zoom++;
        }
        return zoom;
    }

    public static int stabilize(SquaremapWorldSettings settings, double worldScale, int previousZoom) {
        Objects.requireNonNull(settings, "settings");
        validateScale(worldScale);
        if (previousZoom < settings.minZoom() || previousZoom > settings.maxZoom()) {
            throw new IllegalArgumentException("previousZoom is outside squaremap bounds");
        }

        int zoom = previousZoom;
        while (zoom < settings.maxZoom()
                && worldScale > baseBoundary(settings, zoom + 1) * HYSTERESIS) {
            zoom++;
        }
        while (zoom > settings.minZoom()
                && worldScale < baseBoundary(settings, zoom) / HYSTERESIS) {
            zoom--;
        }
        return zoom;
    }

    private static double baseBoundary(SquaremapWorldSettings settings, int upperZoom) {
        return Math.scalb(1.0, upperZoom - settings.maxZoom() - 1);
    }

    private static void validateScale(double worldScale) {
        if (!Double.isFinite(worldScale) || worldScale <= 0) {
            throw new IllegalArgumentException("worldScale must be positive and finite");
        }
    }
}
