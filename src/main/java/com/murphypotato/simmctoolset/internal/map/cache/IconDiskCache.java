package com.murphypotato.simmctoolset.internal.map.cache;

import com.murphypotato.simmctoolset.internal.map.network.HttpValidators;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

public final class IconDiskCache implements IconCacheStore {
    private static final int MAGIC = 0x53494D49;
    private static final int VERSION = 1;
    private static final int DEFAULT_MAX_PNG_BYTES = 4 * 1024 * 1024;
    private static final int MAX_METADATA_BYTES = 64 * 1024;
    private final Path directory;
    private final int maxPngBytes;
    private static final Object[] locks = new Object[64];
    static { for (int i = 0; i < locks.length; i++) locks[i] = new Object(); }

    public IconDiskCache(Path directory) { this(directory, DEFAULT_MAX_PNG_BYTES); }

    public IconDiskCache(Path directory, int maxPngBytes) {
        this.directory = directory.toAbsolutePath().normalize();
        if (maxPngBytes < 1) throw new IllegalArgumentException("maxPngBytes must be positive");
        this.maxPngBytes = maxPngBytes;
    }

    public Optional<IconCacheEntry> load(String key) {
        String name = filename(key);
        Path file = contained(name);
        if (!Files.isRegularFile(file)) return Optional.empty();
        synchronized (lock(name)) {
            try {
                long size = Files.size(file);
                if (size < 61 || size > (long) maxPngBytes + MAX_METADATA_BYTES) throw new IOException("invalid icon entry size");
                byte[] container = Files.readAllBytes(file);
                try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(container))) {
                    if (input.readInt() != MAGIC || input.readInt() != VERSION) throw new IOException("invalid icon header");
                    long lastAccessMillis = input.readLong();
                    String etag = readNullable(input);
                    String modified = readNullable(input);
                    int pngLength = input.readInt();
                    if (pngLength < 1 || pngLength > maxPngBytes) throw new IOException("invalid PNG length");
                    byte[] expectedHash = input.readNBytes(32);
                    if (expectedHash.length != 32) throw new EOFException();
                    byte[] png = input.readNBytes(pngLength);
                    if (png.length != pngLength || input.read() != -1 || !MessageDigest.isEqual(expectedHash, digest(png))) {
                        throw new IOException("icon entry integrity failure");
                    }
                    return Optional.of(new IconCacheEntry(png, new HttpValidators(etag, modified),
                            Instant.ofEpochMilli(lastAccessMillis)));
                }
            } catch (IOException | RuntimeException invalid) {
                AtomicFiles.quarantine(file);
                return Optional.empty();
            }
        }
    }

    public void save(String key, IconCacheEntry entry) {
        byte[] png = entry.png();
        if (png.length > maxPngBytes) throw new IllegalArgumentException("PNG exceeds icon cache limit");
        String name = filename(key);
        synchronized (lock(name)) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(png.length + 128);
                try (DataOutputStream output = new DataOutputStream(bytes)) {
                    output.writeInt(MAGIC); output.writeInt(VERSION); output.writeLong(entry.lastAccess().toEpochMilli());
                    writeNullable(output, entry.validators().etag());
                    writeNullable(output, entry.validators().lastModified());
                    output.writeInt(png.length); output.write(digest(png)); output.write(png);
                }
                AtomicFiles.write(contained(name), bytes.toByteArray());
            } catch (IOException exception) {
                throw new SnapshotDiskCache.CacheWriteException(exception);
            }
        }
    }

    private String filename(String key) { return sha256(key.getBytes(StandardCharsets.UTF_8)) + ".icon"; }
    private Object lock(String name) {
        return locks[Math.floorMod(31 * directory.hashCode() + name.hashCode(), locks.length)];
    }

    private Path contained(String filename) {
        Path result = directory.resolve(filename).normalize();
        if (!result.getParent().equals(directory)) throw new IllegalStateException("cache path escaped directory");
        return result;
    }

    private static void writeNullable(DataOutputStream output, String value) throws IOException {
        if (value == null) { output.writeInt(-1); return; }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 8192) throw new IOException("validator too long");
        output.writeInt(bytes.length); output.write(bytes);
    }

    private static String readNullable(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length == -1) return null;
        if (length < 0 || length > 8192) throw new IOException("invalid validator length");
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String sha256(byte[] bytes) { return HexFormat.of().formatHex(digest(bytes)); }
}
