/*
 * Derived in part from JR1258/EarthMC-Map-Addon SquaremapApiClient, upstream commit
 * c85c5003855eb47868868b931624b951cffba74e, Apache-2.0.
 * Reworked to remove EarthMC/Towny state and add injectable scheduling, conditional resources,
 * immutable SIMMC snapshots, bounded backoff, player-staleness privacy, and crash-safe caching.
 */
package com.murphypotato.simmctoolset.internal.map.network;

import com.murphypotato.simmctoolset.internal.map.cache.SnapshotCacheEntry;
import com.murphypotato.simmctoolset.internal.map.cache.SnapshotDiskCache;
import com.murphypotato.simmctoolset.internal.map.model.MapSnapshot;
import com.murphypotato.simmctoolset.internal.map.model.OnlinePlayerEntry;
import com.murphypotato.simmctoolset.internal.map.model.SquaremapWorldSettings;
import com.murphypotato.simmctoolset.internal.map.parse.PlayersParser;
import com.murphypotato.simmctoolset.internal.map.parse.SquaremapMarkersParser;
import com.murphypotato.simmctoolset.internal.map.parse.SquaremapSettingsParser;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleSupplier;

public final class RefreshCoordinator implements AutoCloseable {
    private static final Duration MARKER_CADENCE = Duration.ofSeconds(15);
    private static final Duration PLAYER_CADENCE = Duration.ofSeconds(2);
    private static final Duration PLAYER_STALE = Duration.ofSeconds(15);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private final String worldKey;
    private final SquaremapDataSource source;
    private final SquaremapSettingsParser settingsParser;
    private final SquaremapMarkersParser markersParser;
    private final PlayersParser playersParser;
    private final SnapshotDiskCache cache;
    private final Clock clock;
    private final RefreshScheduler scheduler;
    private final Executor executor;
    private final DoubleSupplier jitter;
    private final AtomicReference<MapSnapshot> snapshot = new AtomicReference<>(new MapSnapshot(List.of()));
    private final AtomicReference<SquaremapWorldSettings> settings = new AtomicReference<>();
    private final AtomicReference<List<OnlinePlayerEntry>> players = new AtomicReference<>(List.of());

    private final AtomicBoolean scheduling = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<CompletableFuture<Void>> markerInflight = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> playerInflight = new AtomicReference<>();
    private final Object cycleLifecycleLock = new Object();
    private final Object scheduleLock = new Object();
    private volatile boolean playersAvailable;
    private String settingsRaw;
    private String markersRaw;
    private String playersRaw;
    private HttpValidators settingsValidators = HttpValidators.EMPTY;
    private HttpValidators markersValidators = HttpValidators.EMPTY;
    private HttpValidators playersValidators = HttpValidators.EMPTY;
    private int markerFailures;
    private int playerFailures;
    private Instant lastPlayerSuccess;
    private Cancellable markerScheduled;
    private Cancellable playerScheduled;
    private long markerScheduleGeneration;
    private long playerScheduleGeneration;
    private long schedulingRunGeneration;

    public RefreshCoordinator(String worldKey, SquaremapDataSource source,
                              SquaremapSettingsParser settingsParser, SquaremapMarkersParser markersParser,
                              PlayersParser playersParser, SnapshotDiskCache cache, Clock clock,
                              RefreshScheduler scheduler, Executor executor, DoubleSupplier jitter) {
        this.worldKey = Objects.requireNonNull(worldKey, "worldKey");
        this.source = Objects.requireNonNull(source, "source");
        this.settingsParser = Objects.requireNonNull(settingsParser, "settingsParser");
        this.markersParser = Objects.requireNonNull(markersParser, "markersParser");
        this.playersParser = Objects.requireNonNull(playersParser, "playersParser");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.jitter = Objects.requireNonNull(jitter, "jitter");
    }

    public MapSnapshot snapshot() { return snapshot.get(); }
    public SquaremapWorldSettings settings() { return settings.get(); }
    public List<OnlinePlayerEntry> players() { return players.get(); }
    public boolean playersAvailable() { return playersAvailable; }

