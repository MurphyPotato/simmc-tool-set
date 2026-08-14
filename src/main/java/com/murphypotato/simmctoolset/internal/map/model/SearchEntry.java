package com.murphypotato.simmctoolset.internal.map.model;

import java.util.Objects;
import java.util.Optional;

/** Immutable searchable projection of a public marker or online player. */
public record SearchEntry(
        String displayName,
        String normalizedName,
        String layerId,
        Optional<MapPoint> center,
        String stableKey,
        boolean favoriteEligible,
        Optional<MapMarker> marker
) {
    public SearchEntry {
        displayName = requireText(displayName, "displayName");
        normalizedName = requireText(normalizedName, "normalizedName");
        layerId = requireText(layerId, "layerId");
        center = Objects.requireNonNull(center, "center");
        stableKey = requireText(stableKey, "stableKey");
        marker = Objects.requireNonNull(marker, "marker");
        if (favoriteEligible && center.isEmpty()) {
            throw new IllegalArgumentException("Favorite entries require a destination");
        }
    }

    public boolean canNavigate() {
        return center.isPresent();
    }

    public boolean canCreateWaypoint() {
        return center.isPresent();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
