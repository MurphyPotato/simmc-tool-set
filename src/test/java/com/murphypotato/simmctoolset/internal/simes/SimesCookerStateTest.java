package com.murphypotato.simmctoolset.internal.simes;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimesCookerStateTest {
    @Test
    void openLidDoesNotStartTimerAndClosingStartsLocalEstimate() {
        SimesCookerState cooker = new SimesCookerState();
        cooker.observe("炖锅", true, List.of("a", "b"), 1_000L);
        assertTrue(cooker.isOpen());
        assertEquals(0L, cooker.estimateStartedAt());

        cooker.observe("炖锅", false, List.of("a", "b"), 2_000L);
        assertEquals(2_000L, cooker.estimateStartedAt());
        assertEquals(57_000L, cooker.remainingMillis(5_000L));
    }

    @Test
    void reopeningAndClosingAgainRestartsEstimate() {
        SimesCookerState cooker = new SimesCookerState();
        cooker.observe("蒸锅", false, List.of("a", "b"), 1_000L);
        cooker.observe("蒸锅", true, List.of("a", "b"), 10_000L);
        cooker.observe("蒸锅", false, List.of("a", "b"), 11_000L);

        assertEquals(11_000L, cooker.estimateStartedAt());
    }

    @Test
    void contentReductionDoesNotPretendToBeServerCompletion() {
        SimesCookerState cooker = new SimesCookerState();
        cooker.observe("煎锅", false, List.of("a", "b"), 1_000L);
        cooker.observe("煎锅", false, List.of("a"), 2_000L);

        assertTrue(cooker.hasContents());
        assertTrue(!cooker.isCompleted());
    }

    @Test
    void emptyContentsClearEstimateAndCompletionState() {
        SimesCookerState cooker = new SimesCookerState();
        cooker.observe("煎锅", false, List.of("a", "b"), 1_000L);
        cooker.observe("煎锅", false, List.of(), 2_000L);

        assertTrue(!cooker.hasContents());
        assertEquals(0L, cooker.estimateStartedAt());
        assertTrue(!cooker.isCompleted());
    }
}
