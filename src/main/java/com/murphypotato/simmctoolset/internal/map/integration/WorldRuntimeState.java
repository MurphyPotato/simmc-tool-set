package com.murphypotato.simmctoolset.internal.map.integration;

import com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot;

import java.util.Objects;

/** Runtime-selected World Map adapters and independent failure state. */
public final class WorldRuntimeState {
    public static final String COMMON_MIXIN = "com.murphypotato.simmctoolset.mixins.map.MixinGuiMap";
    public static final String LEGACY_SURFACE_MIXIN = "com.murphypotato.simmctoolset.mixins.map.MixinWorldSurfaceLegacy";
    public static final String PROFILED_SURFACE_MIXIN = "com.murphypotato.simmctoolset.mixins.map.MixinWorldSurfaceProfiled";
    public static final String LEGACY_ZOOM_MIXIN = "com.murphypotato.simmctoolset.mixins.map.MixinWorldZoomLegacy";
    public static final String PROFILED_ZOOM_MIXIN = "com.murphypotato.simmctoolset.mixins.map.MixinWorldZoomProfiled";

    private static final WorldRuntimeState EMPTY = create(new XaeroCapabilitySnapshot(java.util.Set.of()),
            WorldRuntimeState.class.getClassLoader());
    private static volatile WorldRuntimeState current = EMPTY;

    private final WorldAdapterSelection selection;
    private final WorldSurfaceAdapter surface;
    private final WorldZoomAdapter zoom;
    private final WorldMapHealth health;

    private WorldRuntimeState(WorldAdapterSelection selection, WorldSurfaceAdapter surface,
                              WorldZoomAdapter zoom, WorldMapHealth health) {
        this.selection = selection;
        this.surface = surface;
        this.zoom = zoom;
        this.health = health;
    }

    public static WorldRuntimeState initialize(XaeroCapabilitySnapshot snapshot, ClassLoader loader) {
        WorldRuntimeState state = create(Objects.requireNonNull(snapshot, "snapshot"),
                Objects.requireNonNull(loader, "loader"));
        current = state;
        return state;
    }

    public static WorldRuntimeState initialize(XaeroCapabilitySnapshot snapshot) {
        return initialize(snapshot, Thread.currentThread().getContextClassLoader());
    }

    public static WorldRuntimeState current() { return current; }
    public static boolean shouldApply(String mixinClassName) { return current.applies(mixinClassName); }

    public WorldAdapterSelection selection() { return selection; }
    public WorldSurfaceAdapter surface() { return surface; }
    public WorldZoomAdapter zoom() { return zoom; }
    public WorldMapHealth health() { return health; }
    public boolean uiEnabled() { return selection.view(); }
    public boolean navigationEnabled() { return selection.navigation() && health.navigationEnabled(); }
    public boolean surfaceEnabled() { return surface != null && health.worldSurfaceEnabled(); }
    public boolean extendedZoomEnabled() { return zoom != null && health.extendedZoomEnabled(); }
    public void fail(WorldMapHealth.Capability capability) { health.fail(capability); }

    public boolean applies(String mixinClassName) {
        if (mixinClassName == null) return false;
        if (mixinClassName.equals(COMMON_MIXIN) || mixinClassName.endsWith(".MixinGuiMap")) return uiEnabled();
        if (mixinClassName.equals(LEGACY_SURFACE_MIXIN) || mixinClassName.endsWith("MixinWorldSurfaceLegacy"))
            return surfaceEnabled() && selection.surface() == WorldAdapterSelection.Surface.LEGACY;
        if (mixinClassName.equals(PROFILED_SURFACE_MIXIN) || mixinClassName.endsWith("MixinWorldSurfaceProfiled"))
            return surfaceEnabled() && selection.surface() == WorldAdapterSelection.Surface.PROFILED;
        if (mixinClassName.equals(LEGACY_ZOOM_MIXIN) || mixinClassName.endsWith("MixinWorldZoomLegacy"))
            return extendedZoomEnabled() && selection.zoom() == WorldAdapterSelection.Zoom.LEGACY;
        if (mixinClassName.equals(PROFILED_ZOOM_MIXIN) || mixinClassName.endsWith("MixinWorldZoomProfiled"))
            return extendedZoomEnabled() && selection.zoom() == WorldAdapterSelection.Zoom.PROFILED;
        return false;
    }

    private static WorldRuntimeState create(XaeroCapabilitySnapshot snapshot, ClassLoader loader) {
        WorldAdapterSelection selection = WorldAdapterSelection.select(snapshot);
        WorldMapHealth health = new WorldMapHealth();
        if (!selection.view()) health.fail(WorldMapHealth.Capability.UI);
        if (!selection.navigation()) health.fail(WorldMapHealth.Capability.NAVIGATION);
        if (!selection.hasSurface()) health.fail(WorldMapHealth.Capability.SURFACE);
        if (!selection.hasZoom()) health.fail(WorldMapHealth.Capability.EXTENDED_ZOOM);
        WorldSurfaceAdapter surface = WorldSurfaceAdapter.resolve(selection, loader);
        if (selection.hasSurface() && surface == null) health.fail(WorldMapHealth.Capability.SURFACE);
        WorldZoomAdapter zoom = WorldZoomAdapter.resolve(selection);
        if (selection.hasZoom() && zoom == null) health.fail(WorldMapHealth.Capability.EXTENDED_ZOOM);
        return new WorldRuntimeState(selection, surface, zoom, health);
    }
}
