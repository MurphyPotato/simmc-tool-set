package com.murphypotato.simmctoolset.internal.simes;

import java.util.List;

/** Client-only projection of a normal cookware vessel observed through display entities. */
final class SimesCookerState {
    static final long COOK_ESTIMATE_MS = 30_000L;
    enum Status { IDLE, READY, COOKING, COMPLETED, FAILED }

    private String cookwareName = "厨具";
    private List<String> contents = List.of();
    private List<String> confirmedContents = List.of();
    private List<String> pendingResult = List.of();
    private long estimateStartedAt;
    private long lastSeen;
    private boolean open;
    private boolean lidStateKnown;
    private Status status = Status.IDLE;

    void observe(String observedCookwareName, boolean observedOpen, List<String> observedContents, long now) {
        lastSeen = now;
        if (observedCookwareName != null && !observedCookwareName.isBlank()) cookwareName = observedCookwareName;
        List<String> safeContents = observedContents == null ? List.of() : observedContents.stream().sorted().toList();
        boolean closedNow = !observedOpen;
        boolean justClosed = lidStateKnown && open && closedNow;
        open = observedOpen;
        lidStateKnown = true;

        contents = safeContents;
        if (safeContents.isEmpty()) {
            acceptContents(safeContents);
            stop(Status.IDLE);
            return;
        }
        boolean changed = !safeContents.equals(confirmedContents);
        if (isCompleted() || isFailed()) {
            if (changed) {
                acceptContents(safeContents);
                stop(Status.READY);
            }
            return; // Same result stays latched, even when the lid opens.
        }
        if (status == Status.COOKING && changed && !isPureAddition(confirmedContents, safeContents)) {
            if (!safeContents.equals(pendingResult)) {
                pendingResult = safeContents;
                return;
            }
            boolean newCharcoal = safeContents.stream().anyMatch(SimesCookerState::isCharcoal)
                    && confirmedContents.stream().noneMatch(SimesCookerState::isCharcoal);
            acceptContents(safeContents);
            stop(newCharcoal && cookwareName.contains("煎锅") ? Status.FAILED : Status.COMPLETED);
            return;
        }
        pendingResult = List.of();
        if (open) {
            acceptContents(safeContents);
            stop(Status.READY);
        } else if (status == Status.IDLE || justClosed || (status == Status.COOKING && changed)) {
            acceptContents(safeContents);
            estimateStartedAt = now;
            status = Status.COOKING;
        } else if (changed) {
            acceptContents(safeContents);
        }
    }

    private static boolean isCharcoal(String key) {
        return key.equals("minecraft:charcoal") || key.startsWith("minecraft:charcoal|");
    }

    private static boolean isPureAddition(List<String> before, List<String> after) {
        if (after.size() <= before.size()) return false;
        java.util.ArrayList<String> remaining = new java.util.ArrayList<>(after);
        for (String item : before) if (!remaining.remove(item)) return false;
        return true;
    }

    private void acceptContents(List<String> value) {
        confirmedContents = value;
        pendingResult = List.of();
    }

    private void stop(Status value) {
        status = value;
        estimateStartedAt = 0L;
    }

    void clear() {
        cookwareName = "厨具";
        contents = List.of();
        confirmedContents = List.of();
        pendingResult = List.of();
        estimateStartedAt = 0L;
        lastSeen = 0L;
        status = Status.IDLE;
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
        return status == Status.COMPLETED;
    }

    Status status() { return status; }
    boolean isFailed() { return status == Status.FAILED; }
    int statusColor() {
        return switch (status) {
            case IDLE -> 0;
            case READY -> 0xFF3FA9FF;
            case COOKING -> 0xFFFFC233;
            case COMPLETED -> 0xFF45E06F;
            case FAILED -> 0xFFFF4040;
        };
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
