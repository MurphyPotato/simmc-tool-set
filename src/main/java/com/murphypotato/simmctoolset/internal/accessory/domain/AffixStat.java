package com.murphypotato.simmctoolset.internal.accessory.domain;

import com.google.gson.annotations.SerializedName;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public enum AffixStat {
    @SerializedName("hunterDamageMultiplier")
    HUNTER_DAMAGE_MULTIPLIER("hunterDamageMultiplier", "旅猎伤害加成倍率", true, 0.02, 0.10,
        "旅猎伤害加成倍率", "旅猎伤害倍率", "旅猎倍率"),
    @SerializedName("hunterCritChance")
    HUNTER_CRIT_CHANCE("hunterCritChance", "旅猎暴击概率", true, 1.0, 200.0,
        "旅猎暴击概率", "旅猎暴击率", "暴击率"),
    @SerializedName("hunterCritDamage")
    HUNTER_CRIT_DAMAGE("hunterCritDamage", "旅猎暴击效果", true, 0.01, 1.0,
        "旅猎暴击效果", "旅猎爆伤", "爆伤"),
    @SerializedName("bowMasteryMultiplier")
    BOW_MASTERY_MULTIPLIER("bowMasteryMultiplier", "弓专精伤害倍率", true, 0.02, 0.07,
        "弓专精伤害倍率", "弓专精伤害倍率加成"),
    @SerializedName("swordMasteryMultiplier")
    SWORD_MASTERY_MULTIPLIER("swordMasteryMultiplier", "剑专精伤害倍率", true, 0.02, 0.07,
        "剑专精伤害倍率", "剑专精伤害倍率加成"),
    @SerializedName("bowFinalDamage")
    BOW_FINAL_DAMAGE("bowFinalDamage", "弓专精最终伤害", true, 0.05, 2.0,
        "弓专精最终伤害", "弓专精最终伤害加成"),
    @SerializedName("swordFinalDamage")
    SWORD_FINAL_DAMAGE("swordFinalDamage", "剑专精最终伤害", true, 0.05, 2.0,
        "剑专精最终伤害", "剑专精最终伤害加成"),
    @SerializedName("bowExtraDamage")
    BOW_EXTRA_DAMAGE("bowExtraDamage", "弓额外伤害", true, 1.0, 20.0,
        "弓额外伤害", "弓额外伤害加成"),
    @SerializedName("swordExtraDamage")
    SWORD_EXTRA_DAMAGE("swordExtraDamage", "剑额外伤害", true, 1.0, 20.0,
        "剑额外伤害", "剑额外伤害加成"),
    @SerializedName("OTHER")
    OTHER("OTHER", "非伤害类词条", false, null, null);

    private final String id;
    private final String label;
    private final boolean combat;
    private final Double minimum;
    private final Double maximum;
    private final List<String> aliases;
    private static final Set<String> KNOWN_NON_DAMAGE_LABELS = Set.of(
        "百分比伤害减免", "伤害减免", "潜行速度"
    );

    AffixStat(
        String id,
        String label,
        boolean combat,
        Double minimum,
        Double maximum,
        String... aliases
    ) {
        this.id = id;
        this.label = label;
        this.combat = combat;
        this.minimum = minimum;
        this.maximum = maximum;
        this.aliases = List.of(aliases);
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public boolean combat() {
        return combat;
    }

    public List<String> aliases() {
        return aliases;
    }

    public boolean hasAdvisoryRange() {
        return minimum != null && maximum != null;
    }

    public boolean isWithinAdvisoryRange(double value) {
        return !hasAdvisoryRange() || value >= minimum && value <= maximum;
    }

    public String advisoryRangeLabel() {
        return hasAdvisoryRange() ? format(minimum) + "-" + format(maximum) : "";
    }

    public static Optional<AffixStat> fromLabel(String label) {
        String normalized = label.trim();
        return Arrays.stream(values())
            .filter(stat -> stat != OTHER && stat.aliases.contains(normalized))
            .findFirst();
    }

    public static boolean isKnownNonDamageLabel(String label) {
        return label != null && KNOWN_NON_DAMAGE_LABELS.contains(label.strip());
    }

    private static String format(double value) {
        if (value == Math.rint(value)) return Long.toString((long) value);
        java.math.BigDecimal decimal = java.math.BigDecimal.valueOf(value).stripTrailingZeros();
        if (value > 0 && value < 1 && decimal.scale() < 2) decimal = decimal.setScale(2);
        return decimal.toPlainString();
    }
}
