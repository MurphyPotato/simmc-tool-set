package com.murphypotato.simmctoolset.internal.map.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Pure conversion of ordered marker commands into capped colored batches and icon barriers. */
public final class ColoredCommandRenderPlanner {
    public static final int MAX_QUADS_PER_BATCH = 65_536;

    public sealed interface Element permits ColoredBatch, Icon { }

    public record ColoredBatch(List<ColoredQuad> quads) implements Element {
        public ColoredBatch {
            quads = List.copyOf(quads);
            if (quads.isEmpty() || quads.size() > MAX_QUADS_PER_BATCH) {
                throw new IllegalArgumentException("invalid colored batch size");
            }
            checkedVertexCount(quads.size());
        }
    }

    public record Icon(LayerRenderer.IconCommand command) implements Element {
        public Icon { Objects.requireNonNull(command, "command"); }
    }

    public record Plan(List<Element> elements, int quadCount) {
        public Plan {
            elements = List.copyOf(elements);
            if (quadCount < 0) throw new IllegalArgumentException("quadCount must be non-negative");
        }
    }

    private record ClipKey(int left, int top, int right, int bottom, boolean circular) {
        static ClipKey of(MinimapOverlayRenderer.Clip clip) {
            return clip == null ? null
                    : new ClipKey(clip.left(), clip.top(), clip.right(), clip.bottom(), clip.circular());
        }
    }

    private record CacheKey(List<LayerRenderer.DrawCommand> commands, int width, int height, ClipKey clip) {
        @Override
        public boolean equals(Object other) {
            return other instanceof CacheKey key && commands == key.commands
                    && width == key.width && height == key.height && Objects.equals(clip, key.clip);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(commands);
            result = 31 * result + width;
            result = 31 * result + height;
            return 31 * result + Objects.hashCode(clip);
        }
    }

    private final LinkedHashMap<CacheKey, Plan> cache = new LinkedHashMap<>(2, 0.75f, true);

    public synchronized Plan plan(List<LayerRenderer.DrawCommand> commands, int screenWidth, int screenHeight,
                                  MinimapOverlayRenderer.Clip clip) {
        Objects.requireNonNull(commands, "commands");
        if (screenWidth <= 0 || screenHeight <= 0) {
            throw new IllegalArgumentException("screen dimensions must be positive");
        }
        CacheKey key = new CacheKey(commands, screenWidth, screenHeight, ClipKey.of(clip));
        Plan existing = cache.get(key);
        if (existing != null) return existing;
        Plan computed = compute(commands, screenWidth, screenHeight, clip);
        cache.put(key, computed);
        if (cache.size() > 2) cache.remove(cache.keySet().iterator().next());
        return computed;
    }

    private static Plan compute(List<LayerRenderer.DrawCommand> commands, int screenWidth, int screenHeight,
                                MinimapOverlayRenderer.Clip clip) {
        Accumulator output = new Accumulator();
        for (LayerRenderer.DrawCommand command : commands) {
            if (command instanceof LayerRenderer.PolygonCommand polygon) {
                appendPolygon(output, polygon, screenWidth, screenHeight, clip);
            } else if (command instanceof LayerRenderer.PolylineCommand line) {
                appendPath(output, line.points(), line.strokeArgb(), line.strokeWidth(),
                        screenWidth, screenHeight, clip);
            } else if (command instanceof LayerRenderer.IconCommand icon && visible(icon, clip)) {
                output.flush();
                output.elements.add(new Icon(icon));
            }
        }
        output.flush();
        return new Plan(output.elements, output.quadCount);
    }

