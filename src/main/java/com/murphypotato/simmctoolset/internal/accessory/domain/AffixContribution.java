package com.murphypotato.simmctoolset.internal.accessory.domain;

public record AffixContribution(
    String affixId,
    String label,
    double expectedDamageContribution,
    double scorePoints,
    boolean effective
) {
}
