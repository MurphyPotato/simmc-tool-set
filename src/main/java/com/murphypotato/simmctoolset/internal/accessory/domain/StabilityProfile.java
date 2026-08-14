package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.List;

public record StabilityProfile(
    double noCritTenProbability,
    double stableFloor80,
    double stabilityGapRatio,
    double critLiftRatio,
    double critDependencyRatio,
    RiskLevel riskLevel,
    List<String> warnings
) {
    public StabilityProfile {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        riskLevel = riskLevel == null ? RiskLevel.NONE : riskLevel;
    }

    public boolean risky() {
        return riskLevel != RiskLevel.NONE;
    }
}
