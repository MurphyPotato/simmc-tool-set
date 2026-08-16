package com.murphypotato.simmctoolset.internal.map.render;

import com.murphypotato.simmctoolset.internal.map.model.MapLayer;
import com.murphypotato.simmctoolset.internal.map.model.MapSnapshot;
import com.murphypotato.simmctoolset.internal.map.model.MapPoint;
import com.murphypotato.simmctoolset.internal.map.model.PolylineMarker;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Atomic world-map snapshot facade. Minecraft drawing is kept behind {@link Surface}. */
public final class WorldMapOverlayRenderer {
    public static final String WORLD_BORDER_LAYER = "squaremap-worldborder";
    private static final double FIT_CONTENT_RATIO = 0.90; // five percent on every side

    public record View(double centerX, double centerZ, double scale, double width, double height) {
        public View {
            if (!Double.isFinite(centerX) || !Double.isFinite(centerZ) || !Double.isFinite(scale) || scale <= 0
                    || !Double.isFinite(width) || width <= 0 || !Double.isFinite(height) || height <= 0) {
                throw new IllegalArgumentException("invalid world-map view");
            }
        }

        public Bounds worldBounds() {
            return new Bounds(centerX - width / (2 * scale), centerZ - height / (2 * scale),
                    centerX + width / (2 * scale), centerZ + height / (2 * scale));
        }

        public ScreenPoint worldToScreen(double worldX, double worldZ) {
            return MapProjection.worldToScreen(worldX, worldZ, centerX, centerZ, scale, width, height);
        }

        public MapPoint2D screenToWorld(double screenX, double screenY) {
            return MapProjection.screenToWorld(screenX, screenY, centerX, centerZ, scale, width, height);
        }
    }

    public record HitResult(MapLayer layer, com.murphypotato.simmctoolset.internal.map.model.MapMarker marker) { }

    @FunctionalInterface
    public interface Surface {
        void draw(View view, List<LayerRenderer.DrawCommand> commands);
    }

    private record State(MapSnapshot snapshot, LayerRenderer.Prepared prepared, Optional<Bounds> worldBorder,
                         Set<String> hiddenLayers, CommandCache commandCache) { }

    private record CommandKey(LayerRenderer.Prepared prepared, Set<String> hiddenLayers, View view) {
        @Override
        public boolean equals(Object other) {
            return other instanceof CommandKey key && prepared == key.prepared
                    && hiddenLayers.equals(key.hiddenLayers) && view.equals(key.view);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * System.identityHashCode(prepared) + hiddenLayers.hashCode()) + view.hashCode();
        }
    }

    private static final class CommandCache {
        private final java.util.LinkedHashMap<CommandKey, List<LayerRenderer.DrawCommand>> entries =
                new java.util.LinkedHashMap<>(2, 0.75f, true);

        synchronized List<LayerRenderer.DrawCommand> getOrCompute(
                CommandKey key, java.util.function.Supplier<List<LayerRenderer.DrawCommand>> supplier) {
            List<LayerRenderer.DrawCommand> cached = entries.get(key);
            if (cached != null) return cached;
            List<LayerRenderer.DrawCommand> computed = List.copyOf(supplier.get());
            entries.put(key, computed);
            if (entries.size() > 2) {
                entries.remove(entries.keySet().iterator().next());
            }
            return computed;
        }
    }

    private final LayerRenderer layers = new LayerRenderer();
    private final AtomicReference<State> state;

    public WorldMapOverlayRenderer(MapSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        this.state = new AtomicReference<>(prepare(snapshot, Set.of()));
    }

    public void acceptSnapshot(MapSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        state.updateAndGet(current -> prepare(snapshot, current.hiddenLayers()));
    }

    public void setHiddenLayers(Set<String> layerIds) {
        Set<String> safe = Set.copyOf(layerIds);
        state.updateAndGet(current -> current.hiddenLayers().equals(safe) ? current
                : new State(current.snapshot(), current.prepared(), current.worldBorder(), safe, new CommandCache()));
    }

    public List<LayerRenderer.DrawCommand> commands(View view) {
        State current = state.get();
        CommandKey key = new CommandKey(current.prepared(), current.hiddenLayers(), Objects.requireNonNull(view, "view"));
        return current.commandCache().getOrCompute(key,
                () -> layers.commands(current.prepared(), current.hiddenLayers(), view));
    }

    public void render(Surface surface, View view) {
        surface.draw(view, commands(view));
    }

    public Optional<HitResult> hitTest(double screenX, double screenY, View view) {
        if (view == null) return Optional.empty();
        State current = state.get();
        return layers.hitTest(current.prepared(), current.hiddenLayers(), screenX, screenY, view)
                .map(hit -> new HitResult(hit.layer(), hit.marker()));
    }

    public Optional<Bounds> worldBorderBounds() {
        return state.get().worldBorder();
    }

    public View fitWorldBorder(View current) {
        Optional<Bounds> maybeBounds = worldBorderBounds();
        if (maybeBounds.isEmpty()) return current;
        Bounds bounds = maybeBounds.orElseThrow();
        double worldWidth = bounds.maxX() - bounds.minX();
        double worldHeight = bounds.maxZ() - bounds.minZ();
        if (!(worldWidth > 0) || !(worldHeight > 0)) return current;
        double fittedScale = Math.min(current.width() * FIT_CONTENT_RATIO / worldWidth,
                current.height() * FIT_CONTENT_RATIO / worldHeight);
        if (!Double.isFinite(fittedScale) || fittedScale <= 0) return current;
        return new View(bounds.centerX(), bounds.centerZ(), fittedScale, current.width(), current.height());
    }

    public void clearScreenState() {
        // Task 9 has no persistent popup yet; retained as the map-close lifecycle boundary for Task 10.
    }

    private State prepare(MapSnapshot snapshot, Set<String> hidden) {
        return new State(snapshot, layers.prepare(snapshot), borderBounds(snapshot),
                Set.copyOf(hidden), new CommandCache());
    }

    private static Optional<Bounds> borderBounds(MapSnapshot snapshot) {
        MapLayer border = snapshot.layer(WORLD_BORDER_LAYER).orElse(null);
        if (border == null) return Optional.empty();
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        boolean found = false;
        for (var marker : border.markers()) {
            if (!(marker instanceof PolylineMarker line)) continue;
            for (MapPoint point : line.points()) {
                found = true;
                minX = Math.min(minX, point.x()); minZ = Math.min(minZ, point.z());
                maxX = Math.max(maxX, point.x()); maxZ = Math.max(maxZ, point.z());
            }
        }
        return found ? Optional.of(new Bounds(minX, minZ, maxX, maxZ)) : Optional.empty();
    }
}
