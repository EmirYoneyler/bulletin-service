package com.fonbulteni.bulletinservice.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import static com.fonbulteni.bulletinservice.analytics.ReturnMath.MC;

/**
 * Inflation for each reporting period, derived from the monthly CPI index (ADR 004).
 *
 * @param cpiMonth         the CPI month used; null when there is no CPI data
 * @param monthlyRate      change of {@code cpiMonth} over the month before it
 * @param periodInflation  inflation per period (week and month to date prorated, year to date and one
 *                         year from index ratios)
 */
public record Inflation(YearMonth cpiMonth, BigDecimal monthlyRate, PeriodValues periodInflation) {

    public static final Inflation EMPTY = new Inflation(null, null, PeriodValues.EMPTY);

    /** TÜİK publishes month m's CPI around the 3rd of month m + 1. */
    static final int CPI_PUBLICATION_DAY = 3;

    private static final DateTimeFormatter MONTH_TR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("tr-TR"));

    /** Turkish label for the bulletin, e.g. "Ekim 2026 TÜFE'si kullanıldı." */
    public String note() {
        return cpiMonth == null ? "TÜFE verisi yok." : monthLabel(cpiMonth) + " TÜFE'si kullanıldı.";
    }

    public static String monthLabel(YearMonth month) {
        return MONTH_TR.format(month.atDay(1));
    }

    /** Real returns {@code (1 + r) / (1 + π) − 1} per period; null where either side is missing. */
    public PeriodValues realOf(PeriodValues nominal) {
        return nominal.combine(periodInflation, BenchmarkMath::real);
    }

    /** Latest CPI month already published on {@code asOf}. */
    static YearMonth latestPublishedMonth(LocalDate asOf) {
        return YearMonth.from(asOf).minusMonths(asOf.getDayOfMonth() >= CPI_PUBLICATION_DAY ? 1 : 2);
    }

    /**
     * @param cpi monthly CPI index levels keyed by month (any base; only ratios are used)
     */
    public static Inflation from(Map<YearMonth, BigDecimal> cpi, LocalDate asOf) {
        YearMonth cutoff = latestPublishedMonth(asOf);
        YearMonth month = null;
        for (YearMonth m : cpi.keySet()) {
            if (!m.isAfter(cutoff) && (month == null || m.isAfter(month))) {
                month = m;
            }
        }
        if (month == null) {
            return EMPTY;
        }
        BigDecimal level = cpi.get(month);
        BigDecimal monthly = ratio(level, cpi.get(month.minusMonths(1)));

        BigDecimal week = monthly == null ? null
                : BenchmarkMath.prorateMonthly(monthly, BenchmarkMath.days(Periods.week(asOf), asOf));
        BigDecimal monthToDate = monthly == null ? null
                : BenchmarkMath.prorateMonthly(monthly, BenchmarkMath.days(Periods.monthToDate(asOf), asOf));
        BigDecimal yearToDate;
        if (month.getYear() == asOf.getYear()) {
            yearToDate = ratio(level, cpi.get(YearMonth.of(asOf.getYear() - 1, 12)));
        } else {
            // Early January: no CPI month of this year is published yet, so prorate like a short period.
            yearToDate = monthly == null ? null
                    : BenchmarkMath.prorateMonthly(monthly, BenchmarkMath.days(Periods.yearToDate(asOf), asOf));
        }
        BigDecimal oneYear = ratio(level, cpi.get(month.minusMonths(12)));
        return new Inflation(month, monthly, new PeriodValues(week, monthToDate, yearToDate, oneYear));
    }

    private static BigDecimal ratio(BigDecimal value, BigDecimal base) {
        return value == null || base == null ? null : value.divide(base, MC).subtract(BigDecimal.ONE, MC);
    }
}
