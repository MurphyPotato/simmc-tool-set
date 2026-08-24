package com.murphypotato.simmctoolset.internal.map.integration;

import com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot;

import java.util.Objects;

import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.WORLD_NAVIGATION;
import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.WORLD_SURFACE_LEGACY;
import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.WORLD_SURFACE_PROFILED;
import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.WORLD_VIEW;
import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.WORLD_ZOOM_LEGACY;
import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.WORLD_ZOOM_PROFILED;

/** Independent World Map capability choices. Unknown capabilities stay disabled. */
public record WorldAdapterSelection(boolean view, boolean navigation,
                                    Surface surface, Zoom zoom) {
    public WorldAdapterSelection {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(zoom, "zoom");
    }

    public static WorldAdapterSelection select(XaeroCapabilitySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Surface surface = snapshot.has(WORLD_SURFACE_LEGACY) ? Surface.LEGACY
                : snapshot.has(WORLD_SURFACE_PROFILED) ? Surface.PROFILED : Surface.NONE;
        Zoom zoom = snapshot.has(WORLD_ZOOM_LEGACY) ? Zoom.LEGACY
                : snapshot.has(WORLD_ZOOM_PROFILED) ? Zoom.PROFILED : Zoom.NONE;
        return new WorldAdapterSelection(snapshot.has(WORLD_VIEW), snapshot.has(WORLD_NAVIGATION), surface, zoom);
    }

    public Family family() {
        return switch (surface) {
            case LEGACY -> zoom == Zoom.LEGACY ? Family.A : Family.MIXED;
            case PROFILED -> zoom == Zoom.PROFILED ? Family.D
                    : zoom == Zoom.LEGACY ? Family.BC : Family.MIXED;
            case NONE -> Family.UNKNOWN;
        };
    }

    public boolean hasSurface() { return surface != Surface.NONE; }
    public boolean hasZoom() { return zoom != Zoom.NONE; }

    public enum Surface { NONE, LEGACY, PROFILED }
    public enum Zoom { NONE, LEGACY, PROFILED }
    public enum Family { UNKNOWN, A, BC, D, MIXED }
}