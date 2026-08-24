package com.murphypotato.simmctoolset.internal.map.integration;

/** Waypoint health is separate from World Map and Minimap overlay health. */
public final class WaypointHealth {
    private volatile boolean enabled;
    public WaypointHealth(boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }
    public void fail() { enabled = false; }
}
