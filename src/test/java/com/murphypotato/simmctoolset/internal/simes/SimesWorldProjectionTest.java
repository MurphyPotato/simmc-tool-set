package com.murphypotato.simmctoolset.internal.simes;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimesWorldProjectionTest {
    @Test
    void mapsVisibleNdcPointToScaledScreenCoordinates() {
        Optional<SimesWorldProjection.ScreenPoint> point = SimesWorldProjection.project(
                0.0F, 0.0F, 1.0F, 1920, 1080);

        assertTrue(point.isPresent());
        assertEquals(960, point.get().x());
        assertEquals(540, point.get().y());
    }

    @Test
    void rejectsBehindCameraAndFarOutsideClipBounds() {
        assertTrue(SimesWorldProjection.project(0.0F, 0.0F, 0.0F, 1920, 1080).isEmpty());
        assertTrue(SimesWorldProjection.project(1.2F, 0.0F, 1.0F, 1920, 1080).isEmpty());
    }
}
