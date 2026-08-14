package com.murphypotato.simmctoolset.internal.map.render;

import java.nio.charset.StandardCharsets;

public record TileValidators(String etag, String lastModified) {
    public static final TileValidators EMPTY = new TileValidators(null, null);
    public static final int MAX_VALUE_BYTES = 1024;

    public TileValidators {
        requireBounded(etag, "ETag");
        requireBounded(lastModified, "Last-Modified");
    }

    private static void requireBounded(String value, String name) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > MAX_VALUE_BYTES) {
            throw new IllegalArgumentException(name + " exceeds " + MAX_VALUE_BYTES + " UTF-8 bytes");
        }
    }
}
