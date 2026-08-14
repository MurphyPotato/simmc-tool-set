package com.murphypotato.simmctoolset.internal.map.render;

public enum RequestContext {
    WORLD_MAP_STILL(32, true, 40),
    WORLD_MAP_MOVING(12, true, 20),
    MINIMAP_STILL(8, false, 8),
    MINIMAP_MOVING(4, false, 4);

    private final int visibleBudget;
    private final boolean prefetchAdjacentZoom;
    private final int concurrentLoads;

    RequestContext(int visibleBudget, boolean prefetchAdjacentZoom, int concurrentLoads) {
        this.visibleBudget = visibleBudget;
        this.prefetchAdjacentZoom = prefetchAdjacentZoom;
        this.concurrentLoads = concurrentLoads;
    }

    public int visibleBudget() { return visibleBudget; }
    public boolean prefetchAdjacentZoom() { return prefetchAdjacentZoom; }
    public int concurrentLoads() { return concurrentLoads; }
}
