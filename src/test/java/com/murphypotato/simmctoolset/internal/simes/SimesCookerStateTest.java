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
        assertEquals(27_000L, cooker.remainingMillis(5_000L));
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
    void contentReductionSignalsResultChangeForCompletion() {
        SimesCookerState cooker = new SimesCookerState();
        cooker.observe("煎锅", false, List.of("a", "b"), 1_000L);
        cooker.observe("煎锅", false, List.of("a"), 2_000L);
        cooker.observe("煎锅", false, List.of("a"), 2_250L);

        assertTrue(cooker.hasContents());
        assertTrue(cooker.isCompleted());
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

    @Test
    void addingContentsRestartsClosedCookAsInV118() {
        SimesCookerState cooker = new SimesCookerState();
        cooker.observe("煎锅", false, List.of("a"), 1_000L);
        cooker.observe("煎锅", false, List.of("a", "b"), 10_000L);
        assertEquals(10_000L, cooker.estimateStartedAt());
    }

    @Test
    void completionRemainsGreenOnRepeatedScansAndOpeningLid() {
        SimesCookerState cooker = new SimesCookerState();
        cooker.observe("蒸锅", false, List.of("raw"), 1_000L);
        cooker.observe("蒸锅", false, List.of("dish"), 2_000L);
        cooker.observe("蒸锅", false, List.of("dish"), 2_250L);
        cooker.observe("蒸锅", false, List.of("dish"), 3_000L);
        assertTrue(cooker.isCompleted());
        assertEquals(0L, cooker.estimateStartedAt());
        cooker.observe("蒸锅", true, List.of("dish"), 4_000L);
        assertTrue(cooker.isCompleted());
        assertEquals(0xFF45E06F, cooker.statusColor());
    }

    @Test
    void skilletFailureRemainsRedWithoutRestartingTimer() {
        SimesCookerState cooker = new SimesCookerState();
        cooker.observe("煎锅", false, List.of("raw"), 1_000L);
        cooker.observe("煎锅", false, List.of("minecraft:charcoal|木炭|{}"), 2_000L);
        cooker.observe("煎锅", false, List.of("minecraft:charcoal|木炭|{}"), 2_250L);
        cooker.observe("煎锅", false, List.of("minecraft:charcoal|木炭|{}"), 3_000L);
        assertTrue(cooker.isFailed());
        assertEquals(0L, cooker.remainingMillis(3_000L));
        assertEquals(0xFFFF4040, cooker.statusColor());
    }

    @Test
    void resultNeedsTwoMatchingSamplesRatherThanOneTransientUpdate() {
        SimesCookerState cooker = new SimesCookerState();
        cooker.observe("煮锅", false, List.of("raw"), 1_000L);
        cooker.observe("煮锅", false, List.of("dish"), 2_000L);
        assertEquals(SimesCookerState.Status.COOKING, cooker.status());
        cooker.observe("煮锅", false, List.of("raw"), 2_250L);
        assertEquals(SimesCookerState.Status.COOKING, cooker.status());
    }
}
