package com.fonbulteni.bulletinservice.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static com.fonbulteni.bulletinservice.analytics.ReturnMath.MC;

/**
 * Rate conversions for benchmarks and real returns (AGENT_PROMPT §7.3, ADR 004). Fractional powers
 * use {@code double}; the result is converted through its shortest decimal form, so exact cases such
 * as {@code 1.45^1} stay exact.
 */
public final class BenchmarkMath {

    static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365);
    static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);

    private BenchmarkMath() {
    }

    /** Calendar days in {@code [periodStart, asOf]}: 7 for a week, the day of month for month to date. */
    public static long days(LocalDate periodStart, LocalDate asOf) {
        return ChronoUnit.DAYS.between(periodStart, asOf) + 1;
    }

    /** Deposit: an annual rate {@code i} held for {@code days}: {@code (1 + i)^(days/365) − 1}. */
    public static BigDecimal compoundAnnual(BigDecimal annualRate, long days) {
        return power(BigDecimal.ONE.add(annualRate, MC), BigDecimal.valueOf(days).divide(DAYS_PER_YEAR, MC));
    }

    /** Inflation: a monthly rate spread geometrically over {@code days}: {@code (1 + π_month)^(days/30) − 1}. */
    public static BigDecimal prorateMonthly(BigDecimal monthlyRate, long days) {
        return power(BigDecimal.ONE.add(monthlyRate, MC), BigDecimal.valueOf(days).divide(DAYS_PER_MONTH, MC));
    }

    /** Real return: {@code (1 + r) / (1 + π) − 1}. */
    public static BigDecimal real(BigDecimal nominal, BigDecimal inflation) {
        return BigDecimal.ONE.add(nominal, MC).divide(BigDecimal.ONE.add(inflation, MC), MC).subtract(BigDecimal.ONE, MC);
    }

    /** {@code base^exponent − 1}. */
    private static BigDecimal power(BigDecimal base, BigDecimal exponent) {
        if (exponent.compareTo(BigDecimal.ONE) == 0) {
            return base.subtract(BigDecimal.ONE, MC);
        }
        double value = Math.pow(base.doubleValue(), exponent.doubleValue());
        return BigDecimal.valueOf(value).round(MC).subtract(BigDecimal.ONE, MC);
    }
}
