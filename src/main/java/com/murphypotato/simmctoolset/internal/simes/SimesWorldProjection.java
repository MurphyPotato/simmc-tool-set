package com.murphypotato.simmctoolset.internal.simes;

import java.util.Optional;

/** Pure NDC-to-screen conversion used by the world-render projection path. */
final class SimesWorldProjection {
    private SimesWorldProjection() {
    }

    static Optional<ScreenPoint> project(float ndcX, float ndcY, float w, int scaledWidth, int scaledHeight) {
        if (w <= 0.01F || scaledWidth <= 0 || scaledHeight <= 0) return Optional.empty();
        if (ndcX < -1.15F || ndcX > 1.15F || ndcY < -1.15F || ndcY > 1.15F) return Optional.empty();
        int x = Math.round((ndcX + 1.0F) * 0.5F * scaledWidth);
        int y = Math.round((1.0F - ndcY) * 0.5F * scaledHeight);
        return Optional.of(new ScreenPoint(x, y));
    }

    record ScreenPoint(int x, int y) {
    }
}
