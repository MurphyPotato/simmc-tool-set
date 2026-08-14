package com.murphypotato.simmctoolset.internal.map.render;

import java.util.Optional;

/** Screen clipping plus an axis-aligned fast path for marker strokes. */
public final class ScreenLineRasterizer {
    public sealed interface Plan permits Rectangle, Sampled { }
    public record Rectangle(int left, int top, int right, int bottom) implements Plan { }
    public record Sampled(ScreenPoint start, ScreenPoint end, int width, int steps) implements Plan { }

    private ScreenLineRasterizer() { }

    public static Optional<Plan> plan(ScreenPoint start, ScreenPoint end, double requestedWidth,
                                      int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0 || !Double.isFinite(requestedWidth)) return Optional.empty();
        Optional<Segment> clipped = clip(start, end, screenWidth - 1.0, screenHeight - 1.0);
        if (clipped.isEmpty()) return Optional.empty();
        ScreenPoint first = clipped.orElseThrow().start();
        ScreenPoint second = clipped.orElseThrow().end();
        int width = Math.max(1, (int) Math.ceil(requestedWidth));
        int half = width / 2;
        double dx = second.x() - first.x();
        double dy = second.y() - first.y();
        if (Math.abs(dy) < 1.0e-9) {
            int left = Math.max(0, (int) Math.floor(Math.min(first.x(), second.x())));
            int right = Math.min(screenWidth, (int) Math.ceil(Math.max(first.x(), second.x())) + 1);
            int top = Math.max(0, (int) Math.floor(first.y()) - half);
            int bottom = Math.min(screenHeight, top + width);
            return right > left && bottom > top ? Optional.of(new Rectangle(left, top, right, bottom))
                    : Optional.empty();
        }
        if (Math.abs(dx) < 1.0e-9) {
            int left = Math.max(0, (int) Math.floor(first.x()) - half);
            int right = Math.min(screenWidth, left + width);
            int top = Math.max(0, (int) Math.floor(Math.min(first.y(), second.y())));
            int bottom = Math.min(screenHeight, (int) Math.ceil(Math.max(first.y(), second.y())) + 1);
            return right > left && bottom > top ? Optional.of(new Rectangle(left, top, right, bottom))
                    : Optional.empty();
        }
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        return Optional.of(new Sampled(first, second, width, steps));
    }

    private static Optional<Segment> clip(ScreenPoint start, ScreenPoint end, double maxX, double maxY) {
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {start.x(), maxX - start.x(), start.y(), maxY - start.y()};
        double t0 = 0;
        double t1 = 1;
        for (int index = 0; index < p.length; index++) {
            if (p[index] == 0) {
                if (q[index] < 0) return Optional.empty();
                continue;
            }
            double ratio = q[index] / p[index];
            if (p[index] < 0) t0 = Math.max(t0, ratio);
            else t1 = Math.min(t1, ratio);
            if (t0 > t1) return Optional.empty();
        }
        return Optional.of(new Segment(new ScreenPoint(start.x() + t0 * dx, start.y() + t0 * dy),
                new ScreenPoint(start.x() + t1 * dx, start.y() + t1 * dy)));
    }

    private record Segment(ScreenPoint start, ScreenPoint end) { }
}
