package com.murphypotato.simmctoolset.internal.map.render;

public interface DecodedTile extends AutoCloseable {
    int width();
    int height();
    @Override void close();
}
