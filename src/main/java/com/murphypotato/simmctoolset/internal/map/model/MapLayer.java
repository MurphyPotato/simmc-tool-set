package com.murphypotato.simmctoolset.internal.map.model;

import java.util.List;
import java.util.Objects;

public record MapLayer(
        String id,
        String name,
        int order,
        int zIndex,
        boolean defaultHidden,
        boolean showControls,
        long timestamp,
        List<MapMarker> markers
) {
    public MapLayer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        markers = List.copyOf(markers);
    }
}
