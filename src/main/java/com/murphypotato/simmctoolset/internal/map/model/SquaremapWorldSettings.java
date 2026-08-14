package com.murphypotato.simmctoolset.internal.map.model;

import java.util.Objects;

public record SquaremapWorldSettings(
        String worldKey,
        int tileSize,
        int minZoom,
        int maxZoom,
        int extraZoom,
        int defaultZoom,
        double spawnX,
        double spawnZ,
        int markerUpdateIntervalSeconds,
        int tilesUpdateIntervalSeconds,
        boolean playerTrackerEnabled,
        int playerUpdateIntervalSeconds
) {
    public SquaremapWorldSettings {
        Objects.requireNonNull(worldKey, "worldKey");
        if (tileSize <= 0) {
            throw new IllegalArgumentException("tileSize must be positive");
        }
        if (minZoom < 0 || maxZoom < minZoom || maxZoom > 30 || extraZoom < 0 || extraZoom > 30 - maxZoom
                || defaultZoom < minZoom || defaultZoom > maxZoom) {
            throw new IllegalArgumentException("zoom values must satisfy 0 <= minZoom <= defaultZoom <= maxZoom and safe extraZoom");
        }
        if (!Double.isFinite(spawnX) || !Double.isFinite(spawnZ)) {
            throw new IllegalArgumentException("spawn coordinates must be finite");
        }
        if (markerUpdateIntervalSeconds < 0
                || tilesUpdateIntervalSeconds < 0
                || playerUpdateIntervalSeconds < 0) {
            throw new IllegalArgumentException("update intervals must be non-negative");
        }
    }

    public SquaremapWorldSettings(String worldKey, int tileSize, int maxZoom, int extraZoom,
                                  int defaultZoom, double spawnX, double spawnZ,
                                  int markerUpdateIntervalSeconds, int tilesUpdateIntervalSeconds,
                                  boolean playerTrackerEnabled, int playerUpdateIntervalSeconds) {
        this(worldKey, tileSize, 0, maxZoom, extraZoom, defaultZoom, spawnX, spawnZ,
                markerUpdateIntervalSeconds, tilesUpdateIntervalSeconds,
                playerTrackerEnabled, playerUpdateIntervalSeconds);
    }

    /** Cache/renderer namespace for server values that change tile interpretation. */
    public String signature() {
        return tileSize + "-" + minZoom + "-" + maxZoom;
    }
}
