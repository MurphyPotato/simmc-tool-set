package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** High-precision cumulative material decay model. */
public final class MaterialDecay {
    public static final MathContext MATH_CONTEXT = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final BigDecimal INTERCEPT = BigDecimal.valueOf(64);
    private static final BigDecimal LINEAR = new BigDecimal("0.714296");
    private static final BigDecimal QUADRATIC = new BigDecimal("0.00271535");
    private static final BigDecimal COMMON_RANGE_MAX = BigDecimal.valueOf(255);

    private MaterialDecay() {}

    /** The user-supplied local cumulative model. The polynomial is not clamped. */
    public static double cumulativeFactor(double m) {
        return cumulativeFactor(BigDecimal.valueOf(m)).doubleValue();
    }

    public static BigDecimal cumulativeFactor(BigDecimal m) {
        requireFinite(m, "累计数量");
        BigDecimal t = m.subtract(BigDecimal.valueOf(64), MATH_CONTEXT);
        return INTERCEPT
            .add(LINEAR.multiply(t, MATH_CONTEXT), MATH_CONTEXT)
            .subtract(QUADRATIC.multiply(t.multiply(t, MATH_CONTEXT), MATH_CONTEXT), MATH_CONTEXT);
    }

    /** True when the polynomial has entered the post-255 rapid-decline range. */
    public static boolean isExtremeUsage(int m) {
        if (m < 0) throw new IllegalArgumentException("累计数量不能为负数");
        return BigDecimal.valueOf(m).compareTo(COMMON_RANGE_MAX) > 0;
    }

    /** Human-readable diagnostic; callers may surface it without altering math. */
    public static String usageWarning(int m) {
        if (m < 0) throw new IllegalArgumentException("累计数量不能为负数");
        if (m > 255) return "材料累计 M 已超过 255，公式进入快速衰减区间";
        if (m >= 196) return "材料累计 M 已进入绝对产出下降区间";
        if (m >= 128) return "材料累计 M 已进入明显衰减区间";
        if (m >= 65) return "材料累计 M 已进入衰减区间";
        return "";
    }

    public static double incremental(double unitAmount, int before, int added) {
        return incremental(BigDecimal.valueOf(unitAmount), before, added).doubleValue();
    }

    public static BigDecimal incremental(BigDecimal unitAmount, int before, int added) {
        requireFinite(unitAmount, "材料理论值");
        if (unitAmount.signum() < 0) throw new IllegalArgumentException("材料理论值不能为负数");
        if (before < 0) throw new IllegalArgumentException("累计数量不能为负数");
        if (added < 0) throw new IllegalArgumentException("新增数量不能为负数");
        if (unitAmount.signum() == 0 || added == 0) return BigDecimal.ZERO;
        return unitAmount.multiply(
            cumulativeFactor(BigDecimal.valueOf(before + (long) added))
                .subtract(cumulativeFactor(BigDecimal.valueOf(before)), MATH_CONTEXT),
            MATH_CONTEXT);
    }

    private static void requireFinite(BigDecimal value, String label) {
        if (value == null) throw new IllegalArgumentException(label + "不能为空");
    }
}
