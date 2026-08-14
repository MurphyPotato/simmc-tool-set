package com.murphypotato.simmctoolset.internal.map.render;

/**
 * Platform adapter. Both methods are invoked only by explicit client-thread drain methods.
 * Upload must copy or take everything it needs before returning; the decoded tile is closed immediately after.
 */
public interface TextureBackend {
    Object upload(TileKey key, DecodedTile image) throws Exception;
    void destroy(Object handle);
}
