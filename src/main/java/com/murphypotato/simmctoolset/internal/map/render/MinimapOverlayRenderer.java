package com.murphypotato.simmctoolset.internal.map.render;

import java.util.List;
import java.util.Objects;

/** Pure minimap projection, density, and clipping model shared by the Xaero Mixin. */
public final class MinimapOverlayRenderer {
    public static final boolean DEFAULT_BACKGROUND_ENABLED = false;
    private static final double BASE_VISIBLE_BLOCKS = 128.0;

    public record Clip(int left, int top, int size, boolean circular) {
        public Clip {
            if (size <= 0) throw new IllegalArgumentException("size must be positive");
        }

        public boolean contains(double x, double y) {
            if (x < left || y < top || x > left + size || y > top + size) return false;
            if (!circular) return true;
            double radius = size / 2.0;
            double dx = x - (left + radius);
            double dy = y - (top + radius);
            return dx * dx + dy * dy <= radius * radius;
        }

        public int right() { return left + size; }
        public int bottom() { return top + size; }
    }

    public record Plan(WorldMapOverlayRenderer.View view, Clip clip,
                       List<LayerRenderer.DrawCommand> commands) {
        public Plan { commands = List.copyOf(commands); }
    }

    private final WorldMapOverlayRenderer overlay;

    public MinimapOverlayRenderer(WorldMapOverlayRenderer overlay) {
        this.overlay = Objects.requireNonNull(overlay, "overlay");
    }

    public Plan plan(double playerX, double playerZ, int screenWidth, int screenHeight,
                     int left, int top, int size, double xaeroZoom, boolean circular) {
        double safeZoom = Double.isFinite(xaeroZoom) ? Math.max(0.25, xaeroZoom) : 1.0;
        double scale = size / (BASE_VISIBLE_BLOCKS * safeZoom);
        double targetX = left + size / 2.0;
        double targetY = top + size / 2.0;
        double centerX = playerX - (targetX - screenWidth / 2.0) / scale;
        double centerZ = playerZ - (targetY - screenHeight / 2.0) / scale;
        WorldMapOverlayRenderer.View view = new WorldMapOverlayRenderer.View(
                centerX, centerZ, scale, screenWidth, screenHeight);
        return new Plan(view, new Clip(left, top, size, circular), overlay.commands(view));
    }

    public static boolean showIcon(double distanceBlocks, double maximumDistance) {
        return Double.isFinite(distanceBlocks) && distanceBlocks >= 0 && distanceBlocks <= maximumDistance;
    }

    public static boolean showLabel(double pixelsPerBlock) {
        return Double.isFinite(pixelsPerBlock) && pixelsPerBlock >= 1.0;
    }
}
