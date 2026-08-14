package com.murphypotato.simmctoolset.internal.map.cache;

import com.murphypotato.simmctoolset.internal.map.network.HttpValidators;

import java.time.Instant;
import java.util.Objects;

public record SnapshotCacheEntry(String worldKey, String settingsJson, String markersJson, String playersJson,
                                 HttpValidators settingsValidators, HttpValidators markersValidators,
                                 HttpValidators playersValidators, Instant fetchedAt,
                                 Instant playersLastSuccessAt, boolean playersAvailable) {
    public SnapshotCacheEntry {
        Objects.requireNonNull(worldKey, "worldKey");
        Objects.requireNonNull(settingsJson, "settingsJson");
        Objects.requireNonNull(markersJson, "markersJson");
        Objects.requireNonNull(playersJson, "playersJson");
        settingsValidators = Objects.requireNonNullElse(settingsValidators, HttpValidators.EMPTY);
        markersValidators = Objects.requireNonNullElse(markersValidators, HttpValidators.EMPTY);
        playersValidators = Objects.requireNonNullElse(playersValidators, HttpValidators.EMPTY);
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        if (playersAvailable) Objects.requireNonNull(playersLastSuccessAt, "playersLastSuccessAt");
    }

    public SnapshotCacheEntry(String worldKey, String settingsJson, String markersJson, String playersJson,
                              HttpValidators settingsValidators, HttpValidators markersValidators,
                              HttpValidators playersValidators, Instant fetchedAt) {
        this(worldKey, settingsJson, markersJson, playersJson, settingsValidators, markersValidators,
                playersValidators, fetchedAt, fetchedAt, true);
    }
}
