package com.murphypotato.simmctoolset.internal.map.integration;

import java.util.EnumSet;

/** Independent health for Minimap paths; a failed shape read does not disable waypoints. */
public final class MinimapHealth {
    private final EnumSet<Capability> healthy = EnumSet.allOf(Capability.class);
    public MinimapHealth() { }
    public MinimapHealth(boolean commonRender) { if (!commonRender) fail(Capability.COMMON_RENDER); }
    public synchronized boolean enabled(Capability capability) { return healthy.contains(capability); }
    public synchronized void fail(Capability capability) { healthy.remove(capability); }
    public enum Capability { COMMON_RENDER, SHAPE }
}
