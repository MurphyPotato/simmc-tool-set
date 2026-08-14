package com.murphypotato.simmctoolset.internal.map.network;

/** Boundary for a later bounded client-thread GPU upload queue. This interface performs no upload itself. */
@FunctionalInterface
public interface IconUploadSink {
    boolean enqueue(String registeredKey, IconBytes icon);
}
