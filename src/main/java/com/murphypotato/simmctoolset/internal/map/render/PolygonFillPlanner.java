package com.murphypotato.simmctoolset.internal.map.render;

import java.util.ArrayList;
import java.util.List;

public final class PolygonFillPlanner {
    private static final Plan EMPTY = new Plan(0, List.of());

    private PolygonFillPlanner() {
    }

    public static Plan plan(List<? extends List<ScreenPoint>> rings, int fillArgb,
                            int left, int top, int right, int bottom,
                            MinimapOverlayRenderer.Clip clip) {
        if ((fillArgb >>> 24) == 0) {
            return EMPTY;
        }
        List<PolygonScanlineRasterizer.FillRectangle> rectangles =
                PolygonScanlineRasterizer.plan(rings, left, top, right, bottom).rectangles();
        if (rectangles.isEmpty()) {
            return EMPTY;
        }
        if (clip == null) {
            return new Plan(fillArgb, rectangles);
        }

        List<PolygonScanlineRasterizer.FillRectangle> clipped = new ArrayList<>(rectangles.size());
        for (PolygonScanlineRasterizer.FillRectangle rectangle : rectangles) {
            clipped.addAll(CircleRectangleClipper.clip(rectangle, clip));
        }
        return clipped.isEmpty() ? EMPTY : new Plan(fillArgb, List.copyOf(clipped));
    }

    @FunctionalInterface
    public interface RectangleSink<T> {
        void fill(T target, int left, int top, int right, int bottom, int argb);
    }

    public static final class Plan {
        private final int fillArgb;
        private final List<PolygonScanlineRasterizer.FillRectangle> rectangles;

        private Plan(int fillArgb, List<PolygonScanlineRasterizer.FillRectangle> rectangles) {
            this.fillArgb = fillArgb;
            this.rectangles = rectangles;
        }

        public List<PolygonScanlineRasterizer.FillRectangle> rectangles() {
            return rectangles;
        }

        public <T> void submitTo(T target, RectangleSink<T> sink) {
            for (PolygonScanlineRasterizer.FillRectangle rectangle : rectangles) {
                sink.fill(target, rectangle.left(), rectangle.top(), rectangle.right(), rectangle.bottom(), fillArgb);
            }
        }
    }
}
