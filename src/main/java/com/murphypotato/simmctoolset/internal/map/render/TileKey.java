package com.murphypotato.simmctoolset.internal.map.render;

import java.util.Objects;

public record TileKey(String profileId, String worldKey, String settingsSignature, int zoom, int x, int z) {
    private static final int MAX_FIELD_LENGTH = 1024;

    public TileKey {
        profileId = checked(profileId, "profileId");
        worldKey = checked(worldKey, "worldKey");
        settingsSignature = checked(settingsSignature, "settingsSignature");
        if (zoom < 0) throw new IllegalArgumentException("zoom must be non-negative");
    }

    public String canonical() {
        return profileId.length() + ":" + profileId
                + worldKey.length() + ":" + worldKey
                + settingsSignature.length() + ":" + settingsSignature
                + ':' + zoom + ':' + x + ':' + z;
    }

    private static String checked(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.length() > MAX_FIELD_LENGTH) throw new IllegalArgumentException(name + " is too long");
        return value;
    }
}
