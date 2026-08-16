package com.murphypotato.simmctoolset.internal.map.network;

import com.murphypotato.simmctoolset.internal.map.cache.IconCacheEntry;
import com.murphypotato.simmctoolset.internal.map.cache.IconCacheStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

public final class IconResourceLoader implements RegisteredIconLoader {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();
    private final URI baseUrl;
    private final SquaremapHttpClient http;
    private final IconCacheStore cache;
    private final Clock clock;
    private final byte[] fallback;
    private final ThreadPoolExecutor workExecutor;
    private final java.util.Set<CompletableFuture<IconBytes>> active = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public IconResourceLoader(URI baseUrl, SquaremapHttpClient http, IconCacheStore cache, Clock clock) {
        this(baseUrl, http, cache, clock, 2, 32);
    }

    public IconResourceLoader(URI baseUrl, SquaremapHttpClient http, IconCacheStore cache, Clock clock,
                              int maxConcurrentWork, int queueCapacity) {
        this.baseUrl = validateBase(baseUrl);
        this.http = Objects.requireNonNull(http, "http");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxConcurrentWork < 1 || queueCapacity < 1) throw new IllegalArgumentException("icon work limits must be positive");
        this.workExecutor = new ThreadPoolExecutor(maxConcurrentWork, maxConcurrentWork, 1, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                    Thread thread = new Thread(runnable, "simmc-icon-work-" + THREAD_SEQUENCE.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        this.workExecutor.allowCoreThreadTimeOut(true);
        this.fallback = loadFallback();
    }

    public CompletableFuture<IconBytes> load(String registeredKey) {
        String key = validateKey(registeredKey);
        if (closed.get()) throw new IllegalStateException("icon loader is closed");
        CompletableFuture<IconBytes> output = new CompletableFuture<>();
        active.add(output);
        output.whenComplete((ignored, failure) -> active.remove(output));
        try {
            workExecutor.execute(() -> {
                if (closed.get()) { output.cancel(true); return; }
                CompletableFuture<HttpResult> request = null;
                try {
                    Optional<IconCacheEntry> existing = cache.load(key);
                    HttpValidators validators = existing.map(IconCacheEntry::validators).orElse(HttpValidators.EMPTY);
                    URI uri = URI.create(trimSlash(baseUrl.toASCIIString()) + "/images/icon/registered/" + encodeSegment(key) + ".png");
                    request = http.get(uri, validators);
                    HttpResult result = request.get();
                    if (!closed.get()) output.complete(resolve(key, existing, result));
                    else output.cancel(true);
                } catch (InterruptedException interrupted) {
                    if (request != null) request.cancel(true);
                    Thread.currentThread().interrupt();
                    output.cancel(true);
                } catch (ExecutionException failure) {
                    output.completeExceptionally(failure.getCause());
                } catch (CancellationException cancellation) {
                    output.cancel(true);
                } catch (RuntimeException failure) {
                    output.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException saturated) {
            active.remove(output);
            output.completeExceptionally(saturated);
        }
        return output;
    }

    /** Returns false when the bounded client-thread upload queue rejects the decoded icon. */
    public CompletableFuture<Boolean> loadAndEnqueue(String registeredKey, IconUploadSink sink) {
        Objects.requireNonNull(sink, "sink");
        return load(registeredKey).thenApply(icon -> sink.enqueue(registeredKey, icon));
    }

    private IconBytes resolve(String key, Optional<IconCacheEntry> existing, HttpResult result) {
        if (result.status() == HttpStatus.SUCCESS && validPng(result.contentType(), result.body())) {
            IconCacheEntry entry = new IconCacheEntry(result.body(), result.validators(), clock.instant());
            bestEffortSave(key, entry);
            return new IconBytes(entry.png(), IconSource.NETWORK);
        }
        if (existing.isPresent()) {
            IconCacheEntry cached = existing.orElseThrow();
            IconCacheEntry touched = new IconCacheEntry(cached.png(), cached.validators(), clock.instant());
            bestEffortSave(key, touched);
            return new IconBytes(touched.png(), IconSource.DISK_CACHE);
        }
        return new IconBytes(fallback, IconSource.BUNDLED_FALLBACK);
    }

    private void bestEffortSave(String key, IconCacheEntry entry) {
        try {
            cache.save(key, entry);
        } catch (CancellationException cancellation) {
            throw cancellation;
        } catch (RuntimeException ignoredDiskFailure) {
            // Network and last-good bytes remain usable when the optional disk cache is unavailable.
        }
    }

    static String validateKey(String key) {
        Objects.requireNonNull(key, "registeredKey");
        if (key.isBlank() || key.equals(".") || key.equals("..")) throw new IllegalArgumentException("unsafe icon key");
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isISOControl(c) || "/\\?#%:".indexOf(c) >= 0) {
                throw new IllegalArgumentException("unsafe icon key");
            }
        }
        return key;
    }

    private static String encodeSegment(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || c == '-' || c == '_' || c == '~') {
                encoded.append((char) c);
            } else {
                encoded.append('%').append(String.format(Locale.ROOT, "%02X", c));
            }
        }
        return encoded.toString();
    }