    public boolean restoreFromDisk() {
        if (closed.get()) return false;
        return cache.load().filter(entry -> entry.worldKey().equals(worldKey)).map(entry -> {
            try {
                SquaremapWorldSettings parsedSettings = settingsParser.parse(entry.settingsJson(), worldKey);
                MapSnapshot parsedSnapshot = markersParser.parse(entry.markersJson());
                List<OnlinePlayerEntry> parsedPlayers = playersParser.parse(entry.playersJson(), worldKey);
                Instant now = clock.instant();
                boolean playerDataFresh = entry.playersAvailable() && entry.playersLastSuccessAt() != null
                        && !entry.playersLastSuccessAt().isAfter(now)
                        && Duration.between(entry.playersLastSuccessAt(), now).compareTo(PLAYER_STALE) <= 0;
                synchronized (this) {
                    if (closed.get()) return false;
                    settingsRaw = entry.settingsJson(); markersRaw = entry.markersJson(); playersRaw = entry.playersJson();
                    settingsValidators = entry.settingsValidators(); markersValidators = entry.markersValidators();
                    playersValidators = entry.playersValidators(); lastPlayerSuccess = entry.playersLastSuccessAt();
                    snapshot.set(parsedSnapshot);
                    settings.set(parsedSettings);
                    players.set(playerDataFresh ? parsedPlayers : List.of());
                    playersAvailable = playerDataFresh;
                }
                return true;
            } catch (RuntimeException invalidPayload) {
                cache.quarantine();
                return false;
            }
        }).orElse(false);
    }

    public void startScheduling() {
        if (closed.get() || !scheduling.compareAndSet(false, true)) return;
        long generation;
        synchronized (scheduleLock) {
            if (closed.get()) {
                scheduling.set(false);
                return;
            }
            generation = ++schedulingRunGeneration;
        }
        try {
            executor.execute(() -> runSchedulingStart(generation));
        } catch (RuntimeException rejected) {
            failSchedulingStart(generation);
        }
    }

    private void runSchedulingStart(long generation) {
        try {
            if (snapshot.get().layers().isEmpty()) restoreFromDisk();
            if (schedulingRunActive(generation)) {
                scheduleMarker(Duration.ZERO);
                schedulePlayer(Duration.ZERO);
            }
        } catch (RuntimeException startFailure) {
            failSchedulingStart(generation);
        }
    }

    private boolean schedulingRunActive(long generation) {
        synchronized (scheduleLock) {
            return !closed.get() && scheduling.get() && schedulingRunGeneration == generation;
        }
    }

    private void failSchedulingStart(long generation) {
        Cancellable markerTask;
        Cancellable playerTask;
        synchronized (scheduleLock) {
            if (schedulingRunGeneration != generation) return;
            scheduling.set(false);
            schedulingRunGeneration++;
            markerScheduleGeneration++;
            playerScheduleGeneration++;
            markerTask = markerScheduled;
            playerTask = playerScheduled;
            markerScheduled = null;
            playerScheduled = null;
        }
        cancelBestEffort(markerTask);
        cancelBestEffort(playerTask);
    }

    public CompletableFuture<Void> refreshMarkersNow() {
        CompletableFuture<Void> acquired;
        HttpValidators sv;
        HttpValidators mv;
        synchronized (cycleLifecycleLock) {
            if (closed.get()) return CompletableFuture.completedFuture(null);
            CompletableFuture<Void> pending = markerInflight.get();
            if (pending != null) return pending;
            acquired = new CompletableFuture<>();
            markerInflight.set(acquired);
            synchronized (this) { sv = settingsValidators; mv = markersValidators; }
        }
        try {
            CompletableFuture<HttpResult> settingsFuture = source.fetchSettings(sv);
            CompletableFuture<HttpResult> markersFuture = source.fetchMarkers(mv);
            settingsFuture.thenCombineAsync(markersFuture, Pair::new, executor)
                    .thenAcceptAsync(pair -> acceptMarkers(pair.settings, pair.markers), executor)
                    .whenComplete((ignored, failure) -> finishMarker(acquired, failure));
        } catch (RuntimeException requestFailure) {
            finishMarker(acquired, requestFailure);
        }
        return acquired;
    }

    public CompletableFuture<Void> refreshPlayersNow() {
        CompletableFuture<Void> acquired;
        HttpValidators validators;
        synchronized (cycleLifecycleLock) {
            if (closed.get()) return CompletableFuture.completedFuture(null);
            CompletableFuture<Void> pending = playerInflight.get();
            if (pending != null) return pending;
            acquired = new CompletableFuture<>();
            playerInflight.set(acquired);
            synchronized (this) { validators = playersValidators; }
        }
        try {
            source.fetchPlayers(validators).thenAcceptAsync(this::acceptPlayers, executor)
                    .whenComplete((ignored, failure) -> finishPlayer(acquired, failure));
        } catch (RuntimeException requestFailure) {
            finishPlayer(acquired, requestFailure);
        }
        return acquired;
    }

    private void finishMarker(CompletableFuture<Void> result, Throwable failure) {
        try {
            if (failure != null && !closed.get()) markerFailed();
        } finally {
            synchronized (cycleLifecycleLock) {
                markerInflight.compareAndSet(result, null);
            }
            result.complete(null);
        }
    }

