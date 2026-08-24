package com.murphypotato.simmctoolset.internal.map;

import com.murphypotato.simmctoolset.internal.map.integration.WorldAdapterSelection;
import com.murphypotato.simmctoolset.internal.map.integration.WorldMapHealth;
import com.murphypotato.simmctoolset.internal.map.integration.WorldViewAdapter;
import com.murphypotato.simmctoolset.internal.map.integration.WorldZoomAdapter;
import com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldAdapterModelTest {
    @Test
    void selectsWorldFamiliesWithoutCopyingGuiAdapters() {
        assertEquals(WorldAdapterSelection.Family.A, select(WORLD_SURFACE_LEGACY, WORLD_ZOOM_LEGACY).family());
        assertEquals(WorldAdapterSelection.Family.BC, select(WORLD_SURFACE_PROFILED, WORLD_ZOOM_LEGACY).family());
        assertEquals(WorldAdapterSelection.Family.D, select(WORLD_SURFACE_PROFILED, WORLD_ZOOM_PROFILED).family());
        WorldAdapterSelection unknown = new WorldAdapterSelection(false, false,
                WorldAdapterSelection.Surface.NONE, WorldAdapterSelection.Zoom.NONE);
        assertEquals(WorldAdapterSelection.Family.UNKNOWN, unknown.family());
        assertFalse(unknown.hasSurface());
    }

    @Test
    void zoomFallbackLeavesNormalMapUsable() {
        WorldZoomAdapter adapter = WorldZoomAdapter.resolve(select(WORLD_SURFACE_PROFILED, WORLD_ZOOM_PROFILED));
        assertNotNull(adapter);
        assertEquals(WorldZoomAdapter.PROFILED_FLOOR, adapter.floor(true, 0.0625d));
        assertEquals(0.5d, adapter.floor(false, 0.5d));
        assertEquals(0.5d, adapter.fallback(0.5d));
        assertEquals(WorldZoomAdapter.LEGACY_FLOOR, adapter.fallback(Double.NaN));
    }

    @Test
    void nullViewStillAllowsUiButNeverHitTest() {
        assertTrue(WorldViewAdapter.view(10, 20, 2, 1, 800, 600).isPresent());
        assertTrue(WorldViewAdapter.view(10, 20, 2, 1, 0, 600).isEmpty());
        assertFalse(WorldViewAdapter.shouldHitTest(null));
    }

    @Test
    void worldFailuresStayIndependent() {
        WorldMapHealth health = new WorldMapHealth();
        health.fail(WorldMapHealth.Capability.SURFACE);
        health.fail(WorldMapHealth.Capability.EXTENDED_ZOOM);
        assertFalse(health.worldSurfaceEnabled());
        assertFalse(health.extendedZoomEnabled());
        assertTrue(health.navigationEnabled());
        assertTrue(health.isHealthy(WorldMapHealth.Capability.UI));
    }

    private static WorldAdapterSelection select(XaeroCapabilitySnapshot.Capability... capabilities) {
        EnumSet<XaeroCapabilitySnapshot.Capability> set = EnumSet.of(WORLD_VIEW, WORLD_NAVIGATION);
        for (XaeroCapabilitySnapshot.Capability capability : capabilities) set.add(capability);
        return WorldAdapterSelection.select(new XaeroCapabilitySnapshot(set));
    }
}