package com.murphypotato.simmctoolset.internal.map.render;

import com.murphypotato.simmctoolset.internal.map.network.IconUploadSink;
import com.murphypotato.simmctoolset.internal.map.network.RegisteredIconLoader;

import java.util.Optional;

/** Bounded async-load to client-thread-upload icon boundary. */
public interface WorldMapIconRegistry extends IconUploadSink, AutoCloseable {
    void request(String registeredKey, RegisteredIconLoader loader);
    int drainUploads(int maxUploads);
    Optional<Object> resolve(String registeredKey);
    void invalidate();
    @Override void close();
}
