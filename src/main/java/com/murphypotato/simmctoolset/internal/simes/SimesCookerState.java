package com.murphypotato.simmctoolset.internal.simes;

import java.util.List;

/** Client-only projection of a normal cookware vessel observed through display entities. */
final class SimesCookerState {
    static final long COOK_ESTIMATE_MS = 60_000L;

    private String cookwareName = "厨具";
    private List<String> contents = List.of();
    private String signature = "";
    private long estimateStartedAt;
    private long lastSeen;
    private boolean completed;
    private boolean open;
    private boolean lidStateKnown;

    void observe(String observedCookwareName, boolean observedOpen, List<String> observedContents, long now) {
        lastSeen = now;
        if (observedCookwareName != null && !observedCookwareName.isBlank()) cookwareName = observedCookwareName;
        List<String> safeContents = observedContents == null ? List.of() : List.copyOf(observedContents);
        boolean closedNow = !observedOpen;
        boolean justClosed = lidStateKnown && open && closedNow;
        open = observedOpen;
        lidStateKnown = true;

        String currentSignature = safeContents.stream().sorted().reduce("", (left, right) -> left + "|" + right);
        int previousCount = contents.size();
        if (safeContents.isEmpty() || open) {
            estimateStartedAt = 0L;
            completed = false;
        } else if (!safeContents.isEmpty() && (justClosed || estimateStartedAt == 0L)) {
            estimateStartedAt = now;
        }
        if (currentSignature.equals(signature)) return;

        // A client-side display-entity count change cannot prove that the server
        // completed the recipe. Keep the local estimate conservative and wait for
        // authoritative server data before exposing a completed state.
        completed = false;
        if (!open && !safeContents.isEmpty() && safeContents.size() > previousCount) {
            estimateStartedAt = now;
        }
        contents = safeContents;
        signature = currentSignature;
    }

    void clear() {
        cookwareName = "厨具";
        contents = List.of();
        signature = "";
        estimateStartedAt = 0L;
        lastSeen = 0L;
        completed = false;
        open = false;
        lidStateKnown = false;
    }

    boolean hasContents() {
        return !contents.isEmpty();
    }

    boolean isOpen() {
        return open;
    }

    boolean isCompleted() {
        return completed;
    }

    long estimateStartedAt() {
        return estimateStartedAt;
    }

    long lastSeen() {
        return lastSeen;
    }

    long remainingMillis(long now) {
        if (estimateStartedAt == 0L) return 0L;
        return Math.max(0L, COOK_ESTIMATE_MS - (now - estimateStartedAt));
    }

    String cookwareName() {
        return cookwareName;
    }

    List<String> contents() {
        return contents;
    }
}
