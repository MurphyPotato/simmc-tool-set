package com.murphypotato.simmctoolset.internal.map.render;

@FunctionalInterface
public interface TileDecoder {
    DecodedTile decode(byte[] png, int expectedTileSize) throws TileDecodeException;
}
