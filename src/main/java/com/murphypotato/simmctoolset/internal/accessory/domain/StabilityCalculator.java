package com.murphypotato.simmctoolset.internal.accessory.domain;

import java.util.ArrayList;
import java.util.List;

public final class StabilityCalculator {
    public static final int ATTACK_WINDOW = 10;
    public static final double FLOOR_CONFIDENCE = 0.80;

    private StabilityCalculator() {
    }

    public static StabilityProfile analyze(DamageBreakdown damage) {
        double chance = clamp(damage.critChance(), 0, 1);
        double noCrit = Math.pow(1.0 - chance, ATTACK_WINDOW);
        int guaranteedCrits = lowerCritQuantile(chance);
        double stableFloor = damage.nonCrit()
            + (damage.crit() - damage.nonCrit()) * guaranteedCrits / ATTACK_WINDOW;
        double gap = ratio(Math.max(0, damage.expected() - stableFloor), damage.expected());
        double lift = ratio(Math.max(0, damage.crit() - damage.nonCrit()), damage.nonCrit());
        double dependency = ratio(Math.max(0, damage.expected() - damage.nonCrit()), damage.expected());

        boolean risky = noCrit >= 0.30 && lift >= 0.50 && dependency >= 0.05;
        RiskLevel level = risky
            ? (noCrit >= 0.50 || gap >= 0.15 ? RiskLevel.HIGH : RiskLevel.CAUTION)
            : RiskLevel.NONE;

        List<String> warnings = new ArrayList<>();
        if (chance < 0.01 && damage.critDamage() > 0) {
            warnings.add("暴击率接近零，当前爆伤几乎不产生期望收益");
        }
        if (risky) {
            warnings.add("10次攻击一次不暴击概率 " + percent(noCrit) + "，短期输出波动较高");
        }
        return new StabilityProfile(noCrit, stableFloor, gap, lift, dependency, level, warnings);
    }

    static int lowerCritQuantile(double chance) {
        int result = 0;
        for (int crits = 1; crits <= ATTACK_WINDOW; crits++) {
            if (tailProbability(ATTACK_WINDOW, chance, crits) + 1e-12 >= FLOOR_CONFIDENCE) result = crits;
            else break;
        }
        return result;
    }

    private static double tailProbability(int trials, double chance, int minimumSuccesses) {
        double result = 0;
        for (int successes = minimumSuccesses; successes <= trials; successes++) {
            result += combination(trials, successes)
                * Math.pow(chance, successes)
                * Math.pow(1.0 - chance, trials - successes);
        }
        return result;
    }

    private static long combination(int n, int k) {
        int effective = Math.min(k, n - k);
        long result = 1;
        for (int index = 1; index <= effective; index++) {
            result = result * (n - effective + index) / index;
        }
        return result;
    }

    private static double ratio(double numerator, double denominator) {
        return denominator <= 1e-12 ? 0 : numerator / denominator;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static String percent(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", value * 100.0);
    }
}
