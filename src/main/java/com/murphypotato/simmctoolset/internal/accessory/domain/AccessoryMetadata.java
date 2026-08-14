package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.Objects;

public record AccessoryMetadata(
    IconSnapshot icon,
    ContainerLocation location,
    LocationState locationState,
    String lastSeenAt
) {
    public AccessoryMetadata {
        locationState = Objects.requireNonNullElse(locationState, LocationState.UNVERIFIED);
        if (location == null) locationState = LocationState.UNVERIFIED;
        lastSeenAt = Objects.requireNonNullElse(lastSeenAt, "").strip();
    }

    public static AccessoryMetadata empty() {
        return new AccessoryMetadata(null, null, LocationState.UNVERIFIED, "");
    }

    public AccessoryMetadata withIcon(IconSnapshot nextIcon) {
        return new AccessoryMetadata(nextIcon, location, locationState, lastSeenAt);
    }

    public AccessoryMetadata withLocation(ContainerLocation nextLocation, LocationState nextState, String seenAt) {
        return new AccessoryMetadata(icon, nextLocation, nextState, seenAt);
    }

    public AccessoryMetadata withoutBlockLocation() {
        if (location == null || !location.locatedBlock()) return this;
        return new AccessoryMetadata(icon, null, LocationState.UNVERIFIED, lastSeenAt);
    }
}
