package com.murphypotato.simmctoolset.internal.scroll.domain;

public enum SearchBudget {
    FAST(1_000_000_000L, 500),
    BALANCED(3_000_000_000L, 1400),
    EXTREME(9_000_000_000L, 2600);

    private final long nanos;
    private final int beamWidth;
    SearchBudget(long nanos, int beamWidth) { this.nanos = nanos; this.beamWidth = beamWidth; }
    public long nanos() { return nanos; }
    public int beamWidth() { return beamWidth; }
}
