package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.Optional;

public record LoadoutAnalysis(
    LoadoutResult expected,
    StabilityProfile expectedStability,
    Optional<LoadoutResult> stable,
    Optional<StabilityProfile> stableStability
) {
    public LoadoutAnalysis {
        stable = stable == null ? Optional.empty() : stable;
        stableStability = stableStability == null ? Optional.empty() : stableStability;
    }

    public LoadoutResult result(PlanVariant variant) {
        return variant == PlanVariant.STABLE ? stable.orElse(expected) : expected;
    }

    public StabilityProfile stability(PlanVariant variant) {
        return variant == PlanVariant.STABLE ? stableStability.orElse(expectedStability) : expectedStability;
    }
}
