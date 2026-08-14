package com.murphypotato.simmctoolset.internal.map.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PolygonScanlineRasterizer {
    private static final Plan EMPTY_PLAN = new Plan(List.of(), 0, 0, 0, 0);

    private PolygonScanlineRasterizer() {
    }

    public static Plan plan(List<? extends List<ScreenPoint>> rings,
                            int left, int top, int right, int bottom) {
        if (rings == null || rings.isEmpty() || right <= left || bottom <= top) {
            return EMPTY_PLAN;
        }

        List<ActiveEdge> preparedEdges = new ArrayList<>();
        long ringEdgeCount = 0;
        int firstScanRow = bottom;
        int lastScanRowExclusive = top;

        for (List<ScreenPoint> ring : rings) {
            if (ring == null || ring.isEmpty()) {
                continue;
            }
            ringEdgeCount += ring.size();
            for (int index = 0; index < ring.size(); index++) {
                ScreenPoint firstPoint = ring.get(index);
                ScreenPoint secondPoint = ring.get((index + 1) % ring.size());
                if (firstPoint == null || secondPoint == null || firstPoint.y() == secondPoint.y()) {
                    continue;
                }

                ScreenPoint minPoint = firstPoint.y() < secondPoint.y() ? firstPoint : secondPoint;
                ScreenPoint maxPoint = firstPoint.y() < secondPoint.y() ? secondPoint : firstPoint;
                int rawFirst = ceilToInt(minPoint.y() - 0.5);
                int rawLastExclusive = ceilToInt(maxPoint.y() - 0.5);
                double slope = (maxPoint.x() - minPoint.x()) / (maxPoint.y() - minPoint.y());
                double rawFirstX = minPoint.x() + ((rawFirst + 0.5) - minPoint.y()) * slope;
                int clippedFirst = Math.max(rawFirst, top);
                int clippedLastExclusive = Math.min(rawLastExclusive, bottom);
                if (clippedFirst >= clippedLastExclusive) {
                    continue;
                }

                double clippedFirstX = rawFirstX + ((double) clippedFirst - rawFirst) * slope;
                preparedEdges.add(new ActiveEdge(clippedFirst, clippedLastExclusive, clippedFirstX, slope));
                firstScanRow = Math.min(firstScanRow, clippedFirst);
                lastScanRowExclusive = Math.max(lastScanRowExclusive, clippedLastExclusive);
            }
        }

        if (preparedEdges.isEmpty()) {
            return EMPTY_PLAN;
        }

        long scannedRows = (long) lastScanRowExclusive - firstScanRow;
        long legacyEdgeRowChecks = ringEdgeCount * scannedRows;
        int rowCount = Math.toIntExact(scannedRows);
        @SuppressWarnings("unchecked")
        List<ActiveEdge>[] startingEdges = (List<ActiveEdge>[]) new List<?>[rowCount];
        for (ActiveEdge edge : preparedEdges) {
            int bucketIndex = edge.startRow - firstScanRow;
            List<ActiveEdge> bucket = startingEdges[bucketIndex];
            if (bucket == null) {
                bucket = new ArrayList<>();
                startingEdges[bucketIndex] = bucket;
            }
            bucket.add(edge);
        }

        long edgeActivations = 0;
        long activeEdgeVisits = 0;
        List<ActiveEdge> activeEdges = new ArrayList<>(preparedEdges.size());
        double[] intersections = new double[preparedEdges.size()];
        int[] spanLefts = new int[preparedEdges.size() / 2];
        int[] spanRights = new int[preparedEdges.size() / 2];
        Map<Span, OpenRectangle> previousRectangles = new LinkedHashMap<>();
        Map<Span, OpenRectangle> nextRectangles = new LinkedHashMap<>();
        List<FillRectangle> rectangles = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            int row = firstScanRow + rowIndex;
            List<ActiveEdge> starting = startingEdges[rowIndex];
            if (starting != null) {
                activeEdges.addAll(starting);
                edgeActivations += starting.size();
            }
            for (Iterator<ActiveEdge> iterator = activeEdges.iterator(); iterator.hasNext();) {
                if (iterator.next().lastRowExclusive <= row) {
                    iterator.remove();
                }
            }
            activeEdgeVisits += activeEdges.size();

            int intersectionCount = activeEdges.size();
            for (int index = 0; index < intersectionCount; index++) {
                intersections[index] = activeEdges.get(index).xAt(row);
            }
            Arrays.sort(intersections, 0, intersectionCount);

            int spanCount = 0;
            for (int index = 0; index + 1 < intersectionCount; index += 2) {
                int spanLeft = Math.max(left, floorToInt(intersections[index]));
                int spanRight = Math.min(right, ceilToInt(intersections[index + 1]));
                if (spanRight > spanLeft) {
                    if (spanCount > 0 && spanLeft <= spanRights[spanCount - 1]) {
                        spanRights[spanCount - 1] = Math.max(spanRights[spanCount - 1], spanRight);
                    } else {
                        spanLefts[spanCount] = spanLeft;
                        spanRights[spanCount] = spanRight;
                        spanCount++;
                    }
                }
            }

            for (int spanIndex = 0; spanIndex < spanCount; spanIndex++) {
                Span span = new Span(spanLefts[spanIndex], spanRights[spanIndex]);
                OpenRectangle open = previousRectangles.remove(span);
                nextRectangles.put(span, open == null
                        ? new OpenRectangle(span.left, row, span.right, row + 1)
                        : open.extendThrough(row + 1));
            }
            for (OpenRectangle completed : previousRectangles.values()) {
                rectangles.add(completed.toFillRectangle());
            }
            previousRectangles.clear();
            Map<Span, OpenRectangle> swap = previousRectangles;
            previousRectangles = nextRectangles;
            nextRectangles = swap;
        }

        for (OpenRectangle completed : previousRectangles.values()) {
            rectangles.add(completed.toFillRectangle());
        }
        return new Plan(rectangles, edgeActivations, activeEdgeVisits, scannedRows, legacyEdgeRowChecks);
    }

    private static int ceilToInt(double value) {
        return (int) Math.ceil(value);
    }

    private static int floorToInt(double value) {
        return (int) Math.floor(value);
    }

    public record FillRectangle(int left, int top, int right, int bottom) {
    }

    public record Plan(List<FillRectangle> rectangles,
                       long edgeActivations,
                       long activeEdgeVisits,
                       long scannedRows,
                       long legacyEdgeRowChecks) {
        public Plan {
            rectangles = List.copyOf(rectangles);
        }
    }

    private record Span(int left, int right) {
    }

    private static final class ActiveEdge {
        private final int startRow;
        private final int lastRowExclusive;
        private final double startX;
        private final double slope;

        private ActiveEdge(int startRow, int lastRowExclusive, double startX, double slope) {
            this.startRow = startRow;
            this.lastRowExclusive = lastRowExclusive;
            this.startX = startX;
            this.slope = slope;
        }

        private double xAt(int row) {
            return Math.fma((double) row - startRow, slope, startX);
        }
    }

    private static final class OpenRectangle {
        private final int left;
        private final int top;
        private final int right;
        private int bottom;

        private OpenRectangle(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        private OpenRectangle extendThrough(int nextBottom) {
            bottom = nextBottom;
            return this;
        }

        private FillRectangle toFillRectangle() {
            return new FillRectangle(left, top, right, bottom);
        }
    }
}