    private static void appendPolygon(Accumulator output, LayerRenderer.PolygonCommand polygon,
                                      int screenWidth, int screenHeight,
                                      MinimapOverlayRenderer.Clip clip) {
        PolygonFillPlanner.Plan fill = PolygonFillPlanner.plan(polygon.rings(), polygon.fillArgb(),
                0, 0, screenWidth, screenHeight, clip);
        for (PolygonScanlineRasterizer.FillRectangle rectangle : fill.rectangles()) {
            output.add(new ColoredQuad(rectangle.left(), rectangle.top(), rectangle.right(), rectangle.bottom(),
                    polygon.fillArgb()));
        }
        if (!ScaledStroke.hasVisibleAlpha(polygon.strokeArgb())) return;
        for (List<ScreenPoint> ring : polygon.rings()) {
            appendPath(output, ring, polygon.strokeArgb(), polygon.strokeWidth(),
                    screenWidth, screenHeight, clip);
            if (ring.size() > 2) {
                appendSegment(output, ring.getLast(), ring.getFirst(), polygon.strokeArgb(),
                        polygon.strokeWidth(), screenWidth, screenHeight, clip);
            }
        }
    }

    private static void appendPath(Accumulator output, List<ScreenPoint> points, int argb, double width,
                                   int screenWidth, int screenHeight, MinimapOverlayRenderer.Clip clip) {
        if ((argb >>> 24) == 0) return;
        for (int index = 1; index < points.size(); index++) {
            appendSegment(output, points.get(index - 1), points.get(index), argb, width,
                    screenWidth, screenHeight, clip);
        }
    }

    private static void appendSegment(Accumulator output, ScreenPoint start, ScreenPoint end,
                                      int argb, double requestedWidth, int screenWidth, int screenHeight,
                                      MinimapOverlayRenderer.Clip clip) {
        ScreenLineRasterizer.Plan plan = ScreenLineRasterizer.plan(
                start, end, requestedWidth, screenWidth, screenHeight).orElse(null);
        if (plan == null) return;
        if (plan instanceof ScreenLineRasterizer.Rectangle rectangle) {
            if (clip == null || !clip.circular()) {
                output.add(new ColoredQuad(
                        rectangle.left(), rectangle.top(), rectangle.right(), rectangle.bottom(), argb));
            } else {
                for (int y = rectangle.top(); y < rectangle.bottom(); y++) {
                    for (int x = rectangle.left(); x < rectangle.right(); x++) {
                        if (clip.contains(x, y)) output.add(new ColoredQuad(x, y, x + 1, y + 1, argb));
                    }
                }
            }
            return;
        }
        ScreenLineRasterizer.Sampled sampled = (ScreenLineRasterizer.Sampled) plan;
        double dx = sampled.end().x() - sampled.start().x();
        double dy = sampled.end().y() - sampled.start().y();
        int half = sampled.width() / 2;
        for (int step = 0; step <= sampled.steps(); step++) {
            double t = (double) step / sampled.steps();
            int x = (int) Math.round(sampled.start().x() + dx * t);
            int y = (int) Math.round(sampled.start().y() + dy * t);
            if (clip != null && !clip.contains(x, y)) continue;
            output.add(new ColoredQuad(x - half, y - half,
                    x - half + sampled.width(), y - half + sampled.width(), argb));
        }
    }

    private static boolean visible(LayerRenderer.IconCommand icon, MinimapOverlayRenderer.Clip clip) {
        if (clip != null && !clip.contains((icon.left() + icon.right()) / 2.0,
                (icon.top() + icon.bottom()) / 2.0)) return false;
        return TextureRenderPlan.icon(icon.left(), icon.top(), icon.right(), icon.bottom()).isPresent();
    }

    static long checkedVertexCount(long quadCount) {
        return Math.multiplyExact(quadCount, 4L);
    }

    private static final class Accumulator {
        private final List<Element> elements = new ArrayList<>();
        private final List<ColoredQuad> current = new ArrayList<>();
        private int quadCount;

        private void add(ColoredQuad quad) {
            quadCount = Math.addExact(quadCount, 1);
            current.add(quad);
            if (current.size() == MAX_QUADS_PER_BATCH) flush();
        }

        private void flush() {
            if (current.isEmpty()) return;
            checkedVertexCount(current.size());
            elements.add(new ColoredBatch(current));
            current.clear();
        }
    }
}
