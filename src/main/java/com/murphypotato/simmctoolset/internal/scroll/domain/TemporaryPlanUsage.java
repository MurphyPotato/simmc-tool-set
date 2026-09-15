package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Ephemeral preview usage; never persisted until explicit confirmation. */
public final class TemporaryPlanUsage {
    private final UUID playerId;
    private final Map<String, Integer> usage = new LinkedHashMap<>();
    public TemporaryPlanUsage(UUID playerId) { this.playerId = playerId; }
    public UUID playerId() { return playerId; }
    public Map<String,Integer> usage() { return Map.copyOf(usage); }
    public void replace(Map<String,Integer> next) { usage.clear(); if (next != null) next.forEach((k,v) -> { if (v > 0) usage.put(k,v); }); }
    public void clear() { usage.clear(); }
}
