/*
 * Derived from JR1258/EarthMC-Map-Addon, Apache-2.0.
 * Upstream: src/main/java/net/townymap/render/SquaremapTileRenderer.java
 * Commit: c85c5003855eb47868868b931624b951cffba74e
 * Modified for SIMMC: dynamic squaremap settings, bounded pure selection, generic profiles,
 * disk-first loading, testable platform adapters, and removal of EarthMC/Towny/fixed-host behavior.
 */
package com.murphypotato.simmctoolset.internal.map.render;

import com.murphypotato.simmctoolset.internal.map.cache.TileDiskCache;
import com.murphypotato.simmctoolset.internal.map.cache.TileCacheEntry;
import com.murphypotato.simmctoolset.internal.map.model.SquaremapWorldSettings;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class SquaremapTileRenderer implements WorldMapTileSource {
    private static final int UNSET_ZOOM = -1;
    private static final int MAX_VISIBLE_TILES = 128;
    private static final int OVERVIEW_PIN_ZOOM = 2;
    private static final long COMPLETED_BACKPRESSURE_COOLDOWN_MILLIS = 1_000;

    private final TileDiskCache diskCache;
    private final TileSource source;
    private final TileDecoder decoder;
    private final TextureBackend textureBackend;
    private final ExecutorService executor;
    private final boolean ownsExecutor;
    private final PinPersistence pinPersistence;
    private final LongSupplier clock;
    private final TileRendererLimits limits;
    private final TileRefreshPolicy refreshPolicy;
    private final Thread clientThread;
    private final Set<TileKey> inflight = ConcurrentHashMap.newKeySet();
    private final Set<TileKey> missingInflight = ConcurrentHashMap.newKeySet();
    private final Map<TileKey, ResidentMetadata> residentMetadata = new ConcurrentHashMap<>();
    private final Map<TileKey, RefreshFailure> refreshFailures = new ConcurrentHashMap<>();
    private final Object refreshLock = new Object();
    private final ArrayDeque<RevalidationRequest> pendingRevalidations = new ArrayDeque<>();
    private ManualSweep manualSweep;
    private final Map<TileKey, Long> failedAt = new ConcurrentHashMap<>();
    private final Map<TileKey, Long> retryNotBefore = new ConcurrentHashMap<>();
    private final ArrayDeque<CompletedTile> completed = new ArrayDeque<>();
    private final LinkedHashMap<TileKey, Object> gpuTextures = new LinkedHashMap<>(64, 0.75f, true);
    private final Set<TileKey> explicitlyPinned = new LinkedHashSet<>();
    private final Set<TileKey> protectedGpuKeys = new LinkedHashSet<>();
    private int failedDestroySlots;
    private int runningRevalidations;
    private int requestedZoom = UNSET_ZOOM;
    private int displayZoom = UNSET_ZOOM;
    private List<TileKey> displayTiles = List.of();
    private Stage stage;
    private long stageToken;
    private ViewportSignature viewportSignature;
    private List<TileKey> currentVisible = List.of();
    private volatile long viewportGeneration;
    private long idleSinceMillis;
    private volatile String profileId;
    private volatile SquaremapWorldSettings settings;
    private volatile long generation;
    private volatile boolean closed;

    public SquaremapTileRenderer(String profileId, SquaremapWorldSettings settings,
                                 TileDiskCache diskCache, TileSource source, TileDecoder decoder,
                                 TextureBackend textureBackend, ExecutorService executor,
                                 boolean ownsExecutor, LongSupplier clock, TileRendererLimits limits) {
        this(profileId, settings, diskCache, source, decoder, textureBackend, executor, ownsExecutor,
                newPinPersistenceExecutor("simmc-tile-pin-persistence"), clock, limits,
                TileRefreshPolicy.standard(TileRefreshPolicy.DEFAULT_TTL_MILLIS));
    }

    public SquaremapTileRenderer(String profileId, SquaremapWorldSettings settings,
                                 TileDiskCache diskCache, TileSource source, TileDecoder decoder,
                                 TextureBackend textureBackend, ExecutorService executor,
                                 boolean ownsExecutor, LongSupplier clock, TileRendererLimits limits,
                                 TileRefreshPolicy refreshPolicy) {
        this(profileId, settings, diskCache, source, decoder, textureBackend, executor, ownsExecutor,
                newPinPersistenceExecutor("simmc-tile-pin-persistence"), clock, limits, refreshPolicy);
    }

    SquaremapTileRenderer(String profileId, SquaremapWorldSettings settings,
                          TileDiskCache diskCache, TileSource source, TileDecoder decoder,
                          TextureBackend textureBackend, ExecutorService executor,
                          boolean ownsExecutor, ExecutorService pinPersistenceExecutor,
                          LongSupplier clock, TileRendererLimits limits) {
        this(profileId, settings, diskCache, source, decoder, textureBackend, executor, ownsExecutor,
                pinPersistenceExecutor, newPinPersistenceExecutor("simmc-tile-pin-fallback"), clock, limits,
                TileRefreshPolicy.standard(TileRefreshPolicy.DEFAULT_TTL_MILLIS));
    }

    SquaremapTileRenderer(String profileId, SquaremapWorldSettings settings,
                          TileDiskCache diskCache, TileSource source, TileDecoder decoder,
                          TextureBackend textureBackend, ExecutorService executor,
                          boolean ownsExecutor, ExecutorService pinPersistenceExecutor,
                          LongSupplier clock, TileRendererLimits limits, TileRefreshPolicy refreshPolicy) {
        this(profileId, settings, diskCache, source, decoder, textureBackend, executor, ownsExecutor,
                pinPersistenceExecutor, newPinPersistenceExecutor("simmc-tile-pin-fallback"), clock, limits,
                refreshPolicy);
    }

    SquaremapTileRenderer(String profileId, SquaremapWorldSettings settings,
                          TileDiskCache diskCache, TileSource source, TileDecoder decoder,
                          TextureBackend textureBackend, ExecutorService executor,
                          boolean ownsExecutor, ExecutorService pinPersistenceExecutor,
                          ExecutorService pinFallbackExecutor,
                          LongSupplier clock, TileRendererLimits limits) {
        this(profileId, settings, diskCache, source, decoder, textureBackend, executor, ownsExecutor,
                pinPersistenceExecutor, pinFallbackExecutor, clock, limits,
                TileRefreshPolicy.standard(TileRefreshPolicy.DEFAULT_TTL_MILLIS));
    }

    SquaremapTileRenderer(String profileId, SquaremapWorldSettings settings,
                          TileDiskCache diskCache, TileSource source, TileDecoder decoder,
                          TextureBackend textureBackend, ExecutorService executor,
                          boolean ownsExecutor, ExecutorService pinPersistenceExecutor,
                          ExecutorService pinFallbackExecutor,
                          LongSupplier clock, TileRendererLimits limits, TileRefreshPolicy refreshPolicy) {
        this.profileId = Objects.requireNonNull(profileId, "profileId");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.diskCache = Objects.requireNonNull(diskCache, "diskCache");
        this.source = Objects.requireNonNull(source, "source");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.textureBackend = Objects.requireNonNull(textureBackend, "textureBackend");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.ownsExecutor = ownsExecutor;
        this.pinPersistence = new PinPersistence(diskCache,
                Objects.requireNonNull(pinPersistenceExecutor, "pinPersistenceExecutor"),
                Objects.requireNonNull(pinFallbackExecutor, "pinFallbackExecutor"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.refreshPolicy = Objects.requireNonNull(refreshPolicy, "refreshPolicy");
        this.clientThread = Thread.currentThread();
    }

    public void requestTiles(List<TileKey> centerOutKeys, RequestContext context, boolean prefetch) {
        ensureOpen();
        Objects.requireNonNull(centerOutKeys, "centerOutKeys");
        Objects.requireNonNull(context, "context");
        if (prefetch && !context.prefetchAdjacentZoom()) return;
        int requestBudget = prefetch ? Math.max(1, context.visibleBudget() / 4) : context.visibleBudget();
        requestTiles(centerOutKeys, context, prefetch, requestBudget);
    }

    private int requestTiles(List<TileKey> centerOutKeys, RequestContext context,
                             boolean prefetch, int requestBudget) {
        if (requestBudget <= 0 || (prefetch && !context.prefetchAdjacentZoom())) return 0;
        int concurrency = Math.min(limits.maxConcurrentLoads(), context.concurrentLoads());
        int accepted = 0;
        for (TileKey key : centerOutKeys) {
            if (accepted >= requestBudget) break;
            if (!belongsToCurrentNamespace(key) || key.zoom() > settings.maxZoom()) continue;
            synchronized (completed) {
                if (containsCompleted(key)) continue;
            }
            synchronized (gpuTextures) {
                if (gpuTextures.containsKey(key)) continue;
            }
            Long failure = failedAt.get(key);
            if (failure != null && elapsed(clock.getAsLong(), failure) < limits.failureCooldownMillis()) continue;
            Long retryAt = retryNotBefore.get(key);
            long now = clock.getAsLong();
            if (retryAt != null && now < retryAt) continue;
            if (retryAt != null) retryNotBefore.remove(key, retryAt);
            boolean capacityReached;
            synchronized (refreshLock) {
                capacityReached = closed || missingInflight.size() >= concurrency
                        || missingInflight.size() + runningRevalidations >= limits.maxConcurrentLoads();
                if (!capacityReached) {
                    if (!belongsToCurrentNamespace(key)) continue;
                    if (!inflight.add(key)) continue;
                    missingInflight.add(key);
                }
            }
            if (capacityReached) break;
            long requestGeneration = generation;
            try {
                int expectedTileSize = settings.tileSize();
                executor.execute(() -> load(key, requestGeneration, expectedTileSize));
                accepted++;
            } catch (RuntimeException rejected) {
                synchronized (refreshLock) {
                    missingInflight.remove(key);
                    inflight.remove(key);
                }
                if (!closed) throw rejected;
            }
        }
        return accepted;
    }

    public List<TileKey> requestViewport(WorldViewport viewport, double worldScale, RequestContext context) {
        requireClientThread();
        ensureOpen();
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(context, "context");
        SquaremapWorldSettings current = settings;
        requestedZoom = requestedZoom == UNSET_ZOOM
                ? TileZoomSelector.initial(current, worldScale)
                : TileZoomSelector.stabilize(current, worldScale, requestedZoom);
        if (displayZoom == UNSET_ZOOM) displayZoom = requestedZoom;
        displayTiles = visibleTiles(profileId, current, displayZoom, viewport, MAX_VISIBLE_TILES);
        boolean displayExact = allExactGpuResident(displayTiles);
        refreshDisplayProtection(displayTiles);

        if (requestedZoom == displayZoom) {
            stage = null;
        } else if (!displayExact) {
            stage = null;
        } else {
            List<TileKey> targetTiles = visibleTiles(
                    profileId, current, requestedZoom, viewport, MAX_VISIBLE_TILES);
            if (!stageCapacityFeasible(displayTiles, targetTiles)) {
                stage = null;
            } else {
                if (stage == null || stage.targetZoom() != requestedZoom
                        || !stage.targetTiles().equals(targetTiles)) {
                    stage = Stage.create(++stageToken, requestedZoom, targetTiles);
                }
                Stage currentStage = stage;
                if (allExactGpuResident(currentStage.targetTiles())) {
                    displayZoom = currentStage.targetZoom();
                    displayTiles = currentStage.targetTiles();
                    stage = null;
                    displayExact = true;
                    refreshDisplayProtection(displayTiles);
                }
            }
        }

        observeViewport(viewport, worldScale, displayZoom, displayTiles, context);
        int remainingBudget = context.visibleBudget();
        remainingBudget -= requestTiles(displayTiles, context, false, remainingBudget);
        if (stage != null && displayExact && remainingBudget > 0) {
            remainingBudget -= requestTiles(stage.targetTiles(), context, false, remainingBudget);
        }
        if (requestedZoom == displayZoom && stage == null && displayExact && remainingBudget > 0) {
            int prefetchBudget = Math.min(remainingBudget, Math.max(1, context.visibleBudget() / 4));
            requestTiles(adjacentZoomTiles(displayTiles, current, context), context, true, prefetchBudget);
        }
        if (!isMoving(context) && elapsed(clock.getAsLong(), idleSinceMillis) >= refreshPolicy.idleDebounceMillis()) {
            scheduleRevalidations(displayTiles, false);
        }
        return displayTiles;
    }

    @Override public void revalidateCurrentViewport() {
        ensureOpen();
        if (viewportSignature == null) return;
        synchronized (refreshLock) {
            if (manualSweep != null && manualSweep.namespaceGeneration == generation
                    && manualSweep.viewportGeneration == viewportGeneration) return;
            manualSweep = new ManualSweep(List.copyOf(currentVisible), generation, viewportGeneration);
        }
        pumpRevalidations();
    }

    public int drainUploads(int maxUploads) {
        requireClientThread();
        if (maxUploads < 0) throw new IllegalArgumentException("maxUploads must be non-negative");
        int uploaded = 0;
        while (uploaded < maxUploads) {
            CompletedTile tile;
            synchronized (completed) { tile = completed.pollFirst(); }
            if (tile == null) break;
            try {
                if (closed || tile.namespaceGeneration() != generation
                        || (tile.viewportGeneration() >= 0 && tile.viewportGeneration() != viewportGeneration)
                        || !belongsToCurrentNamespace(tile.key())) continue;
                Object previous;
                synchronized (gpuTextures) { previous = gpuTextures.get(tile.key()); }
                if (previous == null && !makeGpuSpaceForNew()) {
                    failedAt.put(tile.key(), clock.getAsLong());
                    continue;
                }
                Object handle = textureBackend.upload(tile.key(), tile.image());
                if (handle == null) throw new IllegalStateException("texture backend returned null");
                synchronized (gpuTextures) {
                    previous = gpuTextures.put(tile.key(), handle);
                }
                if (previous != null && !retireReplacedHandle(tile.key(), previous, handle)) {
                    failedAt.put(tile.key(), clock.getAsLong());
                    continue;
                }
                failedAt.remove(tile.key());
                retryNotBefore.remove(tile.key());
                uploaded++;
            } catch (Exception failure) {
                failedAt.put(tile.key(), clock.getAsLong());
            } finally {
                tile.image().close();
            }
        }
        return uploaded;
    }

    public int drainDestructions() {
        requireClientThread();
        return 0;
    }

    /** Pure render lookup: never performs disk or network I/O. Access updates GPU LRU order. */
    public Optional<Object> gpuHandle(TileKey key) {
        requireClientThread();
        synchronized (gpuTextures) { return Optional.ofNullable(gpuTextures.get(key)); }
    }

    public Optional<GpuTileRegion> gpuHandleOrParent(TileKey key) {
        requireClientThread();
        Optional<Object> exact = gpuHandle(key);
        if (exact.isPresent()) return Optional.of(GpuTileRegion.full(key, exact.orElseThrow()));
        for (int zoom = key.zoom() - 1; zoom >= settings.minZoom(); zoom--) {
            ParentFallback fallback = parentFallback(key, zoom, settings.tileSize());
            Optional<Object> handle = gpuHandle(fallback.key());
            if (handle.isPresent()) return Optional.of(new GpuTileRegion(fallback.key(), handle.orElseThrow(),
                    fallback.u0(), fallback.v0(), fallback.u1(), fallback.v1()));
        }
        return Optional.empty();
    }

    public int inflightCount() { return inflight.size(); }
    public int pendingUploadCount() { synchronized (completed) { return completed.size(); } }

    public void pin(Collection<TileKey> keys) {
        requireClientThread();
        ensureOpen();
        Set<TileKey> safeKeys = Set.copyOf(keys);
        explicitlyPinned.addAll(safeKeys);
        diskCache.pinInMemory(safeKeys);
        pinPersistence.request();
    }

    public void updateNamespace(String profileId, SquaremapWorldSettings settings) {
        requireClientThread();
        ensureOpen();
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(settings, "settings");
        List<DecodedTile> staleImages;
        synchronized (completed) {
            if (this.profileId.equals(profileId)
                    && this.settings.worldKey().equals(settings.worldKey())
                    && this.settings.signature().equals(settings.signature())) return;
            generation++;
            viewportGeneration++;
            this.profileId = profileId;
            this.settings = settings;
            failedAt.clear();
            retryNotBefore.clear();
            residentMetadata.clear();
            refreshFailures.clear();
            clearViewportState();
            clearTransitionState(false);
            cancelPendingRevalidations();
            diskCache.clearPinsInMemory();
            staleImages = detachPendingUploads();
        }
        pinPersistence.request();
        staleImages.forEach(DecodedTile::close);
        destroyGpuHandles(detachGpuHandles());
        explicitlyPinned.clear();
    }

    public void invalidateGpuResources() {
        requireClientThread();
        ensureOpen();
        clearTransitionState(true);
        destroyGpuHandles(detachGpuHandles());
    }

    public boolean awaitExecutorTermination(long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit, "unit");
        if (timeout < 0) throw new IllegalArgumentException("timeout must be non-negative");
        long timeoutNanos = unit.toNanos(timeout);
        long started = System.nanoTime();
        if (ownsExecutor && !executor.awaitTermination(timeoutNanos, TimeUnit.NANOSECONDS)) return false;
        long elapsed = System.nanoTime() - started;
        return pinPersistence.awaitTermination(Math.max(0, timeoutNanos - elapsed), TimeUnit.NANOSECONDS);
    }

    private void load(TileKey key, long requestGeneration, int expectedTileSize) {
        try {
            if (closed || requestGeneration != generation) return;
            if (key.zoom() <= OVERVIEW_PIN_ZOOM && !pinIfCurrent(key, requestGeneration)) return;
            TileCacheEntry cached = diskCache.getEntry(key).orElse(null);
            byte[] bytes = cached == null ? null : cached.png();
            DecodedTile image = null;
            if (bytes != null) {
                try { image = decoder.decode(bytes, expectedTileSize); }
                catch (TileDecodeException invalidDiskEntry) { diskCache.remove(key); }
            }
            if (image == null) {
                TileFetchResult result = source.fetch(key);
                if (result == null || result.status() != TileFetchResult.Status.FOUND) {
                    recordMissingFailure(key, requestGeneration, clock.getAsLong());
                    return;
                }
                bytes = result.bytes();
                image = decoder.decode(bytes, expectedTileSize);
                if (closed || requestGeneration != generation || !belongsToCurrentNamespace(key)) {
                    image.close();
                    return;
                }
                cached = new TileCacheEntry(bytes, result.validators(), clock.getAsLong());
                try { diskCache.putEntry(key, cached); }
                catch (RuntimeException cacheUnavailable) { /* memory/GPU path remains usable */ }
            }
            publishLoaded(key, cached, image, requestGeneration);
        } catch (Exception failure) {
            recordMissingFailure(key, requestGeneration, clock.getAsLong());
        } finally {
            synchronized (refreshLock) {
                missingInflight.remove(key);
                inflight.remove(key);
            }
            pumpRevalidations();
        }
    }

    private void observeViewport(WorldViewport viewport, double worldScale, int zoom,
                                 List<TileKey> visible, RequestContext context) {
        long now = clock.getAsLong();
        ViewportSignature signature = new ViewportSignature(generation, zoom,
                Double.doubleToLongBits(viewport.left()), Double.doubleToLongBits(viewport.top()),
                Double.doubleToLongBits(viewport.right()), Double.doubleToLongBits(viewport.bottom()),
                Double.doubleToLongBits(viewport.centerX()), Double.doubleToLongBits(viewport.centerZ()),
                Double.doubleToLongBits(worldScale));
        List<DecodedTile> staleRefreshImages = List.of();
        if (!signature.equals(viewportSignature) || isMoving(context)) {
            synchronized (completed) {
                viewportSignature = signature;
                viewportGeneration++;
                idleSinceMillis = now;
                staleRefreshImages = detachStaleViewportUploads();
            }
            cancelPendingRevalidations();
        }
        currentVisible = List.copyOf(visible);
        staleRefreshImages.forEach(DecodedTile::close);
    }

    private void scheduleRevalidations(List<TileKey> visible, boolean force) {
        long now = clock.getAsLong();
        long requestViewportGeneration = viewportGeneration;
        long requestNamespaceGeneration = generation;
        int accepted = 0;
        synchronized (refreshLock) {
            if (!missingInflight.isEmpty()) return;
            for (TileKey key : visible) {
                if (accepted >= refreshPolicy.maxAcceptedPerFrame()) break;
                ResidentMetadata metadata = residentMetadata.get(key);
                if (metadata == null || (!force && !isStale(now, metadata.lastCheckedMillis()))) continue;
                RefreshFailure failure = refreshFailures.get(key);
                if (failure != null && now < failure.retryNotBeforeMillis()) continue;
                if (!inflight.add(key)) continue;
                pendingRevalidations.addLast(new RevalidationRequest(key, metadata,
                        requestNamespaceGeneration, requestViewportGeneration));
                accepted++;
            }
        }
        pumpRevalidations();
    }

    private boolean isStale(long now, long checked) {
        return checked == 0 || now < checked || now - checked >= refreshPolicy.ttlMillis();
    }

    private void pumpRevalidations() {
        while (true) {
            RevalidationRequest request;
            synchronized (refreshLock) {
                if (pendingRevalidations.isEmpty() && missingInflight.isEmpty()) feedManualSweepLocked();
                if (closed || !missingInflight.isEmpty()
                        || runningRevalidations >= refreshPolicy.maxConcurrentRevalidations()
                        || missingInflight.size() + runningRevalidations >= limits.maxConcurrentLoads()) return;
                request = pendingRevalidations.pollFirst();
                if (request == null) return;
                runningRevalidations++;
            }
            try {
                RevalidationRequest submitted = request;
                executor.execute(() -> revalidate(submitted));
            } catch (RuntimeException rejected) {
                synchronized (refreshLock) {
                    runningRevalidations--;
                    inflight.remove(request.key());
                }
                if (!closed) throw rejected;
            }
        }
    }

    private void revalidate(RevalidationRequest request) {
        try {
            if (!isCurrent(request)) return;
            TileFetchResult result = source.revalidate(request.key(), request.metadata().validators());
            if (!isCurrent(request) || result == null) return;
            long now = clock.getAsLong();
            if (result.status() == TileFetchResult.Status.NOT_MODIFIED) {
                TileValidators merged = mergeValidators(result.validators(), request.metadata().validators());
                if (!diskCache.markValidated(request.key(), merged, now)) {
                    recordRefreshFailure(request, now);
                    return;
                }
                publishValidated(request, merged, now);
                return;
            }
            if (result.status() != TileFetchResult.Status.FOUND) {
                recordRefreshFailure(request, now);
                return;
            }
            byte[] bytes = result.bytes();
            DecodedTile image = decoder.decode(bytes, settings.tileSize());
            if (!isCurrent(request)) {
                image.close();
                return;
            }
            TileCacheEntry updated = new TileCacheEntry(bytes, result.validators(), now);
            try {
                if (!diskCache.putEntry(request.key(), updated)) {
                    image.close();
                    recordRefreshFailure(request, now);
                    return;
                }
            } catch (RuntimeException publicationFailure) {
                image.close();
                recordRefreshFailure(request, now);
                return;
            }
            publishRevalidated(request, updated, image);
        } catch (Exception failure) {
            recordRefreshFailure(request, clock.getAsLong());
        } finally {
            synchronized (refreshLock) {
                inflight.remove(request.key());
                runningRevalidations--;
            }
            pumpRevalidations();
        }
    }

    private boolean isCurrent(RevalidationRequest request) {
        return !closed && request.namespaceGeneration() == generation
                && request.viewportGeneration() == viewportGeneration
                && belongsToCurrentNamespace(request.key());
    }

    private void recordMissingFailure(TileKey key, long requestGeneration, long now) {
        synchronized (completed) {
            if (!closed && requestGeneration == generation && belongsToCurrentNamespace(key)) {
                failedAt.put(key, now);
            }
        }
    }

    private void publishLoaded(TileKey key, TileCacheEntry cached, DecodedTile image,
                               long requestGeneration) {
        synchronized (completed) {
            if (closed || requestGeneration != generation || !belongsToCurrentNamespace(key)) {
                image.close();
                return;
            }
            residentMetadata.put(key, ResidentMetadata.from(cached));
            offerCompleted(new CompletedTile(key, image, requestGeneration, -1));
        }
    }

    private void publishValidated(RevalidationRequest request, TileValidators validators, long now) {
        synchronized (completed) {
            if (!isCurrent(request)) return;
            residentMetadata.put(request.key(), new ResidentMetadata(validators, now));
            refreshFailures.remove(request.key());
        }
    }

    private void publishRevalidated(RevalidationRequest request, TileCacheEntry updated,
                                    DecodedTile image) {
        synchronized (completed) {
            if (!isCurrent(request)) {
                image.close();
                return;
            }
            residentMetadata.put(request.key(), ResidentMetadata.from(updated));
            refreshFailures.remove(request.key());
            offerCompleted(new CompletedTile(request.key(), image,
                    request.namespaceGeneration(), request.viewportGeneration()));
        }
    }

    private static TileValidators mergeValidators(TileValidators response, TileValidators stored) {
        return new TileValidators(response.etag() == null ? stored.etag() : response.etag(),
                response.lastModified() == null ? stored.lastModified() : response.lastModified());
    }

    private void recordRefreshFailure(RevalidationRequest request, long now) {
        synchronized (completed) {
            if (!isCurrent(request)) return;
            refreshFailures.compute(request.key(), (ignored, previous) -> {
                int failures = previous == null ? 1 : Math.min(63, previous.failures() + 1);
                long delay = refreshPolicy.backoffBaseMillis();
                for (int i = 1; i < failures && delay < refreshPolicy.backoffCapMillis(); i++) {
                    delay = Math.min(refreshPolicy.backoffCapMillis(), saturatedAdd(delay, delay));
                }
                return new RefreshFailure(failures, saturatedAdd(now, delay));
            });
        }
    }

    private void cancelPendingRevalidations() {
        synchronized (refreshLock) {
            manualSweep = null;
            RevalidationRequest request;
            while ((request = pendingRevalidations.pollFirst()) != null) inflight.remove(request.key());
        }
    }

    private void feedManualSweepLocked() {
        if (manualSweep == null || closed || !missingInflight.isEmpty()) return;
        if (manualSweep.namespaceGeneration != generation
                || manualSweep.viewportGeneration != viewportGeneration) {
            manualSweep = null;
            return;
        }
        long now = clock.getAsLong();
        int accepted = 0;
        while (manualSweep.cursor < manualSweep.keys.size()
                && accepted < refreshPolicy.maxAcceptedPerFrame()) {
            TileKey key = manualSweep.keys.get(manualSweep.cursor++);
            ResidentMetadata metadata = residentMetadata.get(key);
            if (metadata == null) continue;
            RefreshFailure failure = refreshFailures.get(key);
            if (failure != null && now < failure.retryNotBeforeMillis()) continue;
            if (!inflight.add(key)) continue;
            pendingRevalidations.addLast(new RevalidationRequest(key, metadata,
                    manualSweep.namespaceGeneration, manualSweep.viewportGeneration));
            accepted++;
        }
        if (manualSweep.cursor == manualSweep.keys.size()) manualSweep = null;
    }

    private static boolean isMoving(RequestContext context) {
        return context == RequestContext.WORLD_MAP_MOVING || context == RequestContext.MINIMAP_MOVING;
    }

    private void offerCompleted(CompletedTile tile) {
        boolean accepted = false;
        synchronized (completed) {
            if (!closed && tile.namespaceGeneration() == generation
                    && (tile.viewportGeneration() < 0 || tile.viewportGeneration() == viewportGeneration)
                    && belongsToCurrentNamespace(tile.key())
                    && !containsCompleted(tile.key())) {
                if (completed.size() < limits.maxCompletedUploads()) {
                    completed.addLast(tile);
                    accepted = true;
                } else {
                    retryNotBefore.put(tile.key(), saturatedAdd(
                            clock.getAsLong(), COMPLETED_BACKPRESSURE_COOLDOWN_MILLIS));
                }
            }
        }
        if (!accepted) tile.image().close();
    }

    private boolean containsCompleted(TileKey key) {
        for (CompletedTile tile : completed) if (tile.key().equals(key)) return true;
        return false;
    }

    private boolean allExactGpuResident(List<TileKey> keys) {
        synchronized (gpuTextures) {
            for (TileKey key : keys) if (!gpuTextures.containsKey(key)) return false;
            return true;
        }
    }

    private boolean stageCapacityFeasible(List<TileKey> exactDisplay, List<TileKey> target) {
        LinkedHashSet<TileKey> required = new LinkedHashSet<>(exactDisplay);
        required.addAll(target);
        long available = (long) limits.maxGpuTextures() - (long) failedDestroySlots;
        return required.size() <= available;
    }

    private void refreshDisplayProtection(List<TileKey> keys) {
        protectedGpuKeys.clear();
        synchronized (gpuTextures) {
            for (TileKey key : keys) {
                if (gpuTextures.get(key) != null) {
                    protectedGpuKeys.add(key);
                    continue;
                }
                for (int zoom = key.zoom() - 1; zoom >= settings.minZoom(); zoom--) {
                    TileKey fallback = parentFallback(key, zoom, settings.tileSize()).key();
                    if (gpuTextures.get(fallback) != null) {
                        protectedGpuKeys.add(fallback);
                        break;
                    }
                }
            }
        }
    }

    private boolean makeGpuSpaceForNew() {
        while (true) {
            Object victim;
            synchronized (gpuTextures) {
                long occupied = (long) gpuTextures.size() + (long) failedDestroySlots;
                if (occupied < limits.maxGpuTextures()) return true;
                victim = removeGpuVictim();
            }
            if (victim == null) return false;
            destroyGpuHandle(victim);
        }
    }

    private Object removeGpuVictim() {
        Iterator<Map.Entry<TileKey, Object>> candidates = gpuTextures.entrySet().iterator();
        while (candidates.hasNext()) {
            Map.Entry<TileKey, Object> entry = candidates.next();
            if (!protectedGpuKeys.contains(entry.getKey()) && !isCurrentStageTarget(entry.getKey())
                    && !isPinned(entry.getKey())) {
                Object handle = entry.getValue();
                candidates.remove();
                return handle;
            }
        }
        candidates = gpuTextures.entrySet().iterator();
        while (candidates.hasNext()) {
            Map.Entry<TileKey, Object> entry = candidates.next();
            if (protectedGpuKeys.contains(entry.getKey()) || isCurrentStageTarget(entry.getKey())) continue;
            Object handle = entry.getValue();
            candidates.remove();
            return handle;
        }
        return null;
    }

    private boolean isCurrentStageTarget(TileKey key) {
        Stage currentStage = stage;
        return currentStage != null && currentStage.targetKeys().contains(key);
    }

    private List<Object> detachGpuHandles() {
        synchronized (gpuTextures) {
            List<Object> handles = new ArrayList<>(gpuTextures.values());
            gpuTextures.clear();
            return handles;
        }
    }

    private void destroyGpuHandles(List<Object> handles) {
        for (Object handle : handles) destroyGpuHandle(handle);
    }

    private boolean destroyGpuHandle(Object handle) {
        try {
            textureBackend.destroy(handle);
            return true;
        } catch (RuntimeException ignored) {
            recordFailedDestroySlot();
            return false;
        }
    }

    private boolean retireReplacedHandle(TileKey key, Object previous, Object replacement) {
        try {
            textureBackend.destroy(previous);
            return true;
        } catch (RuntimeException oldDestroyFailure) {
            boolean rolledBack;
            synchronized (gpuTextures) {
                rolledBack = gpuTextures.get(key) == replacement;
                if (rolledBack) gpuTextures.put(key, previous);
            }
            if (!rolledBack) {
                recordFailedDestroySlot();
                return false;
            }
            try {
                textureBackend.destroy(replacement);
            } catch (RuntimeException replacementDestroyFailure) {
                // The replacement is no longer reachable from the managed map.
                recordFailedDestroySlot();
            }
            return false;
        }
    }

    private void recordFailedDestroySlot() {
        if (failedDestroySlots < Integer.MAX_VALUE) failedDestroySlots++;
    }

    private boolean isPinned(TileKey key) {
        return key.zoom() <= OVERVIEW_PIN_ZOOM || explicitlyPinned.contains(key);
    }

    private boolean pinIfCurrent(TileKey key, long requestGeneration) {
        synchronized (completed) {
            if (closed || requestGeneration != generation || !belongsToCurrentNamespace(key)) return false;
            diskCache.pinInMemory(Set.of(key));
        }
        pinPersistence.request();
        return true;
    }

    private boolean belongsToCurrentNamespace(TileKey key) {
        SquaremapWorldSettings current = settings;
        return key.profileId().equals(profileId) && key.worldKey().equals(current.worldKey())
                && key.settingsSignature().equals(current.signature());
    }

    private List<DecodedTile> detachPendingUploads() {
        List<DecodedTile> images = new ArrayList<>(completed.size());
        CompletedTile tile;
        while ((tile = completed.pollFirst()) != null) images.add(tile.image());
        return images;
    }

    private List<DecodedTile> detachStaleViewportUploads() {
        List<DecodedTile> images = new ArrayList<>();
        Iterator<CompletedTile> iterator = completed.iterator();
        while (iterator.hasNext()) {
            CompletedTile tile = iterator.next();
            if (tile.viewportGeneration() >= 0 && tile.viewportGeneration() != viewportGeneration) {
                images.add(tile.image());
                iterator.remove();
            }
        }
        return images;
    }

    private void requireClientThread() {
        if (Thread.currentThread() != clientThread) throw new IllegalStateException("GPU work must run on the client thread");
    }

    private void ensureOpen() { if (closed) throw new IllegalStateException("tile renderer is closed"); }

    private void clearViewportState() {
        viewportSignature = null;
        currentVisible = List.of();
        idleSinceMillis = 0;
    }

    private void clearTransitionState(boolean retainRequestedZoom) {
        if (!retainRequestedZoom) requestedZoom = UNSET_ZOOM;
        displayZoom = UNSET_ZOOM;
        displayTiles = List.of();
        stage = null;
        protectedGpuKeys.clear();
    }

    @Override public void close() {
        requireClientThread();
        List<DecodedTile> pendingImages;
        synchronized (completed) {
            if (closed) return;
            closed = true;
            generation++;
            viewportGeneration++;
            pendingImages = detachPendingUploads();
            failedAt.clear();
            retryNotBefore.clear();
            residentMetadata.clear();
            refreshFailures.clear();
            clearViewportState();
            clearTransitionState(false);
            synchronized (refreshLock) {
                cancelPendingRevalidations();
                inflight.clear();
                missingInflight.clear();
            }
            diskCache.clearPinsInMemory();
        }
        pinPersistence.close();
        if (ownsExecutor) executor.shutdownNow();
        pendingImages.forEach(DecodedTile::close);
        destroyGpuHandles(detachGpuHandles());
        explicitlyPinned.clear();
    }

    public static List<TileKey> adjacentZoomParents(List<TileKey> visible,
                                                     SquaremapWorldSettings settings,
                                                     RequestContext context) {
        Objects.requireNonNull(visible, "visible");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(context, "context");
        if (!context.prefetchAdjacentZoom()) return List.of();
        LinkedHashSet<TileKey> parents = new LinkedHashSet<>();
        int limit = Math.max(1, context.visibleBudget() / 4);
        for (TileKey key : visible) {
            if (key.zoom() <= settings.minZoom() || !key.worldKey().equals(settings.worldKey())
                    || !key.settingsSignature().equals(settings.signature())) continue;
            parents.add(parentFallback(key, key.zoom() - 1, settings.tileSize()).key());
            if (parents.size() == limit) break;
        }
        return List.copyOf(parents);
    }

    public static List<TileKey> adjacentZoomTiles(List<TileKey> visible,
                                                   SquaremapWorldSettings settings,
                                                   RequestContext context) {
        Objects.requireNonNull(visible, "visible");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(context, "context");
        if (!context.prefetchAdjacentZoom()) return List.of();
        LinkedHashSet<TileKey> adjacent = new LinkedHashSet<>();
        int limit = Math.max(1, context.visibleBudget() / 2);
        for (TileKey key : visible) {
            if (!key.worldKey().equals(settings.worldKey())
                    || !key.settingsSignature().equals(settings.signature())) continue;
            if (key.zoom() > settings.minZoom()) adjacent.add(parentFallback(key, key.zoom() - 1, settings.tileSize()).key());
            if (key.zoom() < settings.maxZoom()) {
                long baseX = (long) key.x() * 2;
                long baseZ = (long) key.z() * 2;
                if (baseX >= Integer.MIN_VALUE && baseX + 1 <= Integer.MAX_VALUE
                        && baseZ >= Integer.MIN_VALUE && baseZ + 1 <= Integer.MAX_VALUE) {
                    int childZoom = key.zoom() + 1;
                    int x = (int) baseX;
                    int z = (int) baseZ;
                    adjacent.add(new TileKey(key.profileId(), key.worldKey(), key.settingsSignature(), childZoom, x, z));
                    adjacent.add(new TileKey(key.profileId(), key.worldKey(), key.settingsSignature(), childZoom, x + 1, z));
                    adjacent.add(new TileKey(key.profileId(), key.worldKey(), key.settingsSignature(), childZoom, x, z + 1));
                    adjacent.add(new TileKey(key.profileId(), key.worldKey(), key.settingsSignature(), childZoom, x + 1, z + 1));
                }
            }
            if (adjacent.size() >= limit) break;
        }
        return adjacent.stream().limit(limit).toList();
    }

    public static int selectServerZoom(SquaremapWorldSettings settings, double worldScale) {
        return TileZoomSelector.initial(settings, worldScale);
    }

    public static int maxViewZoom(SquaremapWorldSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return Math.addExact(settings.maxZoom(), settings.extraZoom());
    }

    public static double tileWorldSpan(SquaremapWorldSettings settings, int serverZoom) {
        Objects.requireNonNull(settings, "settings");
        checkZoom(settings, serverZoom);
        return Math.scalb((double) settings.tileSize(), settings.maxZoom() - serverZoom);
    }

    public static TileKey tileAt(String profileId, SquaremapWorldSettings settings,
                                 int serverZoom, double worldX, double worldZ) {
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ)) throw new IllegalArgumentException("world position must be finite");
        double span = tileWorldSpan(settings, serverZoom);
        return new TileKey(profileId, settings.worldKey(), settings.signature(), serverZoom,
                floorTile(worldX, span), floorTile(worldZ, span));
    }

    static TileKey tileAt(SquaremapWorldSettings settings, int serverZoom, double worldX, double worldZ) {
        return tileAt("", settings, serverZoom, worldX, worldZ);
    }

    public static List<TileKey> visibleTiles(String profileId, SquaremapWorldSettings settings,
                                             int serverZoom, WorldViewport viewport, int requestedLimit) {
        Objects.requireNonNull(viewport, "viewport");
        if (requestedLimit < 0) throw new IllegalArgumentException("requestedLimit must be non-negative");
        int limit = Math.min(requestedLimit, MAX_VISIBLE_TILES);
        if (limit == 0) return List.of();
        double span = tileWorldSpan(settings, serverZoom);
        int minX = floorTile(viewport.left(), span);
        int maxX = floorTile(viewport.right(), span);
        int minZ = floorTile(viewport.top(), span);
        int maxZ = floorTile(viewport.bottom(), span);
        int centerX = floorTile(viewport.centerX(), span);
        int centerZ = floorTile(viewport.centerZ(), span);
        long maxRadius = Math.max(
                Math.max(distance(minX, centerX), distance(maxX, centerX)),
                Math.max(distance(minZ, centerZ), distance(maxZ, centerZ)));

        List<TileKey> result = new ArrayList<>(limit);
        for (long radius = 0; radius <= maxRadius && result.size() < limit; radius++) {
            List<TileKey> ring = new ArrayList<>((int) Math.min(Integer.MAX_VALUE, radius * 8 + 1));
            long startZ = (long) centerZ - radius;
            long endZ = (long) centerZ + radius;
            for (long z = startZ; z <= endZ; z++) {
                long startX = (long) centerX - radius;
                long endX = (long) centerX + radius;
                for (long x = startX; x <= endX; x++) {
                    if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                    if (Math.max(Math.abs(x - centerX), Math.abs(z - centerZ)) != radius) continue;
                    ring.add(new TileKey(profileId, settings.worldKey(), settings.signature(),
                            serverZoom, (int) x, (int) z));
                }
            }
            ring.sort(java.util.Comparator
                    .comparingDouble((TileKey key) -> squaredDistance(key.x(), key.z(), centerX, centerZ))
                    .thenComparingInt(TileKey::z)
                    .thenComparingInt(TileKey::x));
            for (TileKey key : ring) {
                if (result.size() == limit) break;
                result.add(key);
            }
        }
        return List.copyOf(result);
    }

    public static ParentFallback parentFallback(TileKey child, int parentZoom, int tileSize) {
        Objects.requireNonNull(child, "child");
        if (tileSize <= 0) throw new IllegalArgumentException("tileSize must be positive");
        int levels = child.zoom() - parentZoom;
        if (parentZoom < 0 || levels <= 0 || levels > 30) throw new IllegalArgumentException("invalid parent zoom");
        int factor = 1 << levels;
        int localX = Math.floorMod(child.x(), factor);
        int localZ = Math.floorMod(child.z(), factor);
        double u0 = localX / (double) factor;
        double v0 = localZ / (double) factor;
        return new ParentFallback(
                new TileKey(child.profileId(), child.worldKey(), child.settingsSignature(), parentZoom,
                        Math.floorDiv(child.x(), factor), Math.floorDiv(child.z(), factor)),
                u0, v0, u0 + 1.0 / factor, v0 + 1.0 / factor
        );
    }

    private static void checkZoom(SquaremapWorldSettings settings, int serverZoom) {
        if (serverZoom < settings.minZoom() || serverZoom > settings.maxZoom()) {
            throw new IllegalArgumentException("server zoom is outside squaremap bounds");
        }
    }

    private static int floorTile(double coordinate, double span) {
        double result = Math.floor(coordinate / span);
        if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("tile coordinate is outside supported range");
        }
        return (int) result;
    }

    private static long distance(int a, int b) { return Math.abs((long) a - b); }
    private static double squaredDistance(int x, int z, int centerX, int centerZ) {
        double dx = (long) x - centerX;
        double dz = (long) z - centerZ;
        return dx * dx + dz * dz;
    }
    private static long elapsed(long now, long then) { return now >= then ? now - then : Long.MAX_VALUE; }
    private static long saturatedAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }
    private static ExecutorService newPinPersistenceExecutor(String threadName) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), task -> {
                    Thread thread = new Thread(task, threadName);
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    private static final class PinPersistence implements AutoCloseable {
        private final TileDiskCache diskCache;
        private final ExecutorService executor;
        private final ExecutorService fallbackExecutor;
        private final Object lock = new Object();
        private boolean dirty;
        private boolean workerOutstanding;
        private boolean submitting;
        private boolean closing;

        private PinPersistence(TileDiskCache diskCache, ExecutorService executor,
                               ExecutorService fallbackExecutor) {
            this.diskCache = diskCache;
            this.executor = executor;
            this.fallbackExecutor = fallbackExecutor;
        }

        private void request() {
            boolean submit;
            synchronized (lock) {
                if (closing) return;
                dirty = true;
                submit = reserveSubmission();
            }
            if (submit) submit();
        }

        private void submit() {
            while (true) {
                boolean accepted = false;
                try {
                    executor.execute(this::drain);
                    accepted = true;
                } catch (RuntimeException rejected) {
                    try {
                        fallbackExecutor.execute(this::drain);
                        accepted = true;
                    } catch (RuntimeException fallbackRejected) {
                        // Keep dirty state in memory; a later request or close may retry submission.
                    }
                }
                boolean resubmit;
                boolean shutdown;
                synchronized (lock) {
                    submitting = false;
                    if (!accepted) workerOutstanding = false;
                    resubmit = accepted && dirty && !workerOutstanding && reserveSubmission();
                    shutdown = closing && !resubmit;
                    lock.notifyAll();
                }
                if (shutdown) shutdownExecutors();
                if (!resubmit) return;
            }
        }

        private void drain() {
            while (true) {
                synchronized (lock) {
                    if (!dirty) {
                        workerOutstanding = false;
                        lock.notifyAll();
                        return;
                    }
                    dirty = false;
                }
                if (!diskCache.persistPins()) {
                    synchronized (lock) {
                        dirty = true;
                        workerOutstanding = false;
                        lock.notifyAll();
                    }
                    return;
                }
            }
        }

        @Override public void close() {
            boolean submit;
            boolean shutdown;
            synchronized (lock) {
                if (closing) return;
                closing = true;
                dirty = true;
                submit = reserveSubmission();
                shutdown = !submit && !submitting;
            }
            if (submit) submit();
            else if (shutdown) shutdownExecutors();
        }

        private boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            long timeoutNanos = unit.toNanos(timeout);
            long started = System.nanoTime();
            if (!executor.awaitTermination(timeoutNanos, TimeUnit.NANOSECONDS)) return false;
            long elapsed = System.nanoTime() - started;
            if (!fallbackExecutor.awaitTermination(Math.max(0, timeoutNanos - elapsed), TimeUnit.NANOSECONDS)) {
                return false;
            }
            synchronized (lock) { return !dirty && !workerOutstanding && !submitting; }
        }

        private void shutdownExecutors() {
            executor.shutdown();
            fallbackExecutor.shutdown();
        }

        private boolean reserveSubmission() {
            if (workerOutstanding || submitting) return false;
            workerOutstanding = true;
            submitting = true;
            return true;
        }
    }

    private record ResidentMetadata(TileValidators validators, long lastCheckedMillis) {
        private static ResidentMetadata from(TileCacheEntry entry) {
            return new ResidentMetadata(entry.validators(), entry.lastCheckedMillis());
        }
    }

    private record RefreshFailure(int failures, long retryNotBeforeMillis) {}

    private record RevalidationRequest(TileKey key, ResidentMetadata metadata,
                                       long namespaceGeneration, long viewportGeneration) {}

    private record ViewportSignature(long namespaceGeneration, int zoom,
                                     long leftBits, long topBits, long rightBits, long bottomBits,
                                      long centerXBits, long centerZBits, long worldScaleBits) {}

    private record Stage(long token, int targetZoom, List<TileKey> targetTiles,
                         Set<TileKey> targetKeys) {
        private static Stage create(long token, int targetZoom, List<TileKey> targetTiles) {
            List<TileKey> ordered = List.copyOf(targetTiles);
            return new Stage(token, targetZoom, ordered, Set.copyOf(ordered));
        }
    }

    private static final class ManualSweep {
        private final List<TileKey> keys;
        private final long namespaceGeneration;
        private final long viewportGeneration;
        private int cursor;

        private ManualSweep(List<TileKey> keys, long namespaceGeneration, long viewportGeneration) {
            this.keys = keys;
            this.namespaceGeneration = namespaceGeneration;
            this.viewportGeneration = viewportGeneration;
        }
    }

    private record CompletedTile(TileKey key, DecodedTile image,
                                 long namespaceGeneration, long viewportGeneration) {}
}
