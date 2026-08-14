package com.murphypotato.simmctoolset.internal.map.cache;

import com.murphypotato.simmctoolset.internal.map.render.TileKey;
import com.murphypotato.simmctoolset.internal.map.render.TileValidators;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public final class TileDiskCache implements AutoCloseable {
    private static final int MAGIC = 0x53494D54;
    private static final int VERSION_1 = 1;
    private static final int VERSION = 2;
    private static final int INDEX_MAGIC = 0x53494D58;
    private static final int INDEX_VERSION = 2;
    private static final String INDEX_FILENAME = ".tile-index";
    private static final Pattern OWNED_TILE_NAME = Pattern.compile("[0-9a-f]{64}\\.tile");
    private static final long DEFAULT_MAX_BYTES = 512L * 1024 * 1024;
    private static final int DEFAULT_MAX_PNG_BYTES = 8 * 1024 * 1024;
    private static final int DEFAULT_SCAN_BATCH = 10_000;
    private static final int METADATA_OVERHEAD_LIMIT = 8 * 1024;
    private static final int MAX_INDEX_BYTES = 16 * 1024 * 1024;
    private static final int MAX_INDEX_ENTRIES = 100_000;
    private static final int MAX_INDEX_NAME_BYTES = 80;
    private static final int MAX_RUNTIME_PIN_NAMES = 4_096;
    private static final long MAX_IMAGE_PIXELS = 100_000_000L;

    private final Path directory;
    private final Path indexPath;
    private final int maxPngBytes;
    private final int maxEntryBytes;
    private final int scanBatchSize;
    private final Runnable beforeEvictionReservation;
    private final Object stateLock = new Object();
    private final Object pinLock = new Object();
    private final AtomicLong accessClock = new AtomicLong(System.currentTimeMillis());
    private final Set<String> runtimePinNames = new LinkedHashSet<>();
    private final Set<String> blockedOwnedNames = new HashSet<>();
    private final Map<String, DiskEntry> index = new LinkedHashMap<>();
    private volatile long maxBytes;
    private volatile boolean closed;

    public TileDiskCache(Path directory) {
        this(directory, DEFAULT_MAX_BYTES, DEFAULT_MAX_PNG_BYTES, DEFAULT_SCAN_BATCH);
    }

    public TileDiskCache(Path directory, long maxBytes, int maxPngBytes, int scanBatchSize) {
        this(directory, maxBytes, maxPngBytes, scanBatchSize, () -> { });
    }

    TileDiskCache(Path directory, long maxBytes, int maxPngBytes, int scanBatchSize,
                  Runnable beforeEvictionReservation) {
        this.directory = directory.toAbsolutePath().normalize();
        this.indexPath = this.directory.resolve(INDEX_FILENAME);
        if (maxBytes < 0 || maxPngBytes < 8 || maxPngBytes > Integer.MAX_VALUE - METADATA_OVERHEAD_LIMIT
                || scanBatchSize < 1) throw new IllegalArgumentException("invalid cache limits");
        this.maxBytes = maxBytes;
        this.maxPngBytes = maxPngBytes;
        this.maxEntryBytes = maxPngBytes + METADATA_OVERHEAD_LIMIT;
        this.scanBatchSize = scanBatchSize;
        this.beforeEvictionReservation = java.util.Objects.requireNonNull(
                beforeEvictionReservation, "beforeEvictionReservation");
        synchronized (stateLock) {
            try {
                Files.createDirectories(this.directory);
                loadOrRebuildIndex();
                synchronized (pinLock) {
                    index.forEach((name, entry) -> {
                        if (entry.pinned()) addRuntimePinLocked(name);
                    });
                }
                if (trimLocked(Set.of()) > maxBytes) throw new IOException("could not enforce tile cache capacity");
                writeIndex();
            } catch (IOException failure) {
                throw new SnapshotDiskCache.CacheWriteException(failure);
            }
        }
    }

    public Optional<byte[]> get(TileKey key) {
        return getEntry(key).map(TileCacheEntry::png);
    }

    public Optional<TileCacheEntry> getEntry(TileKey key) {
        synchronized (stateLock) {
            ensureOpen();
            String name = filename(key);
            Path path = contained(name);
            if (!index.containsKey(name) || !Files.isRegularFile(path)) {
                if (index.containsKey(name)) {
                    if (AtomicFiles.quarantine(path)) forgetEntry(name);
                    else refreshAccountingOrBlock(name, path);
                    writeIndexQuietly();
                }
                return Optional.empty();
            }
            StoredEntry stored;
            try {
                stored = read(path, key);
            } catch (IOException | RuntimeException invalid) {
                if (AtomicFiles.quarantine(path)) forgetEntry(name);
                else refreshAccountingOrBlock(name, path);
                writeIndexQuietly();
                return Optional.empty();
            }
            try {
                byte[] png = stored.entry().png();
                publishLocked(key, stored.entry(), png, nextAccess());
            } catch (IOException | RuntimeException publicationFailure) {
                // A failed LRU metadata touch must not turn a valid stale tile into corruption.
            }
            return Optional.of(stored.entry());
        }
    }

    public void put(TileKey key, byte[] png) {
        if (png == null || png.length < 8 || png.length > maxPngBytes) {
            throw new IllegalArgumentException("invalid PNG size");
        }
        putEntry(key, new TileCacheEntry(png, TileValidators.EMPTY, 0));
    }

    public boolean putEntry(TileKey key, TileCacheEntry entry) {
        java.util.Objects.requireNonNull(key, "key");
        java.util.Objects.requireNonNull(entry, "entry");
        byte[] png = entry.png();
        if (png == null || png.length < 8 || png.length > maxPngBytes) {
            throw new IllegalArgumentException("invalid PNG size");
        }
        if (!isValidPng(png)) throw new IllegalArgumentException("tile is not a valid PNG");
        synchronized (stateLock) {
            ensureOpen();
            if (!blockedOwnedNames.isEmpty()) return false;
            long access = nextAccess();
            try {
                return publishLocked(key, entry, png, access);
            } catch (IOException failure) {
                throw new SnapshotDiskCache.CacheWriteException(failure);
            }
        }
    }

    public boolean markValidated(TileKey key, TileValidators validators, long lastCheckedMillis) {
        java.util.Objects.requireNonNull(key, "key");
        java.util.Objects.requireNonNull(validators, "validators");
        if (lastCheckedMillis < 0) throw new IllegalArgumentException("lastCheckedMillis must be non-negative");
        synchronized (stateLock) {
            ensureOpen();
            String name = filename(key);
            Path path = contained(name);
            if (!index.containsKey(name) || !Files.isRegularFile(path)) return false;
            StoredEntry stored;
            try {
                stored = read(path, key);
            } catch (IOException | RuntimeException invalid) {
                if (AtomicFiles.quarantine(path)) forgetEntry(name);
                else refreshAccountingOrBlock(name, path);
                writeIndexQuietly();
                return false;
            }
            try {
                byte[] png = stored.entry().png();
                TileCacheEntry updated = new TileCacheEntry(png, validators, lastCheckedMillis);
                return publishLocked(key, updated, png, stored.lastAccess());
            } catch (IOException | RuntimeException publicationFailure) {
                return false;
            }
        }
    }

    private boolean publishLocked(TileKey key, TileCacheEntry entry, byte[] png, long lastAccess) throws IOException {
        String name = filename(key);
        byte[] container = container(key, entry, png, lastAccess);
        if (container.length > maxBytes) return false;
        DiskEntry previous = index.get(name);
        boolean incomingPinned = isRuntimePinned(name) || (previous != null && previous.pinned());
        long reservationLimit = saturatedAdd(maxBytes - container.length,
                previous == null ? 0 : previous.size());
        try {
            if (trimLocked(Set.of(), reservationLimit, name, incomingPinned) > reservationLimit) {
                writeIndexQuietly();
                if (incomingPinned) throw new IOException("could not reserve tile cache capacity");
                return false;
            }
        } catch (RuntimeException reservationFailure) {
            writeIndexQuietly();
            throw reservationFailure;
        }
        previous = index.put(name, new DiskEntry(container.length, lastAccess, incomingPinned));
        try {
            // Index-first makes a crash conservative: it can over-count, never hide an owned file.
            writeIndex();
            AtomicFiles.write(contained(name), container);
        } catch (IOException failure) {
            if (previous == null) index.remove(name); else index.put(name, previous);
            writeIndexQuietly();
            throw failure;
        }
        blockedOwnedNames.remove(name);
        return true;
    }

    public void setMaxBytes(long maxBytes, Set<TileKey> pinned) {
        if (maxBytes < 0) throw new IllegalArgumentException("maxBytes must be non-negative");
        synchronized (stateLock) {
            ensureOpen();
            long previousMaxBytes = this.maxBytes;
            this.maxBytes = maxBytes;
            if (trimLocked(Set.copyOf(pinned)) > maxBytes) {
                this.maxBytes = previousMaxBytes;
                throw new SnapshotDiskCache.CacheWriteException(
                        new IOException("could not enforce tile cache capacity"));
            }
            writeIndexQuietly();
        }
    }

    public void pin(Set<TileKey> keys) {
        pinInMemory(keys);
        persistPins();
    }

    public void clearPins() {
        clearPinsInMemory();
        persistPins();
    }

    /** Updates trim-visible pin state without performing file-system I/O. */
    public void pinInMemory(Set<TileKey> keys) {
        List<String> names = Set.copyOf(keys).stream().map(this::filename).sorted().toList();
        synchronized (pinLock) {
            ensureOpen();
            names.forEach(this::addRuntimePinLocked);
        }
    }

    /** Clears trim-visible pin state without performing file-system I/O. */
    public void clearPinsInMemory() {
        synchronized (pinLock) { runtimePinNames.clear(); }
    }

    /** Persists the current live index state. Intended for a background I/O worker. */
    public boolean persistPins() {
        synchronized (stateLock) {
            Set<String> pinned = runtimePinNames();
            index.replaceAll((name, entry) -> new DiskEntry(
                    entry.size(), entry.lastAccess(), pinned.contains(name)));
            return trimLocked(Set.of()) <= maxBytes && writeIndexQuietly();
        }
    }

    public long entrySize(TileKey key) {
        synchronized (stateLock) {
            DiskEntry entry = index.get(filename(key));
            return entry == null ? 0 : entry.size();
        }
    }

    public void remove(TileKey key) {
        synchronized (stateLock) {
            ensureOpen();
            String name = filename(key);
            Path target = contained(name);
            if (deleteOrQuarantine(target)) forgetEntry(name);
            else refreshAccountingOrBlock(name, target);
            writeIndexQuietly();
        }
    }

    Path pathForTesting(TileKey key) { return contained(filename(key)); }

    private void loadOrRebuildIndex() throws IOException {
        boolean loaded = false;
        if (Files.isRegularFile(indexPath)) {
            try {
                readIndex();
                loaded = true;
            } catch (IOException | RuntimeException corrupt) {
                index.clear();
                AtomicFiles.quarantine(indexPath);
            }
        }
        reconcileOwnedFiles(loaded);
    }

    private void readIndex() throws IOException {
        byte[] bytes = readBounded(indexPath, MAX_INDEX_BYTES);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != INDEX_MAGIC || input.readInt() != INDEX_VERSION) throw new IOException("invalid tile index header");
            int count = input.readInt();
            if (count < 0 || count > MAX_INDEX_ENTRIES) throw new IOException("invalid tile index count");
            for (int i = 0; i < count; i++) {
                int nameLength = input.readInt();
                if (nameLength < 1 || nameLength > MAX_INDEX_NAME_BYTES) throw new IOException("invalid tile index name");
                byte[] nameBytes = input.readNBytes(nameLength);
                if (nameBytes.length != nameLength) throw new EOFException();
                String name = new String(nameBytes, StandardCharsets.US_ASCII);
                if (!OWNED_TILE_NAME.matcher(name).matches() || index.put(name,
                        new DiskEntry(input.readLong(), input.readLong(), input.readBoolean())) != null) {
                    throw new IOException("unsafe or duplicate tile index entry");
                }
                DiskEntry entry = index.get(name);
                if (entry.size() < 0) throw new IOException("invalid indexed tile size");
            }
            if (input.read() != -1) throw new IOException("trailing tile index data");
        }
    }

    private void reconcileOwnedFiles(boolean trustedIndex) throws IOException {
        Set<String> seen = new HashSet<>(Math.min(index.size() * 2 + 1, MAX_INDEX_ENTRIES));
        int visitedInBatch = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.tile")) {
            for (Path path : files) {
                String name = path.getFileName().toString();
                if (!OWNED_TILE_NAME.matcher(name).matches()) continue;
                if (seen.size() >= MAX_INDEX_ENTRIES) {
                    Files.deleteIfExists(path);
                    continue;
                }
                seen.add(name);
                try {
                    long actualSize = Files.size(path);
                    DiskEntry known = index.get(name);
                    if (!trustedIndex || known == null || known.size() != actualSize
                            || !isPersistableEntrySize(known.size())) {
                        StoredEntry entry = read(path, null);
                        known = new DiskEntry(actualSize, entry.lastAccess(), known != null && known.pinned());
                        index.put(name, known);
                    }
                    accessClock.accumulateAndGet(known.lastAccess(), Math::max);
                } catch (IOException | RuntimeException invalid) {
                    if (AtomicFiles.quarantine(path)) {
                        index.remove(name);
                    } else {
                        refreshAccountingOrBlock(name, path);
                    }
                }
                if (++visitedInBatch == scanBatchSize) visitedInBatch = 0;
            }
        }
        index.keySet().removeIf(name -> !seen.contains(name));
    }

    private long trimLocked(Set<TileKey> pinned) {
        return trimLocked(pinned, maxBytes, null, true);
    }

    private long trimLocked(Set<TileKey> pinned, long targetBytes, String excludedName,
                            boolean allowPinnedEviction) {
        long total = indexedBytesLocked();
        if (total <= targetBytes) return total;
        Set<String> pinnedNames = pinned.stream().map(this::filename)
                .collect(java.util.stream.Collectors.toSet());
        List<Map.Entry<String, DiskEntry>> candidates = new ArrayList<>(index.entrySet());
        candidates.sort(Comparator.comparingLong((Map.Entry<String, DiskEntry> entry) -> entry.getValue().lastAccess())
                .thenComparing(Map.Entry::getKey));
        total = evictCandidates(total, targetBytes, candidates, pinnedNames, excludedName, false);
        if (allowPinnedEviction && total > targetBytes) {
            total = evictCandidates(total, targetBytes, candidates, pinnedNames, excludedName, true);
        }
        return total;
    }

    private long evictCandidates(long total, long targetBytes,
                                 List<Map.Entry<String, DiskEntry>> candidates,
                                 Set<String> pinnedNames, String excludedName, boolean forcePinned) {
        for (Map.Entry<String, DiskEntry> candidate : candidates) {
            if (total <= targetBytes) break;
            if (candidate.getKey().equals(excludedName)) continue;
            if (!forcePinned && (candidate.getValue().pinned() || pinnedNames.contains(candidate.getKey()))) continue;
            beforeEvictionReservation.run();
            DiskEntry claimed;
            synchronized (pinLock) {
                if (!forcePinned && runtimePinNames.contains(candidate.getKey())) continue;
                claimed = index.remove(candidate.getKey());
            }
            if (claimed == null) continue;
            Path path = contained(candidate.getKey());
            try {
                Files.deleteIfExists(path);
                forgetEntry(candidate.getKey());
                total = total == Long.MAX_VALUE || claimed.size() == Long.MAX_VALUE
                        ? indexedBytesLocked() : total - claimed.size();
            } catch (IOException ignored) {
                // Keep the indexed size when Windows or another process still holds the file.
                index.put(candidate.getKey(), claimed);
                refreshAccountingOrBlock(candidate.getKey(), path);
                total = indexedBytesLocked();
            }
        }
        return total;
    }

    private StoredEntry read(Path path, TileKey expectedKey) throws IOException {
        byte[] bytes = readBounded(path, maxEntryBytes);
        if (bytes.length < 60) throw new IOException("invalid entry size");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC) throw new IOException("invalid header");
            int version = input.readInt();
            if (version != VERSION_1 && version != VERSION) throw new IOException("invalid version");
            long lastAccess = input.readLong();
            int keyLength = input.readInt();
            if (keyLength < 0 || keyLength > 4096) throw new IOException("invalid key length");
            byte[] keyBytes = input.readNBytes(keyLength);
            if (keyBytes.length != keyLength) throw new EOFException();
            String canonical = new String(keyBytes, StandardCharsets.UTF_8);
            if (expectedKey != null && !expectedKey.canonical().equals(canonical)) throw new IOException("key mismatch");
            long lastCheckedMillis = 0;
            TileValidators validators = TileValidators.EMPTY;
            if (version == VERSION) {
                lastCheckedMillis = input.readLong();
                if (lastCheckedMillis < 0) throw new IOException("invalid validation time");
                validators = new TileValidators(readNullable(input), readNullable(input));
            }
            int pngLength = input.readInt();
            if (pngLength < 8 || pngLength > maxPngBytes) throw new IOException("invalid PNG length");
            byte[] expectedHash = input.readNBytes(32);
            byte[] png = input.readNBytes(pngLength);
            if (expectedHash.length != 32 || png.length != pngLength || input.read() != -1
                    || !MessageDigest.isEqual(expectedHash, digest(png)) || !isValidPng(png)) {
                throw new IOException("entry integrity failure");
            }
            return new StoredEntry(new TileCacheEntry(png, validators, lastCheckedMillis), lastAccess);
        }
    }

    private byte[] container(TileKey key, TileCacheEntry entry, byte[] png, long lastAccess) throws IOException {
        byte[] canonical = key.canonical().getBytes(StandardCharsets.UTF_8);
        if (canonical.length > 4096) throw new IOException("key too long");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(png.length + canonical.length + 64);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeLong(lastAccess);
            output.writeInt(canonical.length);
            output.write(canonical);
            output.writeLong(entry.lastCheckedMillis());
            writeNullable(output, entry.validators().etag());
            writeNullable(output, entry.validators().lastModified());
            output.writeInt(png.length);
            output.write(digest(png));
            output.write(png);
        }
        if (bytes.size() > maxEntryBytes) throw new IOException("tile entry exceeds bound");
        return bytes.toByteArray();
    }

    private static void writeNullable(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > TileValidators.MAX_VALUE_BYTES) throw new IOException("validator too long");
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readNullable(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length == -1) return null;
        if (length < 0 || length > TileValidators.MAX_VALUE_BYTES) {
            throw new IOException("invalid validator length");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException malformed) {
            throw new IOException("invalid validator UTF-8", malformed);
        }
    }

    private void writeIndex() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.min(MAX_INDEX_BYTES, 16 + index.size() * 90));
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(INDEX_MAGIC);
            output.writeInt(INDEX_VERSION);
            output.writeInt(index.size());
            for (Map.Entry<String, DiskEntry> entry : index.entrySet()) {
                byte[] name = entry.getKey().getBytes(StandardCharsets.US_ASCII);
                output.writeInt(name.length);
                output.write(name);
                output.writeLong(entry.getValue().size());
                output.writeLong(entry.getValue().lastAccess());
                output.writeBoolean(entry.getValue().pinned());
            }
        }
        if (bytes.size() > MAX_INDEX_BYTES) throw new IOException("tile index exceeds bound");
        AtomicFiles.write(indexPath, bytes.toByteArray());
    }

    private boolean writeIndexQuietly() {
        try {
            writeIndex();
            return true;
        } catch (IOException ignored) {
            // A later startup safely rebuilds from bounded owned entries.
            return false;
        }
    }

    private boolean isRuntimePinned(String name) {
        synchronized (pinLock) { return runtimePinNames.contains(name); }
    }

    private Set<String> runtimePinNames() {
        synchronized (pinLock) { return new HashSet<>(runtimePinNames); }
    }

    private boolean deleteOrQuarantine(Path target) {
        try {
            Files.deleteIfExists(target);
            return true;
        } catch (IOException ignored) {
            return AtomicFiles.quarantine(target);
        }
    }

    private void forgetEntry(String name) {
        index.remove(name);
        blockedOwnedNames.remove(name);
        synchronized (pinLock) { runtimePinNames.remove(name); }
    }

    private void refreshAccountingOrBlock(String name, Path path) {
        DiskEntry previous = index.get(name);
        try {
            long actualSize = Files.size(path);
            long accountedSize = previous == null || previous.size() == Long.MAX_VALUE
                    ? actualSize : Math.max(previous.size(), actualSize);
            long lastAccess = previous == null ? nextAccess() : previous.lastAccess();
            boolean pinned = previous != null && previous.pinned();
            index.put(name, new DiskEntry(accountedSize, lastAccess, pinned));
            if (!isPersistableEntrySize(accountedSize)) blockedOwnedNames.add(name);
            else blockedOwnedNames.remove(name);
        } catch (IOException | RuntimeException unreadable) {
            blockedOwnedNames.add(name);
            long lastAccess = previous == null ? nextAccess() : previous.lastAccess();
            boolean pinned = previous != null && previous.pinned();
            index.put(name, new DiskEntry(Long.MAX_VALUE, lastAccess, pinned));
        }
    }

    private boolean isPersistableEntrySize(long size) {
        return size >= 60 && size <= maxEntryBytes;
    }

    private long indexedBytesLocked() {
        long total = 0;
        for (DiskEntry entry : index.values()) total = saturatedAdd(total, entry.size());
        return total;
    }

    private void addRuntimePinLocked(String name) {
        runtimePinNames.remove(name);
        runtimePinNames.add(name);
        while (runtimePinNames.size() > MAX_RUNTIME_PIN_NAMES) {
            var oldest = runtimePinNames.iterator();
            oldest.next();
            oldest.remove();
        }
    }

    static byte[] readBounded(Path path, int maxBytes) throws IOException {
        if (maxBytes < 0) throw new IllegalArgumentException("maxBytes must be non-negative");
        try (InputStream input = Files.newInputStream(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for (int read; (read = input.read(buffer)) != -1;) {
                if (read > maxBytes - total) throw new IOException("cache file exceeds bound");
                output.write(buffer, 0, read);
                total += read;
            }
            return output.toByteArray();
        }
    }

    private Path contained(String name) {
        if (!OWNED_TILE_NAME.matcher(name).matches()) throw new IllegalArgumentException("unsafe tile filename");
        Path result = directory.resolve(name).normalize();
        if (!directory.equals(result.getParent())) throw new IllegalStateException("tile path escaped cache directory");
        return result;
    }

    private String filename(TileKey key) {
        return HexFormat.of().formatHex(digest(key.canonical().getBytes(StandardCharsets.UTF_8))) + ".tile";
    }

    private long nextAccess() {
        return accessClock.updateAndGet(previous -> Math.max(previous + 1, System.currentTimeMillis()));
    }

    private static boolean isPng(byte[] bytes) {
        byte[] magic = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        for (int i = 0; i < magic.length; i++) if (bytes[i] != magic[i]) return false;
        return true;
    }

    private static boolean isValidPng(byte[] bytes) {
        if (!isPng(bytes)) return false;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return false;
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return width > 0 && height > 0 && (long) width * height <= MAX_IMAGE_PIXELS;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException invalid) {
            return false;
        }
    }

    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("tile cache is closed");
    }

    @Override public void close() {
        synchronized (stateLock) {
            synchronized (pinLock) {
                runtimePinNames.clear();
                blockedOwnedNames.clear();
                closed = true;
            }
        }
    }

    private record StoredEntry(TileCacheEntry entry, long lastAccess) {}
    private record DiskEntry(long size, long lastAccess, boolean pinned) {}
}
