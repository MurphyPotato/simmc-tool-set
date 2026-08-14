/*
 * Geometry LOD cleaning is derived in part from JR1258/EarthMC-Map-Addon
 * WorldMapRenderer, upstream commit c85c5003855eb47868868b931624b951cffba74e.
 * Changes: generalized integer Towny rings to immutable double-precision squaremap
 * multipart polygons with holes, three LODs, centroid/intersection/hit-test support,
 * and explicit resource bounds. Licensed under Apache-2.0; see THIRD_PARTY_NOTICES.md.
 */
package com.murphypotato.simmctoolset.internal.map.render;

import com.murphypotato.simmctoolset.internal.map.model.MapPoint;
import com.murphypotato.simmctoolset.internal.map.model.PolygonMarker;
import com.murphypotato.simmctoolset.internal.map.model.PolygonPart;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PolygonRenderCache {
    private static final int MAX_POINTS_PER_MARKER = 1_000_000;
    private static final double BOUNDARY_EPSILON = 1.0e-9;
    private static final double[] LOD_GRID = {16.0, 4.0, 0.0};
    static final int MAX_TOPOLOGY_COMPARISONS = 100_000;

    public enum Lod { COARSE, MEDIUM, FINE }

    public record RingGeometry(List<MapPoint> points, Bounds bounds) {
        public RingGeometry {
            points = List.copyOf(points);
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    public record PartGeometry(RingGeometry outer, List<RingGeometry> holes, Bounds bounds, MapPoint2D center) {
        public PartGeometry {
            Objects.requireNonNull(outer, "outer");
            holes = List.copyOf(holes);
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(center, "center");
        }
    }

    record TopologyProbe(boolean fellBack, boolean budgetExhausted, int comparisons) { }

    private record SimplificationAttempt(
            PartGeometry geometry, boolean fellBack, boolean budgetExhausted, int comparisons
    ) { }

    private final String renderSignature;
    private final Map<Lod, List<PartGeometry>> lodParts;
    private final Bounds bounds;
    private final MapPoint2D center;

    private PolygonRenderCache(
            String renderSignature, Map<Lod, List<PartGeometry>> lodParts, Bounds bounds, MapPoint2D center
    ) {
        this.renderSignature = renderSignature;
        this.lodParts = Map.copyOf(lodParts);
        this.bounds = bounds;
        this.center = center;
    }

    public static PolygonRenderCache build(PolygonMarker marker, String renderSignature) {
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(renderSignature, "renderSignature");
        if (renderSignature.isEmpty()) {
            throw new IllegalArgumentException("Render signature must not be empty");
        }
        validatePointCount(marker);

        EnumMap<Lod, List<PartGeometry>> levels = new EnumMap<>(Lod.class);
        for (Lod lod : Lod.values()) {
            levels.put(lod, buildParts(marker.parts(), LOD_GRID[lod.ordinal()]));
        }
        List<PartGeometry> fine = levels.get(Lod.FINE);
        if (fine.isEmpty()) {
            throw new IllegalArgumentException("Polygon has no usable parts");
        }
        Bounds markerBounds = union(fine.stream().map(PartGeometry::bounds).toList());
        MapPoint2D markerCenter = weightedCenter(fine, markerBounds);
        return new PolygonRenderCache(renderSignature, levels, markerBounds, markerCenter);
    }

    /** Computes the same fine-geometry center as {@link #build} without constructing other LODs. */
    public static MapPoint2D centerOf(PolygonMarker marker) {
        Objects.requireNonNull(marker, "marker");
        validatePointCount(marker);
        List<PartGeometry> fine = buildParts(marker.parts(), 0);
        if (fine.isEmpty() || fine.stream().noneMatch(part -> hasNonCollinearVertices(part.outer().points()))) {
            throw new IllegalArgumentException("Polygon has no usable parts");
        }
        Bounds markerBounds = union(fine.stream().map(PartGeometry::bounds).toList());
        return weightedCenter(fine, markerBounds);
    }

    public static PolygonRenderCache reuseOrBuild(
            PolygonMarker marker, String renderSignature, PolygonRenderCache previous
    ) {
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(renderSignature, "renderSignature");
        if (previous != null && Objects.equals(previous.renderSignature, renderSignature)) {
            return previous;
        }
        return build(marker, renderSignature);
    }

    public String renderSignature() {
        return renderSignature;
    }

    public List<PartGeometry> parts(Lod lod) {
        return lodParts.get(Objects.requireNonNull(lod, "lod"));
    }

    public Bounds bounds() {
        return bounds;
    }

    public MapPoint2D center() {
        return center;
    }

    public boolean contains(double x, double z) {
        if (!bounds.contains(x, z)) {
            return false;
        }
        for (PartGeometry part : parts(Lod.FINE)) {
            if (!part.bounds().contains(x, z)) {
                continue;
            }
            if (!hasNonCollinearVertices(part.outer().points())) {
                continue;
            }
            PointRelation outer = pointRelation(part.outer().points(), x, z);
            if (outer == PointRelation.OUTSIDE) {
                continue;
            }
            boolean excluded = false;
            for (RingGeometry hole : part.holes()) {
                if (hole.bounds().contains(x, z)
                        && pointRelation(hole.points(), x, z) != PointRelation.OUTSIDE) {
                    excluded = true;
                    break;
                }
            }
            if (!excluded) {
                return true;
            }
        }
        return false;
    }

    public boolean intersects(Bounds viewport) {
        Objects.requireNonNull(viewport, "viewport");
        if (!bounds.intersects(viewport)) {
            return false;
        }
        for (PartGeometry part : parts(Lod.FINE)) {
            if (!part.bounds().intersects(viewport)) {
                continue;
            }
            if (!hasNonCollinearVertices(part.outer().points())) {
                continue;
            }
            if (filledPartIntersects(part, viewport)) {
                return true;
            }
        }
        return false;
    }

    private static void validatePointCount(PolygonMarker marker) {
        int pointCount = 0;
        for (PolygonPart part : marker.parts()) {
            for (List<MapPoint> ring : part.rings()) {
                pointCount = Math.addExact(pointCount, ring.size());
                if (pointCount > MAX_POINTS_PER_MARKER) {
                    throw new IllegalArgumentException("Polygon exceeds point limit");
                }
            }
        }
    }

    private static boolean filledPartIntersects(PartGeometry part, Bounds viewport) {
        List<MapPoint> outer = part.outer().points();
        for (MapPoint point : outer) {
            if (viewport.contains(point.x(), point.z()) && !insideAnyHole(part.holes(), point.x(), point.z())) {
                return true;
            }
        }
        if (ringIntersectsRectangle(outer, viewport)) {
            return true;
        }
        for (RingGeometry hole : part.holes()) {
            if (hole.bounds().intersects(viewport) && ringEntersOpenRectangle(hole.points(), viewport)) {
                return true;
            }
        }
        double[][] corners = {
                {viewport.minX(), viewport.minZ()}, {viewport.maxX(), viewport.minZ()},
                {viewport.maxX(), viewport.maxZ()}, {viewport.minX(), viewport.maxZ()}
        };
        for (double[] corner : corners) {
            PointRelation relation = pointRelation(outer, corner[0], corner[1]);
            if (relation != PointRelation.OUTSIDE && !insideAnyHole(part.holes(), corner[0], corner[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean insideAnyHole(List<RingGeometry> holes, double x, double z) {
        for (RingGeometry hole : holes) {
            if (hole.bounds().contains(x, z) && pointRelation(hole.points(), x, z) != PointRelation.OUTSIDE) {
                return true;
            }
        }
        return false;
    }

    private static boolean ringIntersectsRectangle(List<MapPoint> ring, Bounds rectangle) {
        for (int index = 0; index + 1 < ring.size(); index++) {
            MapPoint first = ring.get(index);
            MapPoint second = ring.get(index + 1);
            if (segmentIntersects(first.x(), first.z(), second.x(), second.z(),
                    rectangle.minX(), rectangle.minZ(), rectangle.maxX(), rectangle.minZ())
                    || segmentIntersects(first.x(), first.z(), second.x(), second.z(),
                    rectangle.maxX(), rectangle.minZ(), rectangle.maxX(), rectangle.maxZ())
                    || segmentIntersects(first.x(), first.z(), second.x(), second.z(),
                    rectangle.maxX(), rectangle.maxZ(), rectangle.minX(), rectangle.maxZ())
                    || segmentIntersects(first.x(), first.z(), second.x(), second.z(),
                    rectangle.minX(), rectangle.maxZ(), rectangle.minX(), rectangle.minZ())) {
                return true;
            }
        }
        return false;
    }

    private static boolean ringEntersOpenRectangle(List<MapPoint> ring, Bounds rectangle) {
        double largestCoordinate = Math.max(
                Math.max(Math.abs(rectangle.minX()), Math.abs(rectangle.maxX())),
                Math.max(Math.abs(rectangle.minZ()), Math.abs(rectangle.maxZ())));
        double epsilon = Math.ulp(Math.max(1.0, largestCoordinate)) * 8.0;
        double minX = rectangle.minX() + epsilon;
        double minZ = rectangle.minZ() + epsilon;
        double maxX = rectangle.maxX() - epsilon;
        double maxZ = rectangle.maxZ() - epsilon;
        if (!Double.isFinite(minX) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxZ)
                || minX >= maxX || minZ >= maxZ) {
            return false;
        }
        for (int index = 0; index + 1 < ring.size(); index++) {
            MapPoint first = ring.get(index);
            MapPoint second = ring.get(index + 1);
            if (segmentIntersectsClosedRectangleInterior(first, second, minX, minZ, maxX, maxZ)) {
                return true;
            }
        }
        return false;
    }

    /** Liang-Barsky clipping against a rectangle shrunken into the requested open interior. */
    private static boolean segmentIntersectsClosedRectangleInterior(
            MapPoint first, MapPoint second, double minX, double minZ, double maxX, double maxZ
    ) {
        double deltaX = second.x() - first.x();
        double deltaZ = second.z() - first.z();
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaZ)
                || deltaX == 0.0 && deltaZ == 0.0) {
            return false;
        }
        double[] parameterRange = {0.0, 1.0};
        return clipParameter(-deltaX, first.x() - minX, parameterRange)
                && clipParameter(deltaX, maxX - first.x(), parameterRange)
                && clipParameter(-deltaZ, first.z() - minZ, parameterRange)
                && clipParameter(deltaZ, maxZ - first.z(), parameterRange)
                && parameterRange[0] <= parameterRange[1];
    }

    private static boolean clipParameter(double direction, double distance, double[] range) {
        if (!Double.isFinite(direction) || !Double.isFinite(distance)) {
            return false;
        }
        if (direction == 0.0) {
            return distance >= 0.0;
        }
        double parameter = distance / direction;
        if (!Double.isFinite(parameter)) {
            return false;
        }
        if (direction < 0.0) {
            if (parameter > range[1]) {
                return false;
            }
            range[0] = Math.max(range[0], parameter);
        } else {
            if (parameter < range[0]) {
                return false;
            }
            range[1] = Math.min(range[1], parameter);
        }
        return true;
    }

    private static boolean segmentIntersects(
            double ax, double az, double bx, double bz, double cx, double cz, double dx, double dz
    ) {
        double o1 = orientation(ax, az, bx, bz, cx, cz);
        double o2 = orientation(ax, az, bx, bz, dx, dz);
        double o3 = orientation(cx, cz, dx, dz, ax, az);
        double o4 = orientation(cx, cz, dx, dz, bx, bz);
        if (oppositeSigns(o1, o2) && oppositeSigns(o3, o4)) {
            return true;
        }
        return Math.abs(o1) <= BOUNDARY_EPSILON && onSegment(ax, az, bx, bz, cx, cz)
                || Math.abs(o2) <= BOUNDARY_EPSILON && onSegment(ax, az, bx, bz, dx, dz)
                || Math.abs(o3) <= BOUNDARY_EPSILON && onSegment(cx, cz, dx, dz, ax, az)
                || Math.abs(o4) <= BOUNDARY_EPSILON && onSegment(cx, cz, dx, dz, bx, bz);
    }

    private static boolean oppositeSigns(double first, double second) {
        return first > BOUNDARY_EPSILON && second < -BOUNDARY_EPSILON
                || first < -BOUNDARY_EPSILON && second > BOUNDARY_EPSILON;
    }

    private static double orientation(double ax, double az, double bx, double bz, double cx, double cz) {
        return (bx - ax) * (cz - az) - (bz - az) * (cx - ax);
    }

    private static boolean onSegment(double ax, double az, double bx, double bz, double x, double z) {
        return x >= Math.min(ax, bx) - BOUNDARY_EPSILON && x <= Math.max(ax, bx) + BOUNDARY_EPSILON
                && z >= Math.min(az, bz) - BOUNDARY_EPSILON && z <= Math.max(az, bz) + BOUNDARY_EPSILON;
    }

    private enum PointRelation { OUTSIDE, INSIDE, BOUNDARY }

    private static PointRelation pointRelation(List<MapPoint> ring, double x, double z) {
        boolean inside = false;
        for (int firstIndex = 0, secondIndex = ring.size() - 1;
             firstIndex < ring.size(); secondIndex = firstIndex++) {
            MapPoint first = ring.get(firstIndex);
            MapPoint second = ring.get(secondIndex);
            if (Math.abs(orientation(second.x(), second.z(), first.x(), first.z(), x, z))
                    <= BOUNDARY_EPSILON && onSegment(second.x(), second.z(), first.x(), first.z(), x, z)) {
                return PointRelation.BOUNDARY;
            }
            if ((first.z() > z) != (second.z() > z)) {
                double crossingX = (second.x() - first.x()) * (z - first.z())
                        / (second.z() - first.z()) + first.x();
                if (x < crossingX) {
                    inside = !inside;
                }
            }
        }
        return inside ? PointRelation.INSIDE : PointRelation.OUTSIDE;
    }

    private static List<PartGeometry> buildParts(List<PolygonPart> source, double grid) {
        List<PartGeometry> result = new ArrayList<>(source.size());
        for (PolygonPart part : source) {
            PartGeometry original = buildPart(part, 0);
            if (original == null) {
                continue;
            }
            if (grid == 0) {
                result.add(original);
                continue;
            }
            result.add(attemptSimplification(part, original, grid).geometry());
        }
        return List.copyOf(result);
    }

    static TopologyProbe topologyProbe(PolygonPart part, Lod lod) {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(lod, "lod");
        PartGeometry original = buildPart(part, 0);
        if (original == null || lod == Lod.FINE) {
            return new TopologyProbe(original == null, false, 0);
        }
        SimplificationAttempt attempt = attemptSimplification(part, original, LOD_GRID[lod.ordinal()]);
        return new TopologyProbe(attempt.fellBack(), attempt.budgetExhausted(), attempt.comparisons());
    }

    private static SimplificationAttempt attemptSimplification(
            PolygonPart source, PartGeometry original, double grid
    ) {
        ComparisonBudget budget = new ComparisonBudget();
        if (!hasValidPartTopology(original, budget)
                || !allRingsHaveValidSimplification(source, grid)) {
            return new SimplificationAttempt(original, true, budget.exhausted(), budget.comparisons());
        }
        PartGeometry simplified = buildPart(source, grid);
        if (simplified == null || !hasValidPartTopology(simplified, budget)) {
            return new SimplificationAttempt(original, true, budget.exhausted(), budget.comparisons());
        }
        return new SimplificationAttempt(simplified, false, budget.exhausted(), budget.comparisons());
    }

    private static boolean allRingsHaveValidSimplification(PolygonPart part, double grid) {
        for (List<MapPoint> ring : part.rings()) {
            if (ring.size() < 3 || ring.stream().distinct().limit(3).count() < 3
                    || !isValidSimplifiedRing(cleanAndClose(ring, grid))) {
                return false;
            }
        }
        return true;
    }

    private static PartGeometry buildPart(PolygonPart part, double grid) {
        if (part.rings().isEmpty()) {
            return null;
        }
        RingGeometry outer = buildRing(part.rings().getFirst(), grid);
        if (outer == null) {
            return null;
        }
        List<RingGeometry> holes = new ArrayList<>(Math.max(0, part.rings().size() - 1));
        for (int index = 1; index < part.rings().size(); index++) {
            RingGeometry hole = buildRing(part.rings().get(index), grid);
            if (hole != null) {
                holes.add(hole);
            }
        }
        return new PartGeometry(outer, holes, outer.bounds(), weightedCenter(outer, holes, outer.bounds()));
    }

    /**
     * Validates only generated LOD candidates. Source rings keep squaremap's even-odd semantics,
     * including self-intersections, and are the whole-part fallback when simplification changes topology.
     */
    private static boolean hasValidPartTopology(PartGeometry part, ComparisonBudget budget) {
        if (!hasNonCollinearVertices(part.outer().points())) {
            return false;
        }
        if (!hasSimpleRingTopology(part.outer().points(), budget)) {
            return false;
        }
        for (RingGeometry hole : part.holes()) {
            if (!hasSimpleRingTopology(hole.points(), budget)
                    || !ringStrictlyInside(hole.points(), part.outer().points(), budget)) {
                return false;
            }
        }
        for (int first = 0; first < part.holes().size(); first++) {
            List<MapPoint> firstRing = part.holes().get(first).points();
            for (int second = first + 1; second < part.holes().size(); second++) {
                List<MapPoint> secondRing = part.holes().get(second).points();
                if (ringsIntersectOrTouch(firstRing, secondRing, budget)
                        || pointRelation(firstRing, secondRing.getFirst().x(), secondRing.getFirst().z())
                        != PointRelation.OUTSIDE
                        || pointRelation(secondRing, firstRing.getFirst().x(), firstRing.getFirst().z())
                        != PointRelation.OUTSIDE) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean ringStrictlyInside(
            List<MapPoint> inner, List<MapPoint> outer, ComparisonBudget budget
    ) {
        if (ringsIntersectOrTouch(inner, outer, budget)) {
            return false;
        }
        for (int index = 0; index + 1 < inner.size(); index++) {
            MapPoint point = inner.get(index);
            if (pointRelation(outer, point.x(), point.z()) != PointRelation.INSIDE) {
                return false;
            }
        }
        return true;
    }

    private static boolean ringsIntersectOrTouch(
            List<MapPoint> first, List<MapPoint> second, ComparisonBudget budget
    ) {
        for (int firstIndex = 0; firstIndex + 1 < first.size(); firstIndex++) {
            MapPoint firstStart = first.get(firstIndex);
            MapPoint firstEnd = first.get(firstIndex + 1);
            for (int secondIndex = 0; secondIndex + 1 < second.size(); secondIndex++) {
                MapPoint secondStart = second.get(secondIndex);
                MapPoint secondEnd = second.get(secondIndex + 1);
                if (!budget.tryComparison()) {
                    return true;
                }
                if (segmentBoundsOverlap(firstStart, firstEnd, secondStart, secondEnd)
                        && segmentIntersects(firstStart.x(), firstStart.z(), firstEnd.x(), firstEnd.z(),
                        secondStart.x(), secondStart.z(), secondEnd.x(), secondEnd.z())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasSimpleRingTopology(List<MapPoint> ring, ComparisonBudget budget) {
        if (ring.size() < 4 || !ring.getFirst().equals(ring.getLast())) {
            return false;
        }
        java.util.HashSet<MapPoint> vertices = new java.util.HashSet<>();
        for (int index = 0; index + 1 < ring.size(); index++) {
            if (!vertices.add(ring.get(index))) {
                return false;
            }
        }
        int segmentCount = ring.size() - 1;
        for (int first = 0; first < segmentCount; first++) {
            for (int second = first + 1; second < segmentCount; second++) {
                if (second == first + 1 || first == 0 && second == segmentCount - 1) {
                    continue;
                }
                if (!budget.tryComparison()) {
                    return false;
                }
                MapPoint firstStart = ring.get(first);
                MapPoint firstEnd = ring.get(first + 1);
                MapPoint secondStart = ring.get(second);
                MapPoint secondEnd = ring.get(second + 1);
                if (segmentBoundsOverlap(firstStart, firstEnd, secondStart, secondEnd)
                        && segmentIntersects(firstStart.x(), firstStart.z(), firstEnd.x(), firstEnd.z(),
                        secondStart.x(), secondStart.z(), secondEnd.x(), secondEnd.z())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean segmentBoundsOverlap(
            MapPoint firstStart, MapPoint firstEnd, MapPoint secondStart, MapPoint secondEnd
    ) {
        return Math.max(firstStart.x(), firstEnd.x()) + BOUNDARY_EPSILON
                >= Math.min(secondStart.x(), secondEnd.x())
                && Math.max(secondStart.x(), secondEnd.x()) + BOUNDARY_EPSILON
                >= Math.min(firstStart.x(), firstEnd.x())
                && Math.max(firstStart.z(), firstEnd.z()) + BOUNDARY_EPSILON
                >= Math.min(secondStart.z(), secondEnd.z())
                && Math.max(secondStart.z(), secondEnd.z()) + BOUNDARY_EPSILON
                >= Math.min(firstStart.z(), firstEnd.z());
    }

    private static final class ComparisonBudget {
        private int comparisons;
        private boolean exhausted;

        private boolean tryComparison() {
            if (comparisons >= MAX_TOPOLOGY_COMPARISONS) {
                exhausted = true;
                return false;
            }
            comparisons++;
            return true;
        }

        private int comparisons() {
            return comparisons;
        }

        private boolean exhausted() {
            return exhausted;
        }
    }

    private static boolean hasNonCollinearVertices(List<MapPoint> ring) {
        if (ring.size() < 4) {
            return false;
        }
        MapPoint first = ring.getFirst();
        MapPoint second = null;
        for (int index = 1; index + 1 < ring.size(); index++) {
            if (!ring.get(index).equals(first)) {
                second = ring.get(index);
                break;
            }
        }
        if (second == null) {
            return false;
        }
        for (int index = 1; index + 1 < ring.size(); index++) {
            MapPoint candidate = ring.get(index);
            double cross = orientation(first.x(), first.z(), second.x(), second.z(),
                    candidate.x(), candidate.z());
            double scale = Math.max(
                    Math.max(Math.abs(second.x() - first.x()), Math.abs(second.z() - first.z())),
                    Math.max(Math.abs(candidate.x() - first.x()), Math.abs(candidate.z() - first.z())));
            double scaleSquared = scale * scale;
            double epsilon = Double.isFinite(scaleSquared)
                    ? Math.max(Double.MIN_VALUE, Math.ulp(scaleSquared) * 16.0)
                    : Double.POSITIVE_INFINITY;
            if (Double.isFinite(cross) && Math.abs(cross) > epsilon) {
                return true;
            }
        }
        return false;
    }

    private static RingGeometry buildRing(List<MapPoint> source, double grid) {
        if (source.size() < 3 || source.stream().distinct().count() < 3) {
            return null;
        }
        List<MapPoint> points;
        if (grid == 0) {
            points = close(source);
        } else {
            points = cleanAndClose(source, grid);
            if (!isValidSimplifiedRing(points)) {
                points = close(source);
            }
        }
        return new RingGeometry(points, boundsOf(points));
    }

    private static boolean isValidSimplifiedRing(List<MapPoint> points) {
        if (points.size() < 4 || !points.getFirst().equals(points.getLast())
                || points.stream().limit(points.size() - 1).distinct().count() < 3) {
            return false;
        }
        Bounds ringBounds = boundsOf(points);
        double width = ringBounds.maxX() - ringBounds.minX();
        double height = ringBounds.maxZ() - ringBounds.minZ();
        double scale = Math.max(width, height);
        double scaleSquared = scale * scale;
        if (!Double.isFinite(scaleSquared)) {
            return false;
        }
        double areaEpsilon = Math.max(Double.MIN_VALUE, Math.ulp(scaleSquared) * 16.0);
        double signedArea = translatedSignedArea(points);
        return Double.isFinite(signedArea) && Math.abs(signedArea) > areaEpsilon;
    }

    private static double translatedSignedArea(List<MapPoint> points) {
        MapPoint origin = points.getFirst();
        double twiceArea = 0;
        for (int index = 0; index + 1 < points.size(); index++) {
            MapPoint first = points.get(index);
            MapPoint second = points.get(index + 1);
            double firstX = first.x() - origin.x();
            double firstZ = first.z() - origin.z();
            double secondX = second.x() - origin.x();
            double secondZ = second.z() - origin.z();
            twiceArea += firstX * secondZ - secondX * firstZ;
        }
        return twiceArea / 2.0;
    }

    private static List<MapPoint> close(List<MapPoint> source) {
        ArrayList<MapPoint> closed = new ArrayList<>(source.size() + 1);
        closed.addAll(source);
        if (!source.getFirst().equals(source.getLast())) {
            closed.add(source.getFirst());
        }
        return List.copyOf(closed);
    }

    /* Adapted from upstream cleanRectilinear/snap: doubles and arbitrary squaremap polygons are supported. */
    private static List<MapPoint> cleanAndClose(List<MapPoint> source, double grid) {
        ArrayList<MapPoint> snapped = new ArrayList<>(source.size());
        for (MapPoint point : source) {
            MapPoint candidate = new MapPoint(snap(point.x(), grid), snap(point.z(), grid));
            if (snapped.isEmpty() || !snapped.getLast().equals(candidate)) {
                snapped.add(candidate);
            }
        }
        while (snapped.size() > 1 && snapped.getFirst().equals(snapped.getLast())) {
            snapped.removeLast();
        }
        boolean changed = true;
        while (changed && snapped.size() > 3) {
            changed = false;
            ArrayList<MapPoint> cleaned = new ArrayList<>(snapped.size());
            for (int index = 0; index < snapped.size(); index++) {
                MapPoint previous = snapped.get((index - 1 + snapped.size()) % snapped.size());
                MapPoint current = snapped.get(index);
                MapPoint next = snapped.get((index + 1) % snapped.size());
                if (Math.abs(orientation(previous.x(), previous.z(), current.x(), current.z(),
                        next.x(), next.z())) <= BOUNDARY_EPSILON) {
                    changed = true;
                } else {
                    cleaned.add(current);
                }
            }
            if (cleaned.size() < 3) {
                break;
            }
            snapped = cleaned;
        }
        return close(snapped);
    }

    private static double snap(double value, double grid) {
        double snapped = Math.floor(value / grid + 0.5) * grid;
        return snapped == -0.0 ? 0.0 : snapped;
    }

    private static Bounds boundsOf(List<MapPoint> points) {
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (MapPoint point : points) {
            minX = Math.min(minX, point.x());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxZ = Math.max(maxZ, point.z());
        }
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    private static Bounds union(List<Bounds> bounds) {
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Bounds item : bounds) {
            minX = Math.min(minX, item.minX());
            minZ = Math.min(minZ, item.minZ());
            maxX = Math.max(maxX, item.maxX());
            maxZ = Math.max(maxZ, item.maxZ());
        }
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    private static MapPoint2D weightedCenter(List<PartGeometry> parts, Bounds fallback) {
        double referenceX = fallback.centerX();
        double referenceZ = fallback.centerZ();
        KahanSum area = new KahanSum();
        KahanSum xMoment = new KahanSum();
        KahanSum zMoment = new KahanSum();
        for (PartGeometry part : parts) {
            AreaMoment moment = areaMoment(part.outer().points());
            double partArea = Math.abs(moment.area());
            area.add(partArea);
            xMoment.add((moment.centroidX() - referenceX) * partArea);
            zMoment.add((moment.centroidZ() - referenceZ) * partArea);
            for (RingGeometry hole : part.holes()) {
                AreaMoment holeMoment = areaMoment(hole.points());
                double holeArea = Math.abs(holeMoment.area());
                area.add(-holeArea);
                xMoment.add(-(holeMoment.centroidX() - referenceX) * holeArea);
                zMoment.add(-(holeMoment.centroidZ() - referenceZ) * holeArea);
            }
        }
        return Double.isFinite(area.value()) && area.value() > 0.0
                ? new MapPoint2D(referenceX + xMoment.value() / area.value(),
                referenceZ + zMoment.value() / area.value())
                : new MapPoint2D(fallback.centerX(), fallback.centerZ());
    }

    private static MapPoint2D weightedCenter(RingGeometry outer, List<RingGeometry> holes, Bounds fallback) {
        double referenceX = fallback.centerX();
        double referenceZ = fallback.centerZ();
        AreaMoment outerMoment = areaMoment(outer.points());
        KahanSum area = new KahanSum();
        KahanSum xMoment = new KahanSum();
        KahanSum zMoment = new KahanSum();
        double outerArea = Math.abs(outerMoment.area());
        area.add(outerArea);
        xMoment.add((outerMoment.centroidX() - referenceX) * outerArea);
        zMoment.add((outerMoment.centroidZ() - referenceZ) * outerArea);
        for (RingGeometry hole : holes) {
            AreaMoment holeMoment = areaMoment(hole.points());
            double holeArea = Math.abs(holeMoment.area());
            area.add(-holeArea);
            xMoment.add(-(holeMoment.centroidX() - referenceX) * holeArea);
            zMoment.add(-(holeMoment.centroidZ() - referenceZ) * holeArea);
        }
        return Double.isFinite(area.value()) && area.value() > 0.0
                ? new MapPoint2D(referenceX + xMoment.value() / area.value(),
                referenceZ + zMoment.value() / area.value())
                : new MapPoint2D(fallback.centerX(), fallback.centerZ());
    }

    private record AreaMoment(double area, double centroidX, double centroidZ) { }

    private static AreaMoment areaMoment(List<MapPoint> points) {
        MapPoint origin = points.getFirst();
        KahanSum twiceArea = new KahanSum();
        KahanSum xMoment = new KahanSum();
        KahanSum zMoment = new KahanSum();
        for (int index = 0; index + 1 < points.size(); index++) {
            MapPoint first = points.get(index);
            MapPoint second = points.get(index + 1);
            double firstX = first.x() - origin.x();
            double firstZ = first.z() - origin.z();
            double secondX = second.x() - origin.x();
            double secondZ = second.z() - origin.z();
            double cross = firstX * secondZ - secondX * firstZ;
            twiceArea.add(cross);
            xMoment.add((firstX + secondX) * cross);
            zMoment.add((firstZ + secondZ) * cross);
        }
        double area = twiceArea.value() / 2.0;
        Bounds pointBounds = boundsOf(points);
        double scale = Math.max(pointBounds.maxX() - pointBounds.minX(),
                pointBounds.maxZ() - pointBounds.minZ());
        double scaleSquared = scale * scale;
        double areaEpsilon = Double.isFinite(scaleSquared)
                ? Math.max(Double.MIN_VALUE, Math.ulp(scaleSquared) * 16.0)
                : Double.POSITIVE_INFINITY;
        if (!Double.isFinite(area) || Math.abs(area) <= areaEpsilon) {
            return new AreaMoment(0, 0, 0);
        }
        return new AreaMoment(area,
                origin.x() + xMoment.value() / (3.0 * twiceArea.value()),
                origin.z() + zMoment.value() / (3.0 * twiceArea.value()));
    }

    private static final class KahanSum {
        private double sum;
        private double correction;

        private void add(double value) {
            double adjusted = value - correction;
            double next = sum + adjusted;
            correction = (next - sum) - adjusted;
            sum = next;
        }

        private double value() {
            return sum;
        }
    }
}
