package com.murphypotato.simmctoolset.internal.map.network;

import java.util.Objects;

public record IconBytes(byte[] png, IconSource source) {
    public IconBytes {
        png = Objects.requireNonNull(png, "png").clone();
        Objects.requireNonNull(source, "source");
    }
    @Override public byte[] png() { return png.clone(); }
}