    private void finishPlayer(CompletableFuture<Void> result, Throwable failure) {
        try {
            if (failure != null && !closed.get()) playerFailed();
        } finally {
            synchronized (cycleLifecycleLock) {
                playerInflight.compareAndSet(result, null);
            }
            result.complete(null);
        }
    }

    private void acceptMarkers(HttpResult settings, HttpResult markers) {
        boolean accepted = false;
        try {
            synchronized (this) {
                if (closed.get()) return;
                String nextSettings = updatedRaw(settings, settingsRaw);
                String nextMarkers = updatedRaw(markers, markersRaw);
                if (nextSettings != null && nextMarkers != null && ok(settings) && ok(markers)) {
                    SquaremapWorldSettings nextWorldSettings = settings.status() == HttpStatus.SUCCESS
                            || this.settings.get() == null
                            ? settingsParser.parse(nextSettings, worldKey) : this.settings.get();
                    MapSnapshot nextSnapshot = markers.status() == HttpStatus.SUCCESS ? markersParser.parse(nextMarkers) : snapshot.get();
                    if (closed.get()) return;
                    settingsRaw = nextSettings; markersRaw = nextMarkers;
                    settingsValidators = settings.validators(); markersValidators = markers.validators();
                    this.settings.set(nextWorldSettings);
                    if (markers.status() == HttpStatus.SUCCESS) snapshot.set(nextSnapshot);
                    markerFailures = 0;
                    persistIfComplete();
                    accepted = true;
                }
            }
        } catch (RuntimeException invalidResponse) {
            // Count below after leaving all state locks.
        }
        if (accepted) scheduleMarkerIfActive(MARKER_CADENCE); else markerFailed();
    }

    private void acceptPlayers(HttpResult result) {
        boolean accepted = false;
        try {
            synchronized (this) {
                if (closed.get()) return;
                String next = updatedRaw(result, playersRaw);
                if (next == null || !ok(result)) {
                    if (result.status() == HttpStatus.NOT_MODIFIED) playersValidators = HttpValidators.EMPTY;
                } else {
                    boolean recoveringFromUnavailable304 = result.status() == HttpStatus.NOT_MODIFIED && !playersAvailable;
                    List<OnlinePlayerEntry> parsed = result.status() == HttpStatus.SUCCESS || recoveringFromUnavailable304
                            ? playersParser.parse(next, worldKey) : players.get();
                    if (closed.get()) return;
                    if (result.status() == HttpStatus.SUCCESS || recoveringFromUnavailable304) players.set(parsed);
                    playersRaw = next; playersValidators = result.validators(); playerFailures = 0;
                    lastPlayerSuccess = clock.instant(); playersAvailable = true;
                    persistIfComplete();
                    accepted = true;
                }
            }
        } catch (RuntimeException invalidResponse) {
            synchronized (this) {
                if (result.status() == HttpStatus.NOT_MODIFIED) playersValidators = HttpValidators.EMPTY;
            }
        }
        if (accepted) schedulePlayerIfActive(PLAYER_CADENCE); else playerFailed();
    }

    private void markerFailed() {
        int failures;
        synchronized (this) {
            if (closed.get()) return;
            failures = ++markerFailures;
        }
        scheduleMarkerIfActive(backoff(failures));
    }

    private void playerFailed() {
        int failures;
        synchronized (this) {
            if (closed.get()) return;
            failures = ++playerFailures;
            boolean stale = lastPlayerSuccess == null || Duration.between(lastPlayerSuccess, clock.instant()).compareTo(PLAYER_STALE) > 0;
            if (failures >= 3 || stale) {
                players.set(List.of());
                playersAvailable = false;
                persistIfComplete();
            }
        }
        schedulePlayerIfActive(backoff(failures));
    }

    private void persistIfComplete() {
        if (closed.get() || settingsRaw == null || markersRaw == null || playersRaw == null) return;
        try {
            cache.save(new SnapshotCacheEntry(worldKey, settingsRaw, markersRaw, playersRaw, settingsValidators,
                    markersValidators, playersValidators, clock.instant(), lastPlayerSuccess, playersAvailable));
        } catch (RuntimeException ignoredCacheFailure) {
            // A disk failure must never discard live last-good data.
        }
    }

    private Duration backoff(int failures) {
        long seconds = Math.min(MAX_BACKOFF.toSeconds(), 1L << Math.min(6, Math.max(0, failures - 1)));
        double sampled = Math.max(-1, Math.min(1, jitter.getAsDouble()));
        return Duration.ofMillis(Math.max(1, Math.round(seconds * 1000 * (1 + sampled * 0.2))));
    }

