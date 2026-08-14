package com.murphypotato.simmctoolset.internal.map.network;

import java.util.concurrent.CompletableFuture;

/** Asynchronous registered-icon boundary used by a client-thread upload registry. */
public interface RegisteredIconLoader extends AutoCloseable {
    CompletableFuture<Boolean> loadAndEnqueue(String registeredKey, IconUploadSink sink);
    @Override void close();
}
