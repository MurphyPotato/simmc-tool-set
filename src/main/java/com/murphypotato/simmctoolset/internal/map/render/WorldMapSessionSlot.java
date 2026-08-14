package com.murphypotato.simmctoolset.internal.map.render;

import com.murphypotato.simmctoolset.internal.map.model.MapSnapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Atomic install/update/invalidate/close slot that Task 13 can drive from connection lifecycle events. */
public final class WorldMapSessionSlot implements AutoCloseable {
    private final AtomicReference<WorldMapRenderSession> current = new AtomicReference<>();

    public synchronized void install(WorldMapRenderSession session, WorldMapRenderSession.SessionInput input) {
        Objects.requireNonNull(session, "session");
        session.accept(Objects.requireNonNull(input, "input"));
        WorldMapRenderSession previous = current.getAndSet(session);
        if (previous != null && previous != session) previous.close();
    }

    public synchronized boolean update(WorldMapRenderSession.SessionInput input) {
        WorldMapRenderSession session = current.get();
        if (session == null) return false;
        session.accept(Objects.requireNonNull(input, "input"));
        return true;
    }

    public synchronized boolean updateSnapshot(MapSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        WorldMapRenderSession session = current.get();
        if (session == null) return false;
        WorldMapRenderSession.SessionInput active = session.activeSession().orElseThrow();
        session.accept(new WorldMapRenderSession.SessionInput(active.profileId(), active.profile(),
                active.settings(), snapshot));
        return true;
    }

    public Optional<WorldMapRenderSession> current() {
        return Optional.ofNullable(current.get());
    }

    public synchronized boolean invalidateGpuResources() {
        WorldMapRenderSession session = current.get();
        if (session == null) return false;
        session.invalidateGpuResources();
        return true;
    }

    public synchronized boolean revalidateCurrentViewport() {
        WorldMapRenderSession session = current.get();
        if (session == null) return false;
        session.revalidateCurrentViewport();
        return true;
    }

    @Override public synchronized void close() {
        WorldMapRenderSession session = current.getAndSet(null);
        if (session != null) session.close();
    }
}
