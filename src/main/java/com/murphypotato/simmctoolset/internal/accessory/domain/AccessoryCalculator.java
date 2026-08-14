package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AccessoryCalculator {
    public static final double BASE_DAMAGE = 10.0;
    public static final double TIE_TOLERANCE = 1e-6;
    public static final double STABLE_EXPECTED_FLOOR = 0.95;
    public static final double STABLE_IMPROVEMENT_MINIMUM = 0.01;

    private AccessoryCalculator() {
    }

    public static DamageBreakdown evaluate(Map<AccessorySlot, AccessoryRecord> accessories, WeaponMode weapon) {
        List<AffixRecord> affixes = accessories.values().stream().flatMap(item -> item.affixes().stream()).toList();
        return evaluateAffixes(affixes, weapon);
    }

    public static DamageBreakdown evaluateAffixes(Collection<AffixRecord> affixes, WeaponMode weapon) {
        double hunterMultiplier = 0;
        double critChancePoints = 0;
        double critDamage = 0;
        double bowMastery = 0;
        double swordMastery = 0;
        double bowFinal = 0;
        double swordFinal = 0;
        double bowExtra = 0;
        double swordExtra = 0;
        double damageReduction = 0;
        int ignored = 0;

        for (AffixRecord affix : affixes) {
            switch (affix.stat()) {
                case HUNTER_DAMAGE_MULTIPLIER -> hunterMultiplier += affix.value();
                case HUNTER_CRIT_CHANCE -> critChancePoints += affix.value();
                case HUNTER_CRIT_DAMAGE -> critDamage += affix.value();
                case BOW_MASTERY_MULTIPLIER -> bowMastery += affix.value();
                case SWORD_MASTERY_MULTIPLIER -> swordMastery += affix.value();
                case BOW_FINAL_DAMAGE -> bowFinal += affix.value();
                case SWORD_FINAL_DAMAGE -> swordFinal += affix.value();
                case BOW_EXTRA_DAMAGE -> bowExtra += affix.value();
                case SWORD_EXTRA_DAMAGE -> swordExtra += affix.value();
                case OTHER -> ignored++;
            }
        }

        double extraDamage = weapon == WeaponMode.BOW ? bowExtra : swordExtra;
        double masteryMultiplier = weapon == WeaponMode.BOW ? bowMastery : swordMastery;
        double finalDamage = weapon == WeaponMode.BOW ? bowFinal : swordFinal;
        double critChance = Math.min(critChancePoints / 100.0, 1.0);
        double baseBeforeCrit = ((BASE_DAMAGE + extraDamage) + BASE_DAMAGE * hunterMultiplier)
            * (1.0 + masteryMultiplier);
        double nonCrit = baseBeforeCrit + finalDamage;
        double crit = baseBeforeCrit * (1.0 + critDamage) + finalDamage;
        double expected = baseBeforeCrit * (1.0 + critChance * critDamage) + finalDamage;

        return new DamageBreakdown(
            expected,
            nonCrit,
            crit,
            extraDamage,
            hunterMultiplier,
            masteryMultiplier,
            critChance,
            critDamage,
            finalDamage,
            ignored,
            damageReduction,
            baseBeforeCrit
        );
    }

    public static LoadoutResult optimize(Collection<AccessoryRecord> pool, WeaponMode weapon) {
        EnumMap<AccessorySlot, List<AccessoryRecord>> choices = new EnumMap<>(AccessorySlot.class);
        for (AccessorySlot slot : AccessorySlot.values()) {
            List<AccessoryRecord> perSlot = new ArrayList<>();
            perSlot.add(AccessoryRecord.blank(slot));
            pool.stream().filter(item -> item.slot() == slot).forEach(perSlot::add);
            choices.put(slot, perSlot);
        }

        SearchState state = new SearchState();
        search(choices, weapon, 0, new EnumMap<>(AccessorySlot.class), state);
        return state.best;
    }

    public static LoadoutAnalysis analyze(Collection<AccessoryRecord> pool, WeaponMode weapon) {
        LoadoutResult expected = optimize(pool, weapon);
        StabilityProfile expectedStability = StabilityCalculator.analyze(expected.breakdown());
        if (!expectedStability.risky()) {
            return new LoadoutAnalysis(expected, expectedStability, Optional.empty(), Optional.empty());
        }

        double minimumExpected = expected.breakdown().expected() * STABLE_EXPECTED_FLOOR;
        LoadoutResult stableCandidate = optimizeStable(pool, weapon, minimumExpected);
        StabilityProfile stableProfile = StabilityCalculator.analyze(stableCandidate.breakdown());
        boolean changed = differs(expected, stableCandidate);
        boolean improved = stableProfile.stableFloor80()
            >= expectedStability.stableFloor80() * (1.0 + STABLE_IMPROVEMENT_MINIMUM) - TIE_TOLERANCE;
        if (!changed || !improved) {
            return new LoadoutAnalysis(expected, expectedStability, Optional.empty(), Optional.empty());
        }
        return new LoadoutAnalysis(expected, expectedStability, Optional.of(stableCandidate), Optional.of(stableProfile));
    }

    private static LoadoutResult optimizeStable(Collection<AccessoryRecord> pool, WeaponMode weapon, double minimumExpected) {
        EnumMap<AccessorySlot, List<AccessoryRecord>> choices = choices(pool);
        StableSearchState state = new StableSearchState(minimumExpected);
        searchStable(choices, weapon, 0, new EnumMap<>(AccessorySlot.class), state);
        return state.best;
    }

    private static EnumMap<AccessorySlot, List<AccessoryRecord>> choices(Collection<AccessoryRecord> pool) {
        EnumMap<AccessorySlot, List<AccessoryRecord>> choices = new EnumMap<>(AccessorySlot.class);
        for (AccessorySlot slot : AccessorySlot.values()) {
            List<AccessoryRecord> perSlot = new ArrayList<>();
            perSlot.add(AccessoryRecord.blank(slot));
            pool.stream().filter(item -> item.slot() == slot).forEach(perSlot::add);
            choices.put(slot, perSlot);
        }
        return choices;
    }

    private static void search(
        EnumMap<AccessorySlot, List<AccessoryRecord>> choices,
        WeaponMode weapon,
        int index,
        EnumMap<AccessorySlot, AccessoryRecord> current,
        SearchState state
    ) {
        AccessorySlot[] slots = AccessorySlot.values();
        if (index == slots.length) {
            DamageBreakdown breakdown = evaluate(current, weapon);
            int realCount = (int) current.values().stream().filter(item -> !item.isBlank()).count();
            LoadoutResult candidate = new LoadoutResult(current, breakdown, realCount);
            if (isBetter(candidate, state.best)) state.best = candidate;
            return;
        }

        AccessorySlot slot = slots[index];
        for (AccessoryRecord accessory : choices.get(slot)) {
            current.put(slot, accessory);
            search(choices, weapon, index + 1, current, state);
        }
        current.remove(slot);
    }

    private static boolean isBetter(LoadoutResult candidate, LoadoutResult current) {
        if (current == null) return true;
        double delta = candidate.breakdown().expected() - current.breakdown().expected();
        if (delta > TIE_TOLERANCE) return true;
        return Math.abs(delta) <= TIE_TOLERANCE && candidate.realItemCount() > current.realItemCount();
    }

    private static void searchStable(
        EnumMap<AccessorySlot, List<AccessoryRecord>> choices,
        WeaponMode weapon,
        int index,
        EnumMap<AccessorySlot, AccessoryRecord> current,
        StableSearchState state
    ) {
        AccessorySlot[] slots = AccessorySlot.values();
        if (index == slots.length) {
            DamageBreakdown breakdown = evaluate(current, weapon);
            if (breakdown.expected() + TIE_TOLERANCE < state.minimumExpected) return;
            int realCount = (int) current.values().stream().filter(item -> !item.isBlank()).count();
            LoadoutResult candidate = new LoadoutResult(current, breakdown, realCount);
            StabilityProfile stability = StabilityCalculator.analyze(breakdown);
            if (state.best == null || isMoreStable(candidate, stability, state.best, state.stability)) {
                state.best = candidate;
                state.stability = stability;
            }
            return;
        }
        AccessorySlot slot = slots[index];
        for (AccessoryRecord accessory : choices.get(slot)) {
            current.put(slot, accessory);
            searchStable(choices, weapon, index + 1, current, state);
        }
        current.remove(slot);
    }

    private static boolean isMoreStable(
        LoadoutResult candidate,
        StabilityProfile candidateStability,
        LoadoutResult current,
        StabilityProfile currentStability
    ) {
        double floorDelta = candidateStability.stableFloor80() - currentStability.stableFloor80();
        if (floorDelta > TIE_TOLERANCE) return true;
        if (Math.abs(floorDelta) > TIE_TOLERANCE) return false;
        double expectedDelta = candidate.breakdown().expected() - current.breakdown().expected();
        if (expectedDelta > TIE_TOLERANCE) return true;
        if (Math.abs(expectedDelta) > TIE_TOLERANCE) return false;
        double noCritDelta = currentStability.noCritTenProbability() - candidateStability.noCritTenProbability();
        if (noCritDelta > TIE_TOLERANCE) return true;
        if (Math.abs(noCritDelta) > TIE_TOLERANCE) return false;
        return candidate.realItemCount() > current.realItemCount();
    }

    private static boolean differs(LoadoutResult left, LoadoutResult right) {
        for (AccessorySlot slot : AccessorySlot.values()) {
            if (!left.accessories().get(slot).id().equals(right.accessories().get(slot).id())) return true;
        }
        return false;
    }

    private static final class SearchState {
        private LoadoutResult best;
    }

    private static final class StableSearchState {
        private final double minimumExpected;
        private LoadoutResult best;
        private StabilityProfile stability;

        private StableSearchState(double minimumExpected) {
            this.minimumExpected = minimumExpected;
        }
    }
}
