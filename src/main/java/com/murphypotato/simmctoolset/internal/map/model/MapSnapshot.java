package com.murphypotato.simmctoolset.internal.map.model;

import java.util.List;
import java.util.Optional;

public record MapSnapshot(List<MapLayer> layers) {
    public MapSnapshot {
        layers = List.copyOf(layers);
    }

    public Optional<MapLayer> layer(String id) {
        return layers.stream().filter(layer -> layer.id().equals(id)).findFirst();
    }
}
