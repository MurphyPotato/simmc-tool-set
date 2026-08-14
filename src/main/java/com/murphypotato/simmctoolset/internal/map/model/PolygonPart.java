package com.murphypotato.simmctoolset.internal.map.model;

import java.util.List;

public record PolygonPart(List<List<MapPoint>> rings) {
    public PolygonPart {
        rings = rings.stream().map(List::copyOf).toList();
    }
}
