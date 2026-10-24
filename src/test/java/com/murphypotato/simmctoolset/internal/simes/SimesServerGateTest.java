package com.murphypotato.simmctoolset.internal.simes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SimesServerGateTest {
    @Test
    void acceptsPrimaryHostnameWithAndWithoutPort() {
        assertTrue(SimesServerGate.isTargetAddress("play.simmc.cn"));
        assertTrue(SimesServerGate.isTargetAddress("play.simmc.cn:25565"));
        assertTrue(SimesServerGate.isTargetAddress("PLAY.SIMMC.CN:7777."));
    }

    @Test
    void acceptsTheConfiguredMirrorHostnameWithAndWithoutPort() {
        assertTrue(SimesServerGate.isTargetAddress("simmc.silkysoftware.com"));
        assertTrue(SimesServerGate.isTargetAddress("SIMMC.SILKYSOFTWARE.COM:25565"));
    }

    @Test
    void acceptsOnlyTheConfiguredBackupAddressAndPort() {
        assertTrue(SimesServerGate.isTargetAddress("110.42.44.191:7777"));
        assertTrue(SimesServerGate.isTargetAddress("110.42.44.191:7777."));
        assertFalse(SimesServerGate.isTargetAddress("110.42.44.191"));
        assertFalse(SimesServerGate.isTargetAddress("110.42.44.191:25565"));
    }

    @Test
    void rejectsOtherOrMalformedAddresses() {
        assertFalse(SimesServerGate.isTargetAddress(null));
        assertFalse(SimesServerGate.isTargetAddress(""));
        assertFalse(SimesServerGate.isTargetAddress("   "));
        assertFalse(SimesServerGate.isTargetAddress("example.org"));
        assertFalse(SimesServerGate.isTargetAddress("example.org:7777"));
    }
}
