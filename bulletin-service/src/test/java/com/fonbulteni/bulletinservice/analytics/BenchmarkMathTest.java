package com.fonbulteni.bulletinservice.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Expected values worked out by hand; 6 Nov 2026 is a Friday. */
class BenchmarkMathTest {

    static final LocalDate FRI = LocalDate.of(2026, 11, 6);

    @Test
    void periodLengthsInDays() {
        assertThat(BenchmarkMath.days(Periods.week(FRI), FRI)).isEqualTo(7);
        assertThat(BenchmarkMath.days(Periods.monthToDate(FRI), FRI)).isEqualTo(6);
        // 31+28+31+30+31+30+31+31+30+31 = 304 days to the end of October, + 6
        assertThat(BenchmarkMath.days(Periods.yearToDate(FRI), FRI)).isEqualTo(310);
        assertThat(BenchmarkMath.days(Periods.oneYear(FRI), FRI)).isEqualTo(365);
    }

    @Test
    void annualRateOverAFullYearIsTheRateItself() {
        // 1.45^(365/365) − 1 = 0.45
        assertThat(BenchmarkMath.compoundAnnual(new BigDecimal("0.45"), 365)).isEqualByComparingTo("0.45");
    }

    @Test
    void annualRateOverOneWeekCompoundsGeometrically() {
        // 1.44^(7/365) − 1 = e^(7/365 · ln 1.44) − 1 ≈ 0.0070
        double expected = Math.exp(7.0 / 365.0 * Math.log(1.44)) - 1;
        assertThat(BenchmarkMath.compoundAnnual(new BigDecimal("0.44"), 7).doubleValue()).isCloseTo(expected, within(1e-12));
        assertThat(expected).isCloseTo(0.00702, within(0.00001));
    }

    @Test
    void monthlyRateProration() {
        // 30 days = exactly one month
        assertThat(BenchmarkMath.prorateMonthly(new BigDecimal("0.02"), 30)).isEqualByComparingTo("0.02");
        // 15 days = half a month: 1.44^0.5 − 1 = 1.2 − 1 = 0.2
        assertThat(BenchmarkMath.prorateMonthly(new BigDecimal("0.44"), 15).doubleValue()).isCloseTo(0.2, within(1e-12));
        // 60 days = two months: 1.21^2 − 1 = 0.4641
        assertThat(BenchmarkMath.prorateMonthly(new BigDecimal("0.21"), 60).doubleValue()).isCloseTo(0.4641, within(1e-12));
    }

    @Test
    void realReturn() {
        // 1.5 / 1.25 − 1 = 0.2
        assertThat(BenchmarkMath.real(new BigDecimal("0.5"), new BigDecimal("0.25"))).isEqualByComparingTo("0.2");
        // same nominal and inflation → no real gain
        assertThat(BenchmarkMath.real(new BigDecimal("0.25"), new BigDecimal("0.25"))).isEqualByComparingTo("0");
        // 1.1 / 1.2 − 1 = −1/12 ≈ −0.083333: positive nominal, negative real
        assertThat(BenchmarkMath.real(new BigDecimal("0.1"), new BigDecimal("0.2")).doubleValue())
                .isCloseTo(-1.0 / 12.0, within(1e-15));
    }

    // --- BenchmarkService pure parts ---

    @Test
    void depositRateCompoundedOverEachPeriod() {
        PeriodValues deposit = BenchmarkService.depositReturns(new BigDecimal("0.45"), FRI);

        assertThat(deposit.oneYear()).isEqualByComparingTo("0.45");
        assertThat(deposit.week().doubleValue()).isCloseTo(Math.pow(1.45, 7.0 / 365) - 1, within(1e-12));
        assertThat(deposit.yearToDate().doubleValue()).isCloseTo(Math.pow(1.45, 310.0 / 365) - 1, within(1e-12));
        assertThat(BenchmarkService.depositReturns(null, FRI)).isEqualTo(PeriodValues.EMPTY);
    }

    @Test
    void goldUsesTheLatestCompletedMonth() {
        Map<YearMonth, BigDecimal> gold = new TreeMap<>(Map.of(
                YearMonth.of(2025, 12), new BigDecimal("800"),
                YearMonth.of(2026, 9), new BigDecimal("1000"),
                YearMonth.of(2026, 10), new BigDecimal("1100"),
                YearMonth.of(2026, 11), new BigDecimal("1500"))); // running month, not final: ignored

        MonthlyChange change = BenchmarkService.monthlyChange(gold, FRI);

        assertThat(change.month()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(change.monthOverMonth()).isEqualByComparingTo("0.1");   // 1100/1000 − 1
        assertThat(change.yearToDate()).isEqualByComparingTo("0.375");     // 1100/800 − 1
    }

    @Test
    void goldInJanuaryHasNoYearToDate() {
        Map<YearMonth, BigDecimal> gold = new TreeMap<>(Map.of(
                YearMonth.of(2026, 11), new BigDecimal("1000"),
                YearMonth.of(2026, 12), new BigDecimal("1050")));

        MonthlyChange change = BenchmarkService.monthlyChange(gold, LocalDate.of(2027, 1, 8));

        assertThat(change.month()).isEqualTo(YearMonth.of(2026, 12));
        assertThat(change.monthOverMonth()).isEqualByComparingTo("0.05");  // 1050/1000 − 1
        assertThat(change.yearToDate()).isNull();                          // no completed month of 2027 yet
    }

    @Test
    void goldWithGapsGivesNullsNotZeros() {
        MonthlyChange change = BenchmarkService.monthlyChange(
                new TreeMap<>(Map.of(YearMonth.of(2026, 10), new BigDecimal("1100"))), FRI);

        assertThat(change.monthOverMonth()).isNull();
        assertThat(change.yearToDate()).isNull();
        assertThat(BenchmarkService.monthlyChange(new TreeMap<>(), FRI)).isEqualTo(MonthlyChange.EMPTY);
    }
}
