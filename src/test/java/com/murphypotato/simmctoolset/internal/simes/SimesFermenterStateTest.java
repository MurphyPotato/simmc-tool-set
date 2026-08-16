package com.murphypotato.simmctoolset.internal.simes;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimesFermenterStateTest {
    @Test
    void capsLocallyTrackedIngredientTypesAndAppliesCorrections() {
        SimesFermenterLedger ledger = new SimesFermenterLedger();
        ledger.add("spice", 12);
        ledger.add("spice", 4);
        ledger.add("salt", 2);

        assertEquals(10, ledger.count("spice"));
        assertEquals(2, ledger.count("salt"));
        assertEquals(3, ledger.remove("spice", 3));
        assertEquals(7, ledger.count("spice"));
    }

    @Test
    void timerDistinguishesProjectedZeroFromServerConfirmation() {
        SimesFermentationTimer timer = new SimesFermentationTimer();
        timer.calibrate("1秒", 1_000_000_000L);

        assertEquals(SimesFermentationTimer.State.CALIBRATED, timer.stateAt(1_500_000_000L));
        assertEquals(SimesFermentationTimer.State.EXPECTED_DONE, timer.stateAt(2_100_000_000L));

        timer.markServerComplete(2_100_000_000L);
        assertEquals(SimesFermentationTimer.State.CONFIRMED, timer.stateAt(2_100_000_000L));

        timer.invalidate("已中断");
        assertEquals(SimesFermentationTimer.State.INVALIDATED, timer.stateAt(2_100_000_000L));
    }

    @Test
    void authoritativeMaterialReplacementClampsCounts() {
        SimesFermenterLedger ledger = new SimesFermenterLedger();
        ledger.replace(Map.of("a", 12, "b", 2));

        assertEquals(10, ledger.count("a"));
        assertEquals(2, ledger.count("b"));
    }
}
