package com.murphypotato.simmctoolset.internal.simes;

/** Tracks server calibration separately from the client's projected zero point. */
final class SimesFermentationTimer {
    enum State { UNKNOWN, CALIBRATED, EXPECTED_DONE, CONFIRMED, INVALIDATED }

    private long calibratedRemainingMillis = -1L;
    private long calibratedAtNanos;
    private State state = State.UNKNOWN;

    void calibrate(String serverRemaining, long nowNanos) {
        long parsed = FermentationCountdown.parseMillis(serverRemaining);
        calibratedRemainingMillis = parsed;
        calibratedAtNanos = parsed >= 0L ? nowNanos : 0L;
        state = parsed >= 0L ? State.CALIBRATED : State.UNKNOWN;
    }

    void invalidate(String ignoredStatus) {
        calibratedRemainingMillis = -1L;
        calibratedAtNanos = 0L;
        state = State.INVALIDATED;
    }

    void markServerComplete(long nowNanos) {
        calibratedRemainingMillis = 0L;
        calibratedAtNanos = nowNanos;
        state = State.CONFIRMED;
    }

    State stateAt(long nowNanos) {
        if (state != State.CALIBRATED) return state;
        return FermentationCountdown.remainingMillis(calibratedRemainingMillis, calibratedAtNanos, nowNanos) > 0L
                ? State.CALIBRATED : State.EXPECTED_DONE;
    }

    long remainingMillisAt(long nowNanos) {
        State current = stateAt(nowNanos);
        if (current == State.CALIBRATED || current == State.EXPECTED_DONE || current == State.CONFIRMED) {
            return FermentationCountdown.remainingMillis(calibratedRemainingMillis, calibratedAtNanos, nowNanos);
        }
        return -1L;
    }

    String displayAt(long nowNanos) {
        return switch (stateAt(nowNanos)) {
            case INVALIDATED -> "请使用烹饪钟重新校准";
            case CONFIRMED -> "已完成（服务器确认）";
            case EXPECTED_DONE -> "预计已到，等待服务器确认";
            case CALIBRATED -> FermentationCountdown.formatMillis(remainingMillisAt(nowNanos))
                    + "（服务器校准·本地推算）";
            case UNKNOWN -> "等待烹饪钟";
        };
    }
}
