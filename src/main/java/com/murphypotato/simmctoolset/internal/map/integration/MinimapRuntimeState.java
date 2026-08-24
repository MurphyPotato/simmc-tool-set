package com.murphypotato.simmctoolset.internal.map.integration;

import com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot;
import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.*;

/** Capability-selected Minimap runtime. Unknown or incomplete paths fail closed. */
public final class MinimapRuntimeState {
    private static final MinimapRuntimeState EMPTY = emptyState();
    private static volatile MinimapRuntimeState current = EMPTY;
    private final MinimapHealth health;
    private final MinimapShapeAdapter shape;
    private MinimapRuntimeState(MinimapHealth health, MinimapShapeAdapter shape) { this.health = health; this.shape = shape; }
    private static MinimapRuntimeState emptyState() {
        MinimapHealth health = new MinimapHealth(false);
        health.fail(MinimapHealth.Capability.SHAPE);
        return new MinimapRuntimeState(health, null);
    }
    public static MinimapRuntimeState initialize(XaeroCapabilitySnapshot snapshot, ClassLoader loader) {
        if (snapshot == null || loader == null) return EMPTY;
        MinimapHealth health = new MinimapHealth(snapshot.has(MINIMAP_RENDER_COMMON)
                && (snapshot.has(MINIMAP_HOOK_PIP) || snapshot.has(MINIMAP_HOOK_DEPTH_TRACE)));
        MinimapShapeAdapter shape = MinimapShapeAdapter.resolve(snapshot, loader);
        if (shape.kind() == MinimapShapeAdapter.Kind.NONE) health.fail(MinimapHealth.Capability.SHAPE);
        MinimapRuntimeState state = new MinimapRuntimeState(health, shape);
        current = state;
        return state;
    }
    public static MinimapRuntimeState empty() { return EMPTY; }
    public static MinimapRuntimeState current() { return current; }
    public MinimapHealth health() { return health; }
    public boolean overlayEnabled() { return health.enabled(MinimapHealth.Capability.COMMON_RENDER); }
    public boolean shapeEnabled() { return health.enabled(MinimapHealth.Capability.SHAPE); }
    public boolean circular() {
        if (!shapeEnabled() || shape == null) return false;
        try { return shape.circular(); } catch (RuntimeException failure) { health.fail(MinimapHealth.Capability.SHAPE); return false; }
    }
}
