package com.murphypotato.simmctoolset.internal.map.render;

/** Optional pixel-capable decoded tile contract for production GPU adapters. */
public interface PixelDecodedTile extends DecodedTile {
    int argb(int x, int y);
}
