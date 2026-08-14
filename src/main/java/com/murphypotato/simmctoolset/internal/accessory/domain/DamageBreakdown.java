package com.murphypotato.simmctoolset.internal.accessory.domain;

public record DamageBreakdown(
    double expected,
    double nonCrit,
    double crit,
    double extraDamage,
    double hunterMultiplier,
    double masteryMultiplier,
    double critChance,
    double critDamage,
    double finalDamage,
    int ignoredAffixes,
    double damageReduction,
    double baseBeforeCrit
) {
}
