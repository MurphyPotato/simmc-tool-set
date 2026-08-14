package com.murphypotato.simmctoolset.internal.map.render;

/** Immutable limits for viewport-scoped stale-while-revalidate work. */
public record TileRefreshPolicy(long ttlMillis, long idleDebounceMillis,
                                int maxAcceptedPerFrame, int maxConcurrentRevalidations,
                                long backoffBaseMillis, long backoffCapMillis) {
    public static final long DEFAULT_TTL_MILLIS = 300_000;

    public TileRefreshPolicy {
        if (ttlMillis < 1 || idleDebounceMillis < 0 || maxAcceptedPerFrame < 1
                || maxConcurrentRevalidations < 1 || backoffBaseMillis < 1
                || backoffCapMillis < backoffBaseMillis) {
            throw new IllegalArgumentException("invalid tile refresh policy");
        }
    }

    public static TileRefreshPolicy standard(long ttlMillis) {
        return new TileRefreshPolicy(ttlMillis, 400, 4, 2, 5_000, 300_000);
    }

    public static TileRefreshPolicy standardSeconds(int ttlSeconds) {
        return standard(Math.multiplyExact((long) ttlSeconds, 1_000L));
    }
}
