package com.murphypotato.simmctoolset.map;

import java.util.Set;

/** Immutable result of probing the Xaero classes selected by the runtime class loader. */
public record XaeroCapabilitySnapshot(Set<Capability> capabilities) {
    public XaeroCapabilitySnapshot {
        capabilities = Set.copyOf(capabilities);
    }

    public boolean has(Capability capability) {
        return capabilities.contains(capability);
    }

    public enum Capability {
        WORLD_VIEW,
        WORLD_SURFACE_LEGACY,
        WORLD_SURFACE_PROFILED,
        WORLD_NAVIGATION,
        WORLD_ZOOM_LEGACY,
        WORLD_ZOOM_PROFILED,
        MINIMAP_RENDER_COMMON,
        MINIMAP_HOOK_DEPTH_TRACE,
        MINIMAP_HOOK_PIP,
        MINIMAP_SHAPE_LEGACY,
        MINIMAP_SHAPE_PROFILE,
        MINIMAP_FABRIC_HUD,
        WAYPOINT_WRITE
    }
}
