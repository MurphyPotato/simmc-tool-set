package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.Objects;
import java.util.UUID;

public record AffixRecord(
    String id,
    AffixStat stat,
    double value,
    AffixUnit unit,
    String label,
    String rawText,
    String warning
) {
    public AffixRecord {
        id = clean(id, UUID.randomUUID().toString());
        stat = Objects.requireNonNullElse(stat, AffixStat.OTHER);
        unit = Objects.requireNonNullElse(unit, AffixUnit.NONE);
        if (!Double.isFinite(value)) value = 0;
        label = clean(label, stat.label());
        rawText = Objects.requireNonNullElse(rawText, "");
        warning = Objects.requireNonNullElse(warning, "");
    }

    public AffixRecord withValue(AffixStat nextStat, double nextValue, String nextLabel) {
        return new AffixRecord(id, nextStat, nextValue, unit, nextLabel, rawText, "");
    }

    public String valueText() {
        return "+" + java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() + unit.suffix();
    }

    public String displayText() {
        return label + " " + valueText();
    }

    private static String clean(String value, String fallback) {
        String result = Objects.requireNonNullElse(value, "").strip();
        return result.isEmpty() ? fallback : result;
    }
}
