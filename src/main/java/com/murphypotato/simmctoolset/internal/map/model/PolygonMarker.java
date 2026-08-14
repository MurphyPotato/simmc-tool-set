package com.murphypotato.simmctoolset.internal.map.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PolygonMarker(
        List<PolygonPart> parts,
        MarkerStyle style,
        String tooltip,
        String popup,
        Optional<LandDetails> landDetails
) implements MapMarker {
    public PolygonMarker {
        parts = List.copyOf(parts);
        Objects.requireNonNull(style, "style");
        tooltip = tooltip == null ? "" : tooltip;
        popup = popup == null ? "" : popup;
        landDetails = Objects.requireNonNull(landDetails, "landDetails");
    }

    public PolygonMarker(List<PolygonPart> parts, MarkerStyle style, String tooltip, String popup) {
        this(parts, style, tooltip, popup, Optional.empty());
    }

    @Override
    public String type() {
        return "polygon";
    }
}
