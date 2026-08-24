package com.murphypotato.simmctoolset.internal.map;

import com.murphypotato.simmctoolset.internal.map.integration.MinimapHealth;
import com.murphypotato.simmctoolset.internal.map.integration.MinimapRuntimeState;
import com.murphypotato.simmctoolset.internal.map.integration.MinimapShapeAdapter;
import com.murphypotato.simmctoolset.internal.map.integration.WaypointHealth;
import com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static com.murphypotato.simmctoolset.map.XaeroCapabilitySnapshot.Capability.*;
import static org.junit.jupiter.api.Assertions.*;

class MinimapRuntimeStateTest {
    @Test
    void unknownMinimapFailsClosedWithoutAffectingWaypointHealth() {
        MinimapRuntimeState state = MinimapRuntimeState.initialize(new XaeroCapabilitySnapshot(EnumSet.of(WAYPOINT_WRITE)), getClass().getClassLoader());
        assertFalse(state.overlayEnabled());
        assertFalse(state.shapeEnabled());
        WaypointHealth waypoint = new WaypointHealth(true);
        state.health().fail(MinimapHealth.Capability.SHAPE);
        assertTrue(waypoint.enabled());
    }

    @Test
    void selectsLegacyAndProfileShapeFamilies() {
        assertEquals(MinimapShapeAdapter.Kind.LEGACY,
                MinimapShapeAdapter.resolve(new XaeroCapabilitySnapshot(EnumSet.of(MINIMAP_SHAPE_LEGACY)), getClass().getClassLoader()).kind());
        assertEquals(MinimapShapeAdapter.Kind.PROFILE,
                MinimapShapeAdapter.resolve(new XaeroCapabilitySnapshot(EnumSet.of(MINIMAP_SHAPE_PROFILE)), getClass().getClassLoader()).kind());
    }

    @Test
    void waypointHealthIsOneWayAndIndependent() {
        WaypointHealth health = new WaypointHealth(true);
        health.fail();
        health.fail();
        assertFalse(health.enabled());
    }
}
