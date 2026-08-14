package com.murphypotato.simmctoolset.internal.map.model;

import java.util.Objects;
import java.util.Optional;

public record IconMarker(
        MapPoint point,
        MapPoint size,
        MapPoint anchor,
        Optional<MapPoint> tooltipAnchor,
        String icon,
        String tooltip,
        String popup
) implements MapMarker {
    public IconMarker {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(anchor, "anchor");
        tooltipAnchor = Objects.requireNonNull(tooltipAnchor, "tooltipAnchor");
        Objects.requireNonNull(icon, "icon");
        tooltip = tooltip == null ? "" : tooltip;
        popup = popup == null ? "" : popup;
    }

    @Override
    public String type() {
        return "icon";
    }
}
