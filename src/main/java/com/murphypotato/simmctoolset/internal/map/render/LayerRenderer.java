/*
 * Rendering chronology and screen projection are derived from JR1258/EarthMC-Map-Addon
 * WorldMapRenderer at c85c5003855eb47868868b931624b951cffba74e (Apache-2.0).
 * Reworked for immutable generic squaremap layers and platform-neutral draw commands.
 */
package com.murphypotato.simmctoolset.internal.map.render;

import com.murphypotato.simmctoolset.internal.map.model.IconMarker;
import com.murphypotato.simmctoolset.internal.map.model.MapLayer;
import com.murphypotato.simmctoolset.internal.map.model.MapMarker;
import com.murphypotato.simmctoolset.internal.map.model.MapPoint;
import com.murphypotato.simmctoolset.internal.map.model.MapSnapshot;
import com.murphypotato.simmctoolset.internal.map.model.PolygonMarker;
import com.murphypotato.simmctoolset.internal.map.model.PolylineMarker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/** Pure preparation, culling, projection, draw ordering, and hit geometry for squaremap layers. */
public final class LayerRenderer {
    static final double MIN_SCREEN_HIT_RADIUS = 4.0;
    private static final double INDEX_CELL_SIZE = 2048.0;

    public sealed interface DrawCommand permits PolygonCommand, PolylineCommand, IconCommand {
        PreparedMarker source();
    }

    public record PolygonCommand(PreparedMarker source, List<List<ScreenPoint>> rings,
                                 int strokeArgb, int fillArgb, double strokeWidth) implements DrawCommand {
        public PolygonCommand { rings = rings.stream().map(List::copyOf).toList(); }
    }

    public record PolylineCommand(PreparedMarker source, List<ScreenPoint> points,
                                  int strokeArgb, double strokeWidth) implements DrawCommand {
        public PolylineCommand { points = List.copyOf(points); }
    }

    public record IconCommand(PreparedMarker source, double left, double top, double right, double bottom,
                              String icon) implements DrawCommand { }

