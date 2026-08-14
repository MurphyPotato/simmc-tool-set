package com.murphypotato.simmctoolset.internal.map.model;

import java.util.Objects;
import java.util.Optional;

public record OnlinePlayerEntry(
        String name,
        String uuid,
        String worldKey,
        Optional<MapPoint> position
) {
    public OnlinePlayerEntry {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(worldKey, "worldKey");
        position = Objects.requireNonNull(position, "position");
    }
}
