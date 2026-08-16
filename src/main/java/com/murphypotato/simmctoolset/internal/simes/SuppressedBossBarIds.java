package com.murphypotato.simmctoolset.internal.simes;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Tracks boss-bar IDs that were hidden before vanilla created their bars. */
final class SuppressedBossBarIds {
    private final Set<UUID> ids = new HashSet<>();

    synchronized void suppress(UUID id) {
        if (id != null) ids.add(id);
    }

    synchronized boolean contains(UUID id) {
        return id != null && ids.contains(id);
    }

    synchronized boolean release(UUID id) {
        return id != null && ids.remove(id);
    }

    synchronized void clear() {
        ids.clear();
    }
}
