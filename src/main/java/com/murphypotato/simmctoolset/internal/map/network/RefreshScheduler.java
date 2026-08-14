package com.murphypotato.simmctoolset.internal.map.network;

import java.time.Duration;

@FunctionalInterface
public interface RefreshScheduler {
    Cancellable schedule(Runnable action, Duration delay);
}
