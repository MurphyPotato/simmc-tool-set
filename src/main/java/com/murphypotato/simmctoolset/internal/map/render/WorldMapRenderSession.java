package com.murphypotato.simmctoolset.internal.map.render;

import com.murphypotato.simmctoolset.internal.map.config.ConnectionProfile;
import com.murphypotato.simmctoolset.internal.map.model.IconMarker;
import com.murphypotato.simmctoolset.internal.map.model.MapSnapshot;
import com.murphypotato.simmctoolset.internal.map.model.SquaremapWorldSettings;
import com.murphypotato.simmctoolset.internal.map.network.RegisteredIconLoader;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Task 9 resource boundary for one active world-map connection. Task 13 supplies and owns the
 * connection-specific sources; this class only composes their client-thread render operations.
 */
public final class WorldMapRenderSession implements AutoCloseable {
    public record SessionInput(String profileId, ConnectionProfile profile,
                               SquaremapWorldSettings settings, MapSnapshot snapshot) {
        public SessionInput {
            Objects.requireNonNull(profileId, "profileId");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(snapshot, "snapshot");
            if (!profile.worldKey().equals(settings.worldKey())) {
                throw new IllegalArgumentException("profile and settings worldKey must match");
            }
        }
    }

    public record TileDraw(GpuTileRegion texture, double left, double top, double right, double bottom) {
        public TileDraw { Objects.requireNonNull(texture, "texture"); }
    }

    @FunctionalInterface
    public interface IconLookup {
        Optional<Object> resolve(String registeredKey);
    }

    public interface Surface {
        void drawTile(TileDraw tile);
        void drawMarkers(List<LayerRenderer.DrawCommand> commands, IconLookup icons);
    }

    private final WorldMapTileSource tiles;
    private final RegisteredIconLoader iconLoader;
    private final WorldMapIconRegistry iconTextures;
    private final WorldMapOverlayRenderer overlay = new WorldMapOverlayRenderer(new MapSnapshot(List.of()));
    private final int tileUploadBudget;
    private final int iconUploadBudget;
    private final AtomicReference<SessionInput> active = new AtomicReference<>();
    private boolean closed;

    public WorldMapRenderSession(WorldMapTileSource tiles, RegisteredIconLoader iconLoader,
                                 WorldMapIconRegistry iconTextures,
                                 int tileUploadBudget, int iconUploadBudget) {
        this.tiles = Objects.requireNonNull(tiles, "tiles");
        this.iconLoader = Objects.requireNonNull(iconLoader, "iconLoader");
        this.iconTextures = Objects.requireNonNull(iconTextures, "iconTextures");
        if (tileUploadBudget < 0 || iconUploadBudget < 0) {
            throw new IllegalArgumentException("upload budgets must be non-negative");
        }
        this.tileUploadBudget = tileUploadBudget;
        this.iconUploadBudget = iconUploadBudget;
    }

    public synchronized void accept(SessionInput input) {
        ensureOpen();
        Objects.requireNonNull(input, "input");
        SessionInput previous = active.get();
        if (previous != null && (!previous.profileId().equals(input.profileId())
                || !previous.profile().equals(input.profile()))) {
            throw new IllegalArgumentException("connection identity is immutable; install a new session");
        }
        Set<String> previousIcons = previous == null ? Set.of() : iconKeys(previous.snapshot());
        Set<String> nextIcons = iconKeys(input.snapshot());
        tiles.updateNamespace(input.profileId(), input.settings());
        if (previous != null && !previousIcons.equals(nextIcons)) iconTextures.invalidate();
        overlay.acceptSnapshot(input.snapshot());
        active.set(input);
        nextIcons.forEach(icon -> iconTextures.request(icon, iconLoader));
    }

    public Optional<SessionInput> activeSession() {
        return Optional.ofNullable(active.get());
    }

    public synchronized void render(Surface surface, WorldMapOverlayRenderer.View view, RequestContext context) {
        ensureOpen();
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(context, "context");
        SessionInput session = active.get();
        if (session == null) return;
        Bounds bounds = view.worldBounds();
        WorldViewport viewport = new WorldViewport(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ(),
                view.centerX(), view.centerZ());
        List<TileKey> visible = tiles.requestViewport(viewport, view.scale(), context);
        tiles.drainUploads(tileUploadBudget);
        iconTextures.drainUploads(iconUploadBudget);
        double span = SquaremapTileRenderer.tileWorldSpan(session.settings(),
                visible.isEmpty() ? session.settings().minZoom() : visible.getFirst().zoom());
        for (TileKey key : visible) {
            Optional<GpuTileRegion> texture = tiles.gpuHandleOrParent(key);
            if (texture.isEmpty()) continue;
            double worldLeft = key.x() * span;
            double worldTop = key.z() * span;
            ScreenPoint topLeft = view.worldToScreen(worldLeft, worldTop);
            ScreenPoint bottomRight = view.worldToScreen(worldLeft + span, worldTop + span);
            surface.drawTile(new TileDraw(texture.orElseThrow(), topLeft.x(), topLeft.y(),
                    bottomRight.x(), bottomRight.y()));
        }
        surface.drawMarkers(overlay.commands(view), iconTextures::resolve);
    }

    public WorldMapOverlayRenderer overlay() {
        return overlay;
    }

    public synchronized void invalidateGpuResources() {
        ensureOpen();
        tiles.invalidateGpuResources();
        iconTextures.invalidate();
    }

    public synchronized void revalidateCurrentViewport() {
        ensureOpen();
        tiles.revalidateCurrentViewport();
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("world-map render session is closed");
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        active.set(null);
        RuntimeException failure = null;
        try { iconTextures.close(); }
        catch (RuntimeException closeFailure) { failure = append(failure, closeFailure); }
        try { iconLoader.close(); }
        catch (RuntimeException closeFailure) { failure = append(failure, closeFailure); }
        try { tiles.close(); }
        catch (RuntimeException closeFailure) { failure = append(failure, closeFailure); }
        if (failure != null) throw failure;
    }

    private static Set<String> iconKeys(MapSnapshot snapshot) {
        java.util.HashSet<String> keys = new java.util.HashSet<>();
        for (var layer : snapshot.layers()) {
            for (var marker : layer.markers()) {
                if (marker instanceof IconMarker icon && !icon.icon().isBlank()) keys.add(icon.icon());
            }
        }
        return Set.copyOf(keys);
    }

    private static RuntimeException append(RuntimeException first, RuntimeException next) {
        if (first == null) return next;
        first.addSuppressed(next);
        return first;
    }
}
