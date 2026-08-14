package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record LoadoutScore(
    WeaponMode weapon,
    double totalScore,
    Map<String, Double> accessoryScores,
    List<AffixContribution> affixes,
    Map<AffixStat, Double> effectiveAffixTotals,
    String warning
) {
    public LoadoutScore {
        accessoryScores = accessoryScores == null ? Map.of() : Map.copyOf(accessoryScores);
        affixes = affixes == null ? List.of() : List.copyOf(affixes);
        EnumMap<AffixStat, Double> totals = new EnumMap<>(AffixStat.class);
        if (effectiveAffixTotals != null) totals.putAll(effectiveAffixTotals);
        effectiveAffixTotals = Collections.unmodifiableMap(totals);
        warning = warning == null ? "" : warning;
    }

    public boolean available() {
        return warning.isEmpty();
    }

    public double accessoryScore(String accessoryId) {
        return accessoryScores.getOrDefault(accessoryId, 0.0);
    }
}
