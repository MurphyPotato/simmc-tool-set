package com.murphypotato.simmctoolset.internal.map.model;

import java.util.Optional;

public sealed interface MapMarker permits PolygonMarker, PolylineMarker, IconMarker {
    String type();

    String tooltip();

    String popup();

    default Optional<LandDetails> landDetails() {
        return Optional.empty();
    }
}
