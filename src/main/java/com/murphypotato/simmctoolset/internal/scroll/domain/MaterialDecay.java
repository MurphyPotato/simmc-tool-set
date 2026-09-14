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

    private MaterialDecay() {}

    /** The server's cumulative curve. The polynomial is intentionally not clamped. */
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
