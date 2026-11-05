package com.fonbulteni.bulletinservice.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Comparison values for one {@code asOf} date. All returns are fractions; empty or null parts mean the
 * EVDS data is missing (e.g. no EVDS key, see E1-5b).
 *
 * @param usdTry           USD/TRY change per period
 * @param bist100          BIST 100 total-return index change per period (ADR 003)
 * @param deposit          the latest TL deposit rate compounded over each period
 * @param depositRate      that annual rate as a fraction ({@code 0.45} = %45)
 * @param depositRateDate  observation date of the rate
 * @param gold             gold is monthly in EVDS: month-over-month and year-to-date change
 * @param inflation        CPI-based inflation per period and the CPI month used (ADR 004)
 */
public record Benchmarks(
        LocalDate asOf,
        PeriodValues usdTry,
        PeriodValues bist100,
        PeriodValues deposit,
        BigDecimal depositRate,
        LocalDate depositRateDate,
        MonthlyChange gold,
        Inflation inflation) {
}