    public record PreparedMarker(MapLayer layer, MapMarker marker, int drawOrdinal,
                                 Bounds bounds, PolygonRenderCache polygon) {
        public PreparedMarker {
            Objects.requireNonNull(layer, "layer");
            Objects.requireNonNull(marker, "marker");
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    public record Prepared(List<PreparedMarker> markers, SpatialIndex<PreparedMarker> index,
                           List<PreparedMarker> icons, double maximumIconReach) {
        public Prepared {
            markers = List.copyOf(markers);
            Objects.requireNonNull(index, "index");
            icons = List.copyOf(icons);
        }
    }

    public record Hit(MapLayer layer, MapMarker marker) { }

    public Prepared prepare(MapSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<LayerSource> orderedLayers = new ArrayList<>();
        for (int source = 0; source < snapshot.layers().size(); source++) {
            MapLayer layer = snapshot.layers().get(source);
            orderedLayers.add(new LayerSource(layer, source));
        }
        orderedLayers.sort(Comparator.comparingInt((LayerSource item) -> item.layer().zIndex())
                .thenComparingInt(item -> item.layer().order()).thenComparingInt(LayerSource::sourceOrdinal));

        List<PreparedMarker> prepared = new ArrayList<>();
        List<PreparedMarker> icons = new ArrayList<>();
        List<SpatialIndex.Entry<PreparedMarker>> entries = new ArrayList<>();
        double maxIconReach = MIN_SCREEN_HIT_RADIUS;
        int drawOrdinal = 0;
        for (LayerSource source : orderedLayers) {
            for (int markerOrdinal = 0; markerOrdinal < source.layer().markers().size(); markerOrdinal++) {
                MapMarker marker = source.layer().markers().get(markerOrdinal);
                OptionalDouble iconReach = marker instanceof IconMarker icon
                        ? ScaledIconGeometry.maximumReach(source.layer().id(), icon) : OptionalDouble.empty();
                if (marker instanceof IconMarker && iconReach.isEmpty()) continue;
                PolygonRenderCache polygon = marker instanceof PolygonMarker polygonMarker
                        ? PolygonRenderCache.build(polygonMarker, source.layer().id() + ':' + markerOrdinal) : null;
                Bounds bounds = bounds(marker, polygon);
                if (bounds == null) continue;
                PreparedMarker item = new PreparedMarker(source.layer(), marker, drawOrdinal++, bounds, polygon);
                prepared.add(item);
                entries.add(new SpatialIndex.Entry<>(source.layer().id() + ':' + source.sourceOrdinal()
                        + ':' + markerOrdinal, item, bounds));
                if (marker instanceof IconMarker icon) {
                    icons.add(item);
                    maxIconReach = Math.max(maxIconReach, iconReach.orElseThrow());
                }
            }
        }
        return new Prepared(prepared, SpatialIndex.build(INDEX_CELL_SIZE, entries), icons, maxIconReach);
    }

    public List<DrawCommand> commands(Prepared prepared, Set<String> hiddenLayerIds,
                                      WorldMapOverlayRenderer.View view) {
        Objects.requireNonNull(prepared, "prepared");
        Set<String> hidden = Set.copyOf(hiddenLayerIds);
        Objects.requireNonNull(view, "view");
        Bounds viewport = viewportBounds(view);
        Bounds candidateViewport = expandedBounds(viewport, scaledReach(prepared.maximumIconReach(), view.scale()));
        List<PreparedMarker> candidates = prepared.index().query(candidateViewport).stream()
                .map(SpatialIndex.Entry::value).sorted(Comparator.comparingInt(PreparedMarker::drawOrdinal)).toList();
        List<DrawCommand> commands = new ArrayList<>(candidates.size());
        PolygonRenderCache.Lod lod = lod(view.scale());
        for (PreparedMarker item : candidates) {
            if (hidden.contains(item.layer().id())) continue;
            MapMarker marker = item.marker();
            if (!(marker instanceof IconMarker) && !item.bounds().intersects(viewport)) continue;
            if (marker instanceof PolygonMarker polygon) {
                Optional<ScaledStroke.Plan> stroke = ScaledStroke.plan(
                        argb(polygon.style().strokeColor(), polygon.style().opacity()),
                        polygon.style().weight(), view.scale());
                int strokeArgb = 0;
                double strokeWidth = 1.0;
                if (stroke.isPresent()) {
                    ScaledStroke.Plan drawableStroke = stroke.orElseThrow();
                    strokeArgb = drawableStroke.argb();
                    strokeWidth = drawableStroke.width();
                }
                for (PolygonRenderCache.PartGeometry part : item.polygon().parts(lod)) {
                    List<List<ScreenPoint>> rings = new ArrayList<>();
                    rings.add(project(part.outer().points(), view));
                    part.holes().forEach(hole -> rings.add(project(hole.points(), view)));
                    commands.add(new PolygonCommand(item, rings, strokeArgb,
                            argb(polygon.style().fillColor(), polygon.style().fillOpacity()), strokeWidth));
                }
            } else if (marker instanceof PolylineMarker line) {
                Optional<ScaledStroke.Plan> stroke = ScaledStroke.plan(
                        argb(line.style().strokeColor(), line.style().opacity()),
                        line.style().weight(), view.scale());
                if (stroke.isEmpty()) continue;
                ScaledStroke.Plan drawableStroke = stroke.orElseThrow();
                commands.add(new PolylineCommand(item, project(line.points(), view),
                        drawableStroke.argb(), drawableStroke.width()));
            } else if (marker instanceof IconMarker icon) {
                ScreenPoint point = view.worldToScreen(icon.point().x(), icon.point().z());
                ScaledIconGeometry.Plan geometry = ScaledIconGeometry.plan(
                        item.layer().id(), icon, point, view.scale()).orElse(null);
                if (geometry == null || !intersectsViewport(geometry, view)) continue;
                commands.add(new IconCommand(item, geometry.left(), geometry.top(),
                        geometry.right(), geometry.bottom(), icon.icon()));
            }
        }
        return List.copyOf(commands);
    }

    public Optional<Hit> hitTest(Prepared prepared, Set<String> hiddenLayerIds,
                                 double screenX, double screenY,
                                 WorldMapOverlayRenderer.View view) {
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) return Optional.empty();
        MapPoint2D world = view.screenToWorld(screenX, screenY);
        Bounds query = expandedBounds(new Bounds(world.x(), world.z(), world.x(), world.z()),
                scaledReach(prepared.maximumIconReach(), view.scale()));
        List<PreparedMarker> candidates = new ArrayList<>(prepared.index().query(query).stream()
                .map(SpatialIndex.Entry::value).toList());
        candidates.sort(Comparator.comparingInt(PreparedMarker::drawOrdinal).reversed());
        Set<String> hidden = Set.copyOf(hiddenLayerIds);
        for (PreparedMarker item : candidates) {
            if (hidden.contains(item.layer().id())) continue;
            if (hits(item, screenX, screenY, world, view)) {
                return Optional.of(new Hit(item.layer(), item.marker()));
            }
        }
        return Optional.empty();
    }

    private static boolean hits(PreparedMarker item, double screenX, double screenY,
                                MapPoint2D world, WorldMapOverlayRenderer.View view) {
        if (item.marker() instanceof PolygonMarker) {
            return item.bounds().contains(world.x(), world.z()) && item.polygon().contains(world.x(), world.z());
        }
        if (item.marker() instanceof PolylineMarker line) {
            double tolerance = Math.max(MIN_SCREEN_HIT_RADIUS, line.style().weight() * 0.5);
            List<ScreenPoint> points = project(line.points(), view);
            for (int index = 1; index < points.size(); index++) {
                if (segmentDistance(screenX, screenY, points.get(index - 1), points.get(index)) <= tolerance) return true;
            }
            return false;
        }
        IconMarker icon = (IconMarker) item.marker();
        ScreenPoint point = view.worldToScreen(icon.point().x(), icon.point().z());
        ScaledIconGeometry.Plan geometry = ScaledIconGeometry.plan(
                item.layer().id(), icon, point, view.scale()).orElse(null);
        if (geometry == null) return false;
        boolean insideScaledRectangle = screenX >= geometry.left() && screenX <= geometry.right()
                && screenY >= geometry.top() && screenY <= geometry.bottom();
        return (geometry.hasPositiveArea() && insideScaledRectangle)
                || Math.hypot(screenX - point.x(), screenY - point.y()) <= MIN_SCREEN_HIT_RADIUS;
    }

    private static boolean intersectsViewport(ScaledIconGeometry.Plan geometry,
                                              WorldMapOverlayRenderer.View view) {
        return geometry.right() >= 0 && geometry.left() <= view.width()
                && geometry.bottom() >= 0 && geometry.top() <= view.height();
    }

    private static double scaledReach(double reach, double scale) {
        double scaled = reach / scale;
        return Double.isFinite(scaled) ? scaled : Double.MAX_VALUE;
    }

    private static Bounds expandedBounds(Bounds bounds, double amount) {
        return new Bounds(saturatedSubtract(bounds.minX(), amount),
                saturatedSubtract(bounds.minZ(), amount),
                saturatedAdd(bounds.maxX(), amount), saturatedAdd(bounds.maxZ(), amount));
    }

    private static Bounds viewportBounds(WorldMapOverlayRenderer.View view) {
        double halfWorldWidth = finiteOrMaximum(view.width() * 0.5 / view.scale());
        double halfWorldHeight = finiteOrMaximum(view.height() * 0.5 / view.scale());
        return new Bounds(saturatedSubtract(view.centerX(), halfWorldWidth),
                saturatedSubtract(view.centerZ(), halfWorldHeight),
                saturatedAdd(view.centerX(), halfWorldWidth),
                saturatedAdd(view.centerZ(), halfWorldHeight));
    }

    private static double finiteOrMaximum(double value) {
        return Double.isFinite(value) ? value : Double.MAX_VALUE;
    }

    private static double saturatedSubtract(double value, double amount) {
        double result = value - amount;
        return Double.isFinite(result) ? result : -Double.MAX_VALUE;
    }

    private static double saturatedAdd(double value, double amount) {
        double result = value + amount;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private static double segmentDistance(double x, double y, ScreenPoint start, ScreenPoint end) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) return Math.hypot(x - start.x(), y - start.y());
        double t = Math.max(0, Math.min(1, ((x - start.x()) * dx + (y - start.y()) * dy) / lengthSquared));
        return Math.hypot(x - (start.x() + t * dx), y - (start.y() + t * dy));
    }

    static PolygonRenderCache.Lod lod(double scale) {
        return scale < 0.05 ? PolygonRenderCache.Lod.COARSE
                : scale < 0.20 ? PolygonRenderCache.Lod.MEDIUM : PolygonRenderCache.Lod.FINE;
    }

    private static List<ScreenPoint> project(List<MapPoint> points, WorldMapOverlayRenderer.View view) {
        return points.stream().map(point -> view.worldToScreen(point.x(), point.z())).toList();
    }

    private static Bounds bounds(MapMarker marker, PolygonRenderCache polygon) {
        if (polygon != null) return polygon.bounds();
        if (marker instanceof IconMarker icon) {
            return new Bounds(icon.point().x(), icon.point().z(), icon.point().x(), icon.point().z());
        }
        List<MapPoint> points = ((PolylineMarker) marker).points();
        if (points.isEmpty()) return null;
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (MapPoint point : points) {
            minX = Math.min(minX, point.x()); minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x()); maxZ = Math.max(maxZ, point.z());
        }
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    private static int argb(int rgb, double opacity) {
        return ((int) Math.round(opacity * 255.0) << 24) | rgb;
    }

    private record LayerSource(MapLayer layer, int sourceOrdinal) { }
}
