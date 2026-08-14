package com.murphypotato.simmctoolset.internal.map.model;

import java.util.List;
import java.util.Objects;

public record PolylineMarker(
        List<MapPoint> points,
        MarkerStyle style,
        String tooltip,
        String popup
) implements MapMarker {
    public PolylineMarker {
        points = List.copyOf(points);
        Objects.requireNonNull(style, "style");
        tooltip = tooltip == null ? "" : tooltip;
        popup = popup == null ? "" : popup;
    }

    @Override
    public String type() {
        return "polyline";
    }
}
