package com.murphypotato.simmctoolset.client;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DiagnosticLogTest {
    @Test
    void formatsDiagnosticTimesInUtcPlusEight() {
        assertEquals("2026-08-28T08:00:00+08:00",
                DiagnosticLog.formatTimestamp(Instant.parse("2026-08-28T00:00:00Z")));
        assertEquals("2026-08-29T07:30:00+08:00",
                DiagnosticLog.formatTimestamp(Instant.parse("2026-08-28T23:30:00Z")));
    }
}
