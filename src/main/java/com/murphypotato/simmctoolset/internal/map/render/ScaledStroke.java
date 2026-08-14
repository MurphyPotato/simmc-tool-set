package com.murphypotato.simmctoolset.internal.map.render;

import java.util.Optional;

/** Computes a drawable screen-space stroke from a world-scale marker style. */
public final class ScaledStroke {
    public record Plan(int argb, double width) { }

    private ScaledStroke() { }

    public static boolean hasVisibleAlpha(int argb) {
        return (argb >>> 24) != 0;
    }

    public static Optional<Plan> plan(int argb, double weight, double scale) {
        if (!Double.isFinite(weight) || weight <= 0
                || !Double.isFinite(scale) || scale <= 0
                || !hasVisibleAlpha(argb)) return Optional.empty();
        double scaledWidth = weight * scale;
        if (!Double.isFinite(scaledWidth) || scaledWidth <= 0) return Optional.empty();
        if (scaledWidth >= 1) return Optional.of(new Plan(argb, scaledWidth));

        int sourceAlpha = argb >>> 24;
        int scaledAlpha = Math.max(0, Math.min(255, (int) Math.round(sourceAlpha * scaledWidth)));
        if (scaledAlpha == 0) return Optional.empty();
        return Optional.of(new Plan((scaledAlpha << 24) | (argb & 0xFFFFFF), 1));
    }
}
