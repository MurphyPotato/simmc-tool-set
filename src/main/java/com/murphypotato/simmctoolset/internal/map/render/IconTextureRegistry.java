package com.murphypotato.simmctoolset.internal.map.render;

import com.murphypotato.simmctoolset.internal.map.network.IconBytes;
import com.murphypotato.simmctoolset.internal.map.network.IconSource;
import com.murphypotato.simmctoolset.internal.map.network.RegisteredIconLoader;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded icon queue whose upload and destruction methods are called on the client thread. */
public final class IconTextureRegistry implements WorldMapIconRegistry {
    private enum Status { REQUESTED, QUEUED, READY, FAILED }
    private record Pending(String key, IconBytes icon) { }

    private final int queueCapacity;
    private final IconTextureBackend backend;
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final Map<String, Status> status = new ConcurrentHashMap<>();
    private final Map<String, Object> textures = new HashMap<>();
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean closed;

    public IconTextureRegistry(int queueCapacity, IconTextureBackend backend) {
        if (queueCapacity < 1) throw new IllegalArgumentException("queueCapacity must be positive");
        this.queueCapacity = queueCapacity;
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Override public void request(String registeredKey, RegisteredIconLoader loader) {
        Objects.requireNonNull(registeredKey, "registeredKey");
        Objects.requireNonNull(loader, "loader");
        if (closed) return;
        AtomicBoolean start = new AtomicBoolean();
        status.compute(registeredKey, (ignored, current) -> {
            if (current == null || current == Status.FAILED) {
                start.set(true);
                return Status.REQUESTED;
            }
            return current;
        });
        if (!start.get()) return;
        long requestGeneration = generation.get();
        try {
            loader.loadAndEnqueue(registeredKey,
                    (loadedKey, icon) -> enqueue(requestGeneration, loadedKey, icon))
                    .whenComplete((accepted, failure) -> {
                if (generation.get() == requestGeneration
                        && (failure != null || !Boolean.TRUE.equals(accepted))) {
                    status.computeIfPresent(registeredKey,
                            (ignored, current) -> current == Status.REQUESTED ? Status.FAILED : current);
                }
            });
        } catch (RuntimeException failure) {
            status.replace(registeredKey, Status.REQUESTED, Status.FAILED);
        }
    }

    @Override public boolean enqueue(String registeredKey, IconBytes icon) {
        return enqueue(generation.get(), registeredKey, icon);
    }

    private boolean enqueue(long requestGeneration, String registeredKey, IconBytes icon) {
        Objects.requireNonNull(registeredKey, "registeredKey");
        Objects.requireNonNull(icon, "icon");
        if (closed || generation.get() != requestGeneration) return false;
        if (icon.source() == IconSource.BUNDLED_FALLBACK) {
            status.put(registeredKey, Status.FAILED);
            return true;
        }
        synchronized (pending) {
            if (closed || generation.get() != requestGeneration) return false;
            if (pending.size() >= queueCapacity) {
                status.put(registeredKey, Status.FAILED);
                return false;
            }
            pending.addLast(new Pending(registeredKey, icon));
            status.put(registeredKey, Status.QUEUED);
            return true;
        }
    }

    @Override public int drainUploads(int maxUploads) {
        if (maxUploads < 0) throw new IllegalArgumentException("maxUploads must be non-negative");
        if (closed) return 0;
        int uploaded = 0;
        while (uploaded < maxUploads) {
            Pending next;
            synchronized (pending) { next = pending.pollFirst(); }
            if (next == null) break;
            try {
                Object handle = backend.upload(next.key(), next.icon());
                if (handle == null) throw new IllegalStateException("icon backend returned null");
                Object replaced = textures.put(next.key(), handle);
                if (replaced != null) backend.destroy(replaced);
                status.put(next.key(), Status.READY);
                uploaded++;
            } catch (Exception failure) {
                status.put(next.key(), Status.FAILED);
            }
        }
        return uploaded;
    }

    @Override public Optional<Object> resolve(String registeredKey) {
        if (status.get(registeredKey) != Status.READY) return Optional.empty();
        return Optional.ofNullable(textures.get(registeredKey));
    }

    public int pendingUploadCount() {
        synchronized (pending) { return pending.size(); }
    }

    @Override public void invalidate() {
        generation.incrementAndGet();
        clearResources();
    }

    private void clearResources() {
        synchronized (pending) { pending.clear(); }
        status.clear();
        java.util.List<Object> handles = java.util.List.copyOf(textures.values());
        textures.clear();
        RuntimeException failure = null;
        for (Object handle : handles) {
            try { backend.destroy(handle); }
            catch (RuntimeException destroyFailure) {
                if (failure == null) failure = destroyFailure;
                else failure.addSuppressed(destroyFailure);
            }
        }
        if (failure != null) throw failure;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        generation.incrementAndGet();
        clearResources();
    }
}
