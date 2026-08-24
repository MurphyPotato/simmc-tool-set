package com.murphypotato.simmctoolset.internal.map.integration;

/** Zoom-floor policy shared by A-C and D without linking to GuiMap bytecode. */
public final class WorldZoomAdapter {
    public static final double LEGACY_FLOOR = 0.0625d;
    public static final double PROFILED_FLOOR = 0.001953125d;
    private final WorldAdapterSelection.Zoom kind;

    private WorldZoomAdapter(WorldAdapterSelection.Zoom kind) { this.kind = kind; }
    public static WorldZoomAdapter resolve(WorldAdapterSelection selection) {
        return selection.hasZoom() ? new WorldZoomAdapter(selection.zoom()) : null;
    }
    public WorldAdapterSelection.Zoom kind() { return kind; }
    public boolean supportsExtendedZoom() { return kind != WorldAdapterSelection.Zoom.NONE; }
    public double floor(boolean extended, double requestedFloor) {
        if (!extended) return requestedFloor;
        if (!Double.isFinite(requestedFloor) || requestedFloor <= 0) return LEGACY_FLOOR;
        return kind == WorldAdapterSelection.Zoom.PROFILED
                ? Math.min(requestedFloor, PROFILED_FLOOR) : Math.min(requestedFloor, LEGACY_FLOOR);
    }
    public double fallback(double original) {
        return Double.isFinite(original) && original > 0 ? original : LEGACY_FLOOR;
    }
}