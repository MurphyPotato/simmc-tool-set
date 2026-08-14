package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.List;

public record AccessoryScore(
    String accessoryId,
    WeaponMode weapon,
    double totalScore,
    double expectedWhenEquipped,
    double gapToBest,
    List<AffixContribution> affixes,
    String warning
) {
    public AccessoryScore {
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        warning = warning == null ? "" : warning;
    }

    public boolean available() {
        return warning.isEmpty();
    }
}
