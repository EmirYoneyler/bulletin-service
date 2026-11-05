package com.fonbulteni.bulletinservice.analytics;

import java.math.BigDecimal;
import java.util.function.BiFunction;

/** One value per reporting period (fractions); null where it cannot be computed. */
public record PeriodValues(BigDecimal week, BigDecimal monthToDate, BigDecimal yearToDate, BigDecimal oneYear) {

    public static final PeriodValues EMPTY = new PeriodValues(null, null, null, null);

    /** Combines two period sets value by value; a null on either side gives null. */
    public PeriodValues combine(PeriodValues other, BiFunction<BigDecimal, BigDecimal, BigDecimal> f) {
        return new PeriodValues(apply(week, other.week, f), apply(monthToDate, other.monthToDate, f),
                apply(yearToDate, other.yearToDate, f), apply(oneYear, other.oneYear, f));
    }

    private static BigDecimal apply(BigDecimal a, BigDecimal b, BiFunction<BigDecimal, BigDecimal, BigDecimal> f) {
        return a == null || b == null ? null : f.apply(a, b);
    }
}
