package com.murphypotato.simmctoolset.internal.map.render;

import com.murphypotato.simmctoolset.internal.map.network.IconBytes;

/** Client-thread GPU adapter for registered squaremap icons. */
public interface IconTextureBackend {
    Object upload(String registeredKey, IconBytes icon) throws Exception;
    void destroy(Object handle);
}
