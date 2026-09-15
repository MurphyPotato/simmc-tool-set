package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Ephemeral preview usage; never persisted until explicit confirmation. */
public final class TemporaryPlanUsage {
    private final UUID playerId;
    private final Map<String, Integer> usage = new LinkedHashMap<>();
    private long baseRevision = -1L;
    private LocalDate baseDate;

    public TemporaryPlanUsage(UUID playerId) { this.playerId = playerId; }

    public UUID playerId() { return playerId; }
    public Map<String,Integer> usage() { return Map.copyOf(usage); }
    public long baseRevision() { return baseRevision; }
    public LocalDate baseDate() { return baseDate; }

    /**
     * Replaces, rather than adds to, the current preview.  The revision binds
     * the preview to the committed daily ledger it was calculated against, so
     * a later committed change cannot accidentally reuse stale preview M.
     */
    public void replace(Map<String,Integer> next, LocalDate date, long revision) {
        if (date == null) throw new IllegalArgumentException("记录日期不能为空");
        if (revision < 0) throw new IllegalArgumentException("记录版本不能为负数");
        usage.clear();
        if (next != null) {
            next.forEach((k, v) -> {
                if (k == null || k.isBlank() || v == null || v < 0) {
                    throw new IllegalArgumentException("临时材料数量必须为非负整数");
                }
                if (v > 0) usage.put(k, v);
            });
        }
        baseDate = date;
        baseRevision = revision;
    }

    /** Compatibility helper for domain callers that do not have a ledger revision. */
    public void replace(Map<String,Integer> next) {
        replace(next, LocalDate.MIN, 0L);
    }

    public boolean matches(LocalDate date, long revision) {
        return date != null && baseDate != null && baseDate.equals(date)
            && baseRevision >= 0 && baseRevision == revision;
    }

    public void clear() {
        usage.clear();
        baseRevision = -1L;
        baseDate = null;
    }
}
