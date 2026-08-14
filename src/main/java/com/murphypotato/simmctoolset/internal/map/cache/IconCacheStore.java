package com.murphypotato.simmctoolset.internal.map.cache;

import java.util.Optional;

public interface IconCacheStore {
    Optional<IconCacheEntry> load(String key);
    void save(String key, IconCacheEntry entry);
}
