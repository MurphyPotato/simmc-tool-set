package com.murphypotato.simmctoolset.internal.map.integration;

import java.util.EnumSet;
import java.util.Set;

/** Runtime health for World-only paths; a failed path never disables Minimap or waypoints. */
public final class WorldMapHealth {
    private final EnumSet<Capability> healthy = EnumSet.allOf(Capability.class);

    public synchronized boolean isHealthy(Capability capability) { return healthy.contains(capability); }
    public synchronized void fail(Capability capability) { healthy.remove(capability); }
    public synchronized Set<Capability> healthyCapabilities() { return Set.copyOf(healthy); }
    public synchronized boolean worldSurfaceEnabled() { return isHealthy(Capability.SURFACE); }
    public synchronized boolean navigationEnabled() { return isHealthy(Capability.NAVIGATION); }
    public synchronized boolean extendedZoomEnabled() { return isHealthy(Capability.EXTENDED_ZOOM); }

    public enum Capability { UI, SURFACE, NAVIGATION, EXTENDED_ZOOM }
}