package com.murphypotato.simmctoolset.internal.simes;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcaneStatusStateTest {
    private static final Set<String> KNOWN = Set.of("火球术", "御风术");

    @Test
    void keepsCancellingEveryPacketAfterAHiddenAddUntilRemove() {
        ArcaneStatusState state = new ArcaneStatusState(KNOWN);
        UUID id = UUID.randomUUID();

        assertTrue(state.add(id, "正在吟唱 火球术", 0.7f, false, 0L, true).cancel());
        state.tick(2_000_000_000L);

        assertTrue(state.updateProgress(id, 0.2f, 2_000_000_100L, true).cancel());
        assertTrue(state.updateStyle(id, 2_000_000_200L, true).cancel());
        assertTrue(state.updateProperties(id, 2_000_000_300L, true).cancel());
        assertTrue(state.updateName(id, "火球术剩余: 20 tick", 2_000_000_400L, true).cancel());
        assertTrue(state.remove(id, 2_000_000_500L).cancel());
        assertFalse(state.updateProgress(id, 0.1f, 2_000_000_600L, true).cancel());
    }

    @Test
    void keepsSuppressedEntryAcrossUnknownNamesUntilRemove() {
        ArcaneStatusState state = new ArcaneStatusState(KNOWN);
        UUID id = UUID.randomUUID();

        state.add(id, "正在吟唱 火球术", 0.7f, false, 0L, true);
        assertTrue(state.updateName(id, "未知 BossBar", 1L, true).cancel());
        assertEquals("火球术", state.snapshot(id).name());
        assertTrue(state.updateName(id, "御风术剩余: 40 tick", 2L, true).cancel());
        assertEquals("御风术", state.snapshot(id).name());
        assertTrue(state.updateProgress(id, 0.2f, 3L, true).cancel());
        assertTrue(state.remove(id, 4L).cancel());
        assertFalse(state.updateProgress(id, 0.1f, 5L, true).cancel());
    }

    @Test
    void defersBlankProgressBarsAndOnlyHidesKnownArcaneLevels() {
        ArcaneStatusState state = new ArcaneStatusState(KNOWN);
        UUID pendingId = UUID.randomUUID();
        UUID knownLevelId = UUID.randomUUID();
        UUID unknownLevelId = UUID.randomUUID();

        assertFalse(state.add(pendingId, "", 1.0f, true, 10L, true).cancel());
        assertEquals(ArcaneStatusState.Kind.PENDING, state.snapshot(pendingId).kind());
        assertTrue(state.updateName(pendingId, "御风术剩余：40 tick", 20L, true).cancel());
        assertEquals(ArcaneStatusState.Kind.DURATION, state.snapshot(pendingId).kind());

        assertTrue(state.add(knownLevelId, "火球术 Lv5 MAX/MAX", 1.0f, false, 30L, true).cancel());
        assertFalse(state.add(unknownLevelId, "未知法术 Lv5 MAX", 1.0f, false, 30L, true).cancel());
    }

    @Test
    void refreshesUpdateBaselineAndResetsDurationWhenCastingChangesKind() {
        ArcaneStatusState state = new ArcaneStatusState(KNOWN);
        UUID id = UUID.randomUUID();

        state.add(id, "正在吟唱 火球术", 0.4f, false, 100L, false);
        assertEquals(100L, state.snapshot(id).updatedAt());
        state.updateProgress(id, 0.8f, 200L, false);
        assertEquals(200L, state.snapshot(id).updatedAt());

        state.updateName(id, "火球术剩余: 40 tick", 300L, false);
        ArcaneStatusState.Snapshot duration = state.snapshot(id);
        assertEquals(ArcaneStatusState.Kind.DURATION, duration.kind());
        assertEquals(40, duration.totalTicks());
        assertEquals(40, duration.remainingTicks());
        assertEquals(0.8f, duration.progress());
        assertEquals(300L, duration.updatedAt());

        state.updateProgress(id, 0.25f, 350L, false);
        duration = state.snapshot(id);
        assertEquals(40, duration.totalTicks());
        assertEquals(40, duration.remainingTicks());
        assertEquals(0.25f, duration.progress());
        assertEquals(350L, duration.updatedAt());

        state.updateName(id, "火球术剩余: 7 tick", 400L, false);
        duration = state.snapshot(id);
        assertEquals(7, duration.totalTicks());
        assertEquals(7, duration.remainingTicks());
        assertEquals(0.25f, duration.progress());
        assertEquals(400L, duration.updatedAt());
    }

    @Test
    void removeStartsAnInterruptedExitAnimationForIncompleteCasting() {
        ArcaneStatusState state = new ArcaneStatusState(KNOWN);
        UUID id = UUID.randomUUID();

        state.add(id, "正在吟唱 火球术", 0.5f, false, 1_000L, false);
        assertFalse(state.remove(id, 2_000L).cancel());
        ArcaneStatusState.Snapshot exiting = state.snapshot(id);
        assertTrue(exiting.exiting());
        assertTrue(exiting.interrupted());
        state.tick(2_000L + ArcaneStatusState.EXIT_NANOS - 1L);
        assertTrue(state.snapshot(id).exiting());
        state.tick(2_000L + ArcaneStatusState.EXIT_NANOS + 1L);
        assertEquals(null, state.snapshot(id));
    }

    @Test
    void resetClearsStatesAndSuppression() {
        ArcaneStatusState state = new ArcaneStatusState(KNOWN);
        UUID id = UUID.randomUUID();
        state.add(id, "正在吟唱 火球术", 1.0f, false, 0L, true);
        state.reset();
        assertEquals(null, state.snapshot(id));
        assertFalse(state.updateStyle(id, 1L, true).cancel());
    }
}
