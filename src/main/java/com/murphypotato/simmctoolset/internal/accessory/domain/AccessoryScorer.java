package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AccessoryScorer {
    private static final int MAX_EXACT_AFFIXES = 16;
    private static final double SCORE_PER_BASE_DAMAGE = 100.0 / AccessoryCalculator.BASE_DAMAGE;

    private AccessoryScorer() {
    }

    public static Map<String, AccessoryScore> scorePool(
        Collection<AccessoryRecord> pool,
        LoadoutResult reference,
        WeaponMode weapon
    ) {
        Map<String, AccessoryScore> result = new LinkedHashMap<>();
        for (AccessoryRecord candidate : pool) {
            EnumMap<AccessorySlot, AccessoryRecord> context = new EnumMap<>(AccessorySlot.class);
            context.putAll(reference.accessories());
            context.put(candidate.slot(), candidate);
            result.put(candidate.id(), score(candidate, context, reference.breakdown().expected(), weapon));
        }
        return Map.copyOf(result);
    }

    public static AccessoryScore score(
        AccessoryRecord candidate,
        Map<AccessorySlot, AccessoryRecord> context,
        double bestExpected,
        WeaponMode weapon
    ) {
        DamageBreakdown equipped = AccessoryCalculator.evaluate(context, weapon);
        List<IndexedAffix> effectiveAffixes = new ArrayList<>();
        for (AccessoryRecord accessory : context.values()) {
            for (AffixRecord affix : accessory.affixes()) {
                if (isEffective(affix.stat(), weapon)) effectiveAffixes.add(new IndexedAffix(accessory.id(), affix));
            }
        }

        if (effectiveAffixes.size() > MAX_EXACT_AFFIXES) {
            return unavailable(candidate, weapon, equipped.expected(), bestExpected, "有效词条超过16条，无法执行精确评分");
        }

        Map<String, Double> damageContributions = shapley(effectiveAffixes, weapon);
        List<AffixContribution> candidateContributions = new ArrayList<>();
        double totalScore = 0;
        for (AffixRecord affix : candidate.affixes()) {
            boolean effective = isEffective(affix.stat(), weapon);
            double contribution = effective ? damageContributions.getOrDefault(affix.id(), 0.0) : 0.0;
            double points = contribution * SCORE_PER_BASE_DAMAGE;
            totalScore += points;
            candidateContributions.add(new AffixContribution(
                affix.id(), affix.label(), contribution, points, effective
            ));
        }
        return new AccessoryScore(
            candidate.id(), weapon, totalScore, equipped.expected(), equipped.expected() - bestExpected,
            candidateContributions, ""
        );
    }

    public static LoadoutScore scoreLoadout(LoadoutResult loadout, WeaponMode weapon) {
        List<IndexedAffix> effectiveAffixes = new ArrayList<>();
        EnumMap<AffixStat, Double> effectiveTotals = new EnumMap<>(AffixStat.class);
        for (AccessoryRecord accessory : loadout.accessories().values()) {
            for (AffixRecord affix : accessory.affixes()) {
                if (!isEffective(affix.stat(), weapon)) continue;
                effectiveAffixes.add(new IndexedAffix(accessory.id(), affix));
                effectiveTotals.merge(affix.stat(), affix.value(), Double::sum);
            }
        }

        if (effectiveAffixes.size() > MAX_EXACT_AFFIXES) {
            return new LoadoutScore(
                weapon,
                (loadout.breakdown().expected() - AccessoryCalculator.BASE_DAMAGE) * SCORE_PER_BASE_DAMAGE,
                Map.of(),
                List.of(),
                effectiveTotals,
                "有效词条超过16条，无法执行精确套装评分"
            );
        }

        Map<String, Double> damageContributions = shapley(effectiveAffixes, weapon);
        Map<String, Double> accessoryScores = new LinkedHashMap<>();
        List<AffixContribution> affixContributions = new ArrayList<>();
        for (AccessoryRecord accessory : loadout.accessories().values()) {
            if (!accessory.isBlank()) accessoryScores.put(accessory.id(), 0.0);
            for (AffixRecord affix : accessory.affixes()) {
                boolean effective = isEffective(affix.stat(), weapon);
                double contribution = effective ? damageContributions.getOrDefault(affix.id(), 0.0) : 0.0;
                double points = contribution * SCORE_PER_BASE_DAMAGE;
                if (!accessory.isBlank()) accessoryScores.merge(accessory.id(), points, Double::sum);
                affixContributions.add(new AffixContribution(
                    affix.id(), affix.label(), contribution, points, effective
                ));
            }
        }

        return new LoadoutScore(
            weapon,
            (loadout.breakdown().expected() - AccessoryCalculator.BASE_DAMAGE) * SCORE_PER_BASE_DAMAGE,
            accessoryScores,
            affixContributions,
            effectiveTotals,
            ""
        );
    }

    public static boolean isEffective(AffixStat stat, WeaponMode weapon) {
        return switch (stat) {
            case HUNTER_DAMAGE_MULTIPLIER, HUNTER_CRIT_CHANCE, HUNTER_CRIT_DAMAGE -> true;
            case BOW_MASTERY_MULTIPLIER, BOW_FINAL_DAMAGE, BOW_EXTRA_DAMAGE -> weapon == WeaponMode.BOW;
            case SWORD_MASTERY_MULTIPLIER, SWORD_FINAL_DAMAGE, SWORD_EXTRA_DAMAGE -> weapon == WeaponMode.SWORD;
            case OTHER -> false;
        };
    }

    private static Map<String, Double> shapley(List<IndexedAffix> affixes, WeaponMode weapon) {
        int count = affixes.size();
        if (count == 0) return Map.of();
        int subsetCount = 1 << count;
        double[] values = new double[subsetCount];
        for (int mask = 0; mask < subsetCount; mask++) {
            List<AffixRecord> subset = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                if ((mask & (1 << index)) != 0) subset.add(affixes.get(index).affix());
            }
            values[mask] = AccessoryCalculator.evaluateAffixes(subset, weapon).expected();
        }

        double[] factorial = new double[count + 1];
        factorial[0] = 1;
        for (int index = 1; index <= count; index++) factorial[index] = factorial[index - 1] * index;
        Map<String, Double> result = new HashMap<>();
        for (int target = 0; target < count; target++) {
            double contribution = 0;
            int targetBit = 1 << target;
            for (int mask = 0; mask < subsetCount; mask++) {
                if ((mask & targetBit) != 0) continue;
                int size = Integer.bitCount(mask);
                double weight = factorial[size] * factorial[count - size - 1] / factorial[count];
                contribution += weight * (values[mask | targetBit] - values[mask]);
            }
            result.put(affixes.get(target).affix().id(), contribution);
        }
        return result;
    }

    private static AccessoryScore unavailable(
        AccessoryRecord candidate,
        WeaponMode weapon,
        double expected,
        double bestExpected,
        String warning
    ) {
        List<AffixContribution> affixes = candidate.affixes().stream()
            .map(affix -> new AffixContribution(affix.id(), affix.label(), 0, 0, isEffective(affix.stat(), weapon)))
            .toList();
        return new AccessoryScore(candidate.id(), weapon, 0, expected, expected - bestExpected, affixes, warning);
    }

    private record IndexedAffix(String accessoryId, AffixRecord affix) {
    }
}
