package com.murphypotato.simmctoolset.internal.map.cache;

import com.murphypotato.simmctoolset.internal.map.network.HttpValidators;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public final class SnapshotDiskCache {
    private static final int FORMAT_VERSION = 2;
    public static final long DEFAULT_MAX_CONTAINER_BYTES = 24L * 1024 * 1024;
    private static final int MAX_WORLD_KEY_BYTES = 256;
    private static final int MAX_VALIDATOR_BYTES = 8192;
    private static final int MAX_TIMESTAMP_BYTES = 64;
    private static final long MAX_SETTINGS_BYTES = 1024L * 1024;
    private static final long MAX_MARKERS_BYTES = 16L * 1024 * 1024;
    private static final long MAX_PLAYERS_BYTES = 4L * 1024 * 1024;
    private static final Object[] LOCKS = new Object[64];
    static { for (int i = 0; i < LOCKS.length; i++) LOCKS[i] = new Object(); }
    private static final Gson GSON = new Gson();
    private final Path file;
    private final long maxContainerBytes;
    private final Object pathLock;
    private final OperationHook operationHook;

    public SnapshotDiskCache(Path file) {
        this(file, DEFAULT_MAX_CONTAINER_BYTES);
    }

    public SnapshotDiskCache(Path file, long maxContainerBytes) {
        this(file, maxContainerBytes, OperationHook.NONE);
    }

    SnapshotDiskCache(Path file, long maxContainerBytes, OperationHook operationHook) {
        this.file = file.toAbsolutePath().normalize();
        if (maxContainerBytes < 1) throw new IllegalArgumentException("maxContainerBytes must be positive");
        this.maxContainerBytes = maxContainerBytes;
        this.pathLock = LOCKS[Math.floorMod(this.file.hashCode(), LOCKS.length)];
        this.operationHook = operationHook;
    }

    public Optional<SnapshotCacheEntry> load() {
        synchronized (pathLock) {
            if (!Files.isRegularFile(file)) return Optional.empty();
            try {
                if (Files.size(file) > maxContainerBytes) throw new IOException("snapshot exceeds container limit");
                byte[] encoded = readBounded();
                operationHook.afterReadBeforeParse();
                Stored stored = GSON.fromJson(new String(encoded, StandardCharsets.UTF_8), Stored.class);
                validate(stored);
                return Optional.of(new SnapshotCacheEntry(stored.worldKey, stored.settingsJson, stored.markersJson,
                        stored.playersJson, validators(stored.settings), validators(stored.markers), validators(stored.players),
                        Instant.parse(stored.fetchedAt), instant(stored.playersLastSuccessAt), stored.playersAvailable));
            } catch (IOException | RuntimeException invalid) {
                AtomicFiles.quarantine(file);
                return Optional.empty();
            }
        }
    }

    public void save(SnapshotCacheEntry entry) {
        Stored stored = new Stored(FORMAT_VERSION, entry.worldKey(), entry.settingsJson(), entry.markersJson(),
                entry.playersJson(), stored(entry.settingsValidators()), stored(entry.markersValidators()),
                stored(entry.playersValidators()), entry.fetchedAt().toString(),
                entry.playersLastSuccessAt() == null ? null : entry.playersLastSuccessAt().toString(),
                entry.playersAvailable());
        validate(stored);
        byte[] encoded = GSON.toJson(stored).getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maxContainerBytes) throw new IllegalArgumentException("snapshot exceeds container limit");
        operationHook.beforeSaveLock();
        synchronized (pathLock) {
            try {
                AtomicFiles.write(file, encoded);
            } catch (IOException exception) {
                throw new CacheWriteException(exception);
            }
        }
    }

    public void quarantine() {
        synchronized (pathLock) {
            AtomicFiles.quarantine(file);
        }
    }

    private byte[] readBounded() throws IOException {
        try (InputStream input = Files.newInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            for (int read; (read = input.read(buffer)) != -1;) {
                total += read;
                if (total > maxContainerBytes) throw new IOException("snapshot grew beyond container limit");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void validate(Stored stored) {
        if (stored == null || stored.version != FORMAT_VERSION || stored.worldKey == null
                || stored.settingsJson == null || stored.markersJson == null || stored.playersJson == null
                || stored.fetchedAt == null || (stored.playersAvailable && stored.playersLastSuccessAt == null)) {
            throw new JsonParseException("invalid snapshot cache");
        }
        requireBytes(stored.worldKey, MAX_WORLD_KEY_BYTES, "world key");
        requireBytes(stored.settingsJson, MAX_SETTINGS_BYTES, "settings payload");
        requireBytes(stored.markersJson, MAX_MARKERS_BYTES, "markers payload");
        requireBytes(stored.playersJson, MAX_PLAYERS_BYTES, "players payload");
        if (byteLength(stored.settingsJson) + byteLength(stored.markersJson) + byteLength(stored.playersJson)
                > MAX_SETTINGS_BYTES + MAX_MARKERS_BYTES + MAX_PLAYERS_BYTES) {
            throw new IllegalArgumentException("snapshot payload total exceeds limit");
        }
        requireBytes(stored.fetchedAt, MAX_TIMESTAMP_BYTES, "timestamp");
        if (stored.playersLastSuccessAt != null) requireBytes(stored.playersLastSuccessAt, MAX_TIMESTAMP_BYTES, "timestamp");
        validate(stored.settings); validate(stored.markers); validate(stored.players);
    }

    private static void validate(Validator validator) {
        if (validator == null) return;
        if (validator.etag != null) requireBytes(validator.etag, MAX_VALIDATOR_BYTES, "validator");
        if (validator.lastModified != null) requireBytes(validator.lastModified, MAX_VALIDATOR_BYTES, "validator");
    }

    private static void requireBytes(String value, long maximum, String field) {
        if (byteLength(value) > maximum) throw new IllegalArgumentException(field + " exceeds limit");
    }

    private static long byteLength(String value) {
        long bytes = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x7f) bytes++;
            else if (c <= 0x7ff) bytes += 2;
            else if (Character.isHighSurrogate(c) && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) { bytes += 4; i++; }
            else if (Character.isSurrogate(c)) bytes++;
            else bytes += 3;
        }
        return bytes;
    }

    private static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static Validator stored(HttpValidators value) {
        return new Validator(value.etag(), value.lastModified());
    }

    private static HttpValidators validators(Validator value) {
        return value == null ? HttpValidators.EMPTY : new HttpValidators(value.etag, value.lastModified);
    }

    private record Stored(int version, String worldKey, String settingsJson, String markersJson, String playersJson,
                          Validator settings, Validator markers, Validator players, String fetchedAt,
                          String playersLastSuccessAt, boolean playersAvailable) {}
    private record Validator(String etag, String lastModified) {}

    interface OperationHook {
        OperationHook NONE = new OperationHook() {};
        default void afterReadBeforeParse() {}
        default void beforeSaveLock() {}
    }

    public static final class CacheWriteException extends RuntimeException {
        CacheWriteException(IOException cause) { super("Could not persist snapshot cache", cause); }
    }
}