    private static boolean validPng(String contentType, byte[] body) {
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim().equals("image/png")
                || body.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) if (body[i] != PNG_SIGNATURE[i]) return false;
        int offset = PNG_SIGNATURE.length;
        boolean ihdr = false;
        boolean idat = false;
        boolean iend = false;
        int declaredWidth = 0;
        int declaredHeight = 0;
        while (offset <= body.length - 12) {
            long lengthLong = unsignedInt(body, offset);
            if (lengthLong > Integer.MAX_VALUE) return false;
            int length = (int) lengthLong;
            long endLong = (long) offset + 12 + length;
            if (endLong > body.length) return false;
            int dataStart = offset + 8;
            String type = new String(body, offset + 4, 4, StandardCharsets.US_ASCII);
            CRC32 crc = new CRC32();
            crc.update(body, offset + 4, length + 4);
            if (crc.getValue() != unsignedInt(body, dataStart + length)) return false;
            if (!ihdr) {
                if (!type.equals("IHDR") || length != 13) return false;
                long width = unsignedInt(body, dataStart);
                long height = unsignedInt(body, dataStart + 4);
                if (width < 1 || height < 1 || width > 1024 || height > 1024 || width * height > 1_048_576L) {
                    return false;
                }
                declaredWidth = (int) width;
                declaredHeight = (int) height;
                ihdr = true;
            } else if (type.equals("IHDR")) {
                return false;
            }
            if (type.equals("IDAT")) idat = true;
            if (type.equals("IEND")) {
                if (length != 0 || endLong != body.length) return false;
                iend = true;
                break;
            }
            offset = (int) endLong;
        }
        if (!(ihdr && idat && iend)) return false;
        try {
            var image = ImageIO.read(new ByteArrayInputStream(body));
            return image != null && image.getWidth() == declaredWidth && image.getHeight() == declaredHeight;
        } catch (IOException | RuntimeException invalidImageData) {
            return false;
        }
    }

    private static long unsignedInt(byte[] bytes, int offset) {
        return ((long) bytes[offset] & 0xff) << 24
                | ((long) bytes[offset + 1] & 0xff) << 16
                | ((long) bytes[offset + 2] & 0xff) << 8
                | ((long) bytes[offset + 3] & 0xff);
    }

    private static URI validateBase(URI base) {
        Objects.requireNonNull(base, "baseUrl");
        if (base.getHost() == null || !("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || base.getQuery() != null || base.getFragment() != null || base.getUserInfo() != null) {
            throw new IllegalArgumentException("unsafe map base URL");
        }
        return base;
    }

    private static String trimSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static byte[] loadFallback() {
        try (InputStream stream = IconResourceLoader.class.getResourceAsStream("/assets/simmc_tool_set/generic-fallback.png")) {
            if (stream == null) throw new IllegalStateException("bundled fallback icon missing");
            byte[] decoded = stream.readAllBytes();
            if (!validPng("image/png", decoded)) throw new IllegalStateException("bundled fallback icon invalid");
            return decoded;
        } catch (IOException exception) {
            throw new IllegalStateException("bundled fallback icon unreadable", exception);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        active.forEach(future -> future.cancel(true));
        active.clear();
        workExecutor.shutdownNow();
    }
}