    private void scheduleMarkerIfActive(Duration delay) {
        if (scheduling.get() && !closed.get()) scheduleMarker(delay);
    }

    private void schedulePlayerIfActive(Duration delay) {
        if (scheduling.get() && !closed.get()) schedulePlayer(delay);
    }

    private void scheduleMarker(Duration delay) {
        Cancellable old;
        long generation;
        synchronized (scheduleLock) {
            if (closed.get() || !scheduling.get()) return;
            generation = ++markerScheduleGeneration;
            old = markerScheduled;
            markerScheduled = null;
        }
        cancelBestEffort(old);
        Cancellable created;
        try {
            created = scheduler.schedule(() -> markerScheduleFired(generation), delay);
        } catch (RuntimeException rejected) {
            invalidateMarkerSchedule(generation);
            return;
        }
        if (created == null) {
            invalidateMarkerSchedule(generation);
            return;
        }
        boolean keep;
        synchronized (scheduleLock) {
            keep = !closed.get() && scheduling.get()
                    && markerScheduleGeneration == generation && markerScheduled == null;
            if (keep) markerScheduled = created;
        }
        if (!keep) cancelBestEffort(created);
    }

    private void schedulePlayer(Duration delay) {
        Cancellable old;
        long generation;
        synchronized (scheduleLock) {
            if (closed.get() || !scheduling.get()) return;
            generation = ++playerScheduleGeneration;
            old = playerScheduled;
            playerScheduled = null;
        }
        cancelBestEffort(old);
        Cancellable created;
        try {
            created = scheduler.schedule(() -> playerScheduleFired(generation), delay);
        } catch (RuntimeException rejected) {
            invalidatePlayerSchedule(generation);
            return;
        }
        if (created == null) {
            invalidatePlayerSchedule(generation);
            return;
        }
        boolean keep;
        synchronized (scheduleLock) {
            keep = !closed.get() && scheduling.get()
                    && playerScheduleGeneration == generation && playerScheduled == null;
            if (keep) playerScheduled = created;
        }
        if (!keep) cancelBestEffort(created);
    }

    private void markerScheduleFired(long generation) {
        synchronized (scheduleLock) {
            if (closed.get() || markerScheduleGeneration != generation) return;
            markerScheduleGeneration++;
            markerScheduled = null;
        }
        refreshMarkersNow();
    }

    private void invalidateMarkerSchedule(long generation) {
        synchronized (scheduleLock) {
            if (markerScheduleGeneration == generation && markerScheduled == null) markerScheduleGeneration++;
        }
    }

    private void playerScheduleFired(long generation) {
        synchronized (scheduleLock) {
            if (closed.get() || playerScheduleGeneration != generation) return;
            playerScheduleGeneration++;
            playerScheduled = null;
        }
        refreshPlayersNow();
    }

    private void invalidatePlayerSchedule(long generation) {
        synchronized (scheduleLock) {
            if (playerScheduleGeneration == generation && playerScheduled == null) playerScheduleGeneration++;
        }
    }

    private static void cancelBestEffort(Cancellable cancellable) {
        if (cancellable == null) return;
        try {
            cancellable.cancel();
        } catch (RuntimeException ignored) {
            // Scheduling is optional; a broken handle must not trap the refresh lifecycle.
        }
    }

    private static boolean ok(HttpResult result) {
        return result.status() == HttpStatus.SUCCESS || result.status() == HttpStatus.NOT_MODIFIED;
    }

    private static String updatedRaw(HttpResult result, String previous) {
        return result.status() == HttpStatus.SUCCESS ? new String(result.body(), StandardCharsets.UTF_8)
                : result.status() == HttpStatus.NOT_MODIFIED ? previous : null;
    }

    @Override
    public void close() {
        CompletableFuture<Void> marker;
        CompletableFuture<Void> player;
        synchronized (cycleLifecycleLock) {
            if (!closed.compareAndSet(false, true)) return;
            scheduling.set(false);
            marker = markerInflight.getAndSet(null);
            player = playerInflight.getAndSet(null);
        }
        synchronized (this) {
            // Wait for a parser/publisher that entered before close; it rechecks closed before publication.
        }
        Cancellable markerTask;
        Cancellable playerTask;
        synchronized (scheduleLock) {
            schedulingRunGeneration++;
            markerScheduleGeneration++;
            playerScheduleGeneration++;
            markerTask = markerScheduled;
            playerTask = playerScheduled;
            markerScheduled = null;
            playerScheduled = null;
        }
        cancelBestEffort(markerTask);
        cancelBestEffort(playerTask);
        if (marker != null) marker.complete(null);
        if (player != null) player.complete(null);
    }

    private record Pair(HttpResult settings, HttpResult markers) {}
}
