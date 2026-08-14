package com.murphypotato.simmctoolset.internal.map.render;

@FunctionalInterface
public interface TileSource {
    TileFetchResult fetch(TileKey key) throws Exception;

    default TileFetchResult revalidate(TileKey key, TileValidators validators) throws Exception {
        return fetch(key);
    }
}
