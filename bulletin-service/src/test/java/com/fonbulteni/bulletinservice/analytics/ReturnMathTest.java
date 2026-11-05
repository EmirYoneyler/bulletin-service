package com.fonbulteni.bulletinservice.analytics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Expected values are worked out by hand (comments show the arithmetic). Calendar: 6 Nov 2026 is a
 * Friday; 29 Oct 2026 (Cumhuriyet Bayramı) has no price.
 */
class ReturnMathTest {

    static final LocalDate FRI = LocalDate.of(2026, 11, 6);

    static PricePoint p(String date, String price) {
        return new PricePoint(LocalDate.parse(date), new BigDecimal(price));
    }

    static List<PricePoint> series(PricePoint... points) {
        return ReturnMath.normalize(List.of(points));
    }

    @Test
    void calendarAssumptionsHold() {
        assertThat(FRI.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(LocalDate.of(2026, 10, 30).getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
    }

    @Nested
    class PeriodReturns {

        @Test
        void weekIsPreviousFridayToThisFriday() {
            // 103 / 100 − 1 = 0.03
            List<PricePoint> s = series(p("2026-10-30", "100"), p("2026-11-02", "101"), p("2026-11-03", "102"),
                    p("2026-11-04", "101.5"), p("2026-11-05", "102.5"), p("2026-11-06", "103"));

            assertThat(ReturnMath.periodReturn(s, Periods.week(FRI), FRI)).isEqualByComparingTo("0.03");
        }

        @Test
        void holidayOnPeriodStartUsesLastTradingDayBefore() {
            // Previous Friday has no price; Wednesday 28 Oct (29 Oct is a holiday) is the start: 210 / 200 − 1 = 0.05
            List<PricePoint> s = series(p("2026-10-28", "200"), p("2026-11-02", "205"), p("2026-11-06", "210"));

            assertThat(ReturnMath.periodReturn(s, Periods.week(FRI), FRI)).isEqualByComparingTo("0.05");
        }

        @Test
        void weekendAsOfUsesFridaysClose() {
            // asOf Saturday 7 Nov: start = last close before Sun 1 Nov = Fri 30 Oct (100), end = Fri 6 Nov (103)
            LocalDate saturday = FRI.plusDays(1);
            List<PricePoint> s = series(p("2026-10-30", "100"), p("2026-11-06", "103"));

            assertThat(ReturnMath.periodReturn(s, Periods.week(saturday), saturday)).isEqualByComparingTo("0.03");
        }

        @Test
        void negativeReturnKeepsItsSign() {
            // 99.1 / 100 − 1 = −0.009 (shown later as %-0,9)
            List<PricePoint> s = series(p("2026-10-30", "100"), p("2026-11-06", "99.1"));

            assertThat(ReturnMath.periodReturn(s, Periods.week(FRI), FRI)).isEqualByComparingTo("-0.009");
        }

        @Test
        void monthToDateStartsFromLastCloseOfPreviousMonth() {
            // Month starts on Sunday 1 Nov; last October close is Fri 30 Oct (50): 55 / 50 − 1 = 0.1
            List<PricePoint> s = series(p("2026-10-30", "50"), p("2026-11-02", "52"), p("2026-11-06", "55"));

            assertThat(ReturnMath.periodReturn(s, Periods.monthToDate(FRI), FRI)).isEqualByComparingTo("0.1");
        }

        @Test
        void yearToDateStartsFromLastCloseOfPreviousYear() {
            // 31 Dec 2025 = 80: 100 / 80 − 1 = 0.25
            List<PricePoint> s = series(p("2025-12-30", "79"), p("2025-12-31", "80"), p("2026-01-02", "81"),
                    p("2026-11-06", "100"));

            assertThat(ReturnMath.periodReturn(s, Periods.yearToDate(FRI), FRI)).isEqualByComparingTo("0.25");
        }

        @Test
        void yearToDateWithoutDecember31UsesDecember30() {
            // 125 / 100 − 1 = 0.25
            List<PricePoint> s = series(p("2025-12-30", "100"), p("2026-11-06", "125"));

            assertThat(ReturnMath.periodReturn(s, Periods.yearToDate(FRI), FRI)).isEqualByComparingTo("0.25");
        }

        @Test
        void oneYearStartsFromSameDateLastYear() {
            // Thu 6 Nov 2025 = 40, the close of 7 Nov 2025 is inside the period: 50 / 40 − 1 = 0.25
            List<PricePoint> s = series(p("2025-11-05", "39"), p("2025-11-06", "40"), p("2025-11-07", "41"),
                    p("2026-11-06", "50"));

            assertThat(Periods.oneYear(FRI)).isEqualTo(LocalDate.of(2025, 11, 7));
            assertThat(ReturnMath.periodReturn(s, Periods.oneYear(FRI), FRI)).isEqualByComparingTo("0.25");
        }

        @Test
        void decimalPricesStayExact() {
            // 1.40 / 1.25 − 1 = 0.12 exactly, no binary floating point drift
            List<PricePoint> s = series(p("2026-10-30", "1.250000"), p("2026-11-06", "1.400000"));

            assertThat(ReturnMath.periodReturn(s, Periods.week(FRI), FRI)).isEqualTo(new BigDecimal("0.12"));
        }
    }

    @Nested
    class MissingData {

        @Test
        void fundLaunchedThisYearHasNoYearToDateButHasMonthToDate() {
            List<PricePoint> s = series(p("2026-03-02", "10"), p("2026-10-30", "12"), p("2026-11-06", "13.2"));

            assertThat(ReturnMath.periodReturn(s, Periods.yearToDate(FRI), FRI)).isNull();
            assertThat(ReturnMath.periodReturn(s, Periods.oneYear(FRI), FRI)).isNull();
            // 13.2 / 12 − 1 = 0.1
            assertThat(ReturnMath.periodReturn(s, Periods.monthToDate(FRI), FRI)).isEqualByComparingTo("0.1");
        }

        @Test
        void noCloseInsideThePeriodIsNullNotZero() {
            // Last close is Fri 30 Oct; nothing after the week began on Sat 31 Oct.
            List<PricePoint> s = series(p("2026-10-23", "100"), p("2026-10-30", "100"));

            assertThat(ReturnMath.periodReturn(s, Periods.week(FRI), FRI)).isNull();
        }

        @Test
        void startPriceOlderThanFourteenDaysIsMissing() {
            LocalDate monthStart = Periods.monthToDate(FRI); // 1 Nov
            // 18 Oct is exactly 14 days before 1 Nov → still accepted: 110 / 100 − 1 = 0.1
            assertThat(ReturnMath.periodReturn(series(p("2026-10-18", "100"), p("2026-11-06", "110")), monthStart, FRI))
                    .isEqualByComparingTo("0.1");
            // 17 Oct is 15 days before → a gap in the data, not a valid start price
            assertThat(ReturnMath.periodReturn(series(p("2026-10-17", "100"), p("2026-11-06", "110")), monthStart, FRI))
                    .isNull();
        }

        @Test
        void emptySeriesGivesNullEverywhere() {
            List<PricePoint> empty = List.of();

            assertThat(ReturnMath.periodReturn(empty, Periods.week(FRI), FRI)).isNull();
            assertThat(ReturnMath.annualizedVolatility(empty, FRI)).isNull();
            assertThat(ReturnMath.maxDrawdown(empty, Periods.oneYear(FRI), FRI)).isNull();
        }
    }

    @Nested
    class Volatility {

        private List<PricePoint> alternating(int count, LocalDate first) {
            List<PricePoint> out = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                out.add(new PricePoint(first.plusDays(i), new BigDecimal(i % 2 == 0 ? "100" : "101")));
            }
            return out;
        }

        @Test
        void alternatingSeriesMatchesHandDerivedClosedForm() {
            // 64 closes 100,101,100,… → 63 log returns: 32 × (+a), 31 × (−a), a = ln(1.01)
            // mean = a/63; Σ(x−mean)² = 63a² − a²/63 = a²·3968/63; sample variance = a²·3968/(63·62)
            // σ_daily = a·√(3968/3906); annual = σ_daily·√252 ≈ 0.009950331 · 1.0079055 · 15.874508 ≈ 0.15921
            List<PricePoint> s = alternating(64, LocalDate.of(2026, 8, 1));
            double a = Math.log(1.01);
            double expected = a * Math.sqrt(3968.0 / 3906.0) * Math.sqrt(252.0);

            BigDecimal vol = ReturnMath.annualizedVolatility(s, s.get(63).date());

            assertThat(vol.doubleValue()).isCloseTo(expected, within(1e-12));
            assertThat(vol.doubleValue()).isCloseTo(0.15921, within(0.00001));
        }

        @Test
        void steadyGrowthHasNoVolatility() {
            // Every day ×1.01: all log returns equal, standard deviation 0
            List<PricePoint> s = new ArrayList<>();
            BigDecimal price = new BigDecimal("100");
            for (int i = 0; i < 64; i++) {
                s.add(new PricePoint(LocalDate.of(2026, 8, 1).plusDays(i), price));
                price = price.multiply(new BigDecimal("1.01"));
            }

            assertThat(ReturnMath.annualizedVolatility(s, s.get(63).date()).doubleValue()).isCloseTo(0.0, within(1e-12));
        }

        @Test
        void usesOnlyTheLast64ClosesUpToAsOf() {
            List<PricePoint> s = new ArrayList<>();
            s.add(p("2026-07-01", "5"));    // older wild prices outside the window
            s.add(p("2026-07-02", "500"));
            s.addAll(alternating(64, LocalDate.of(2026, 8, 1)));
            s.add(new PricePoint(LocalDate.of(2026, 8, 1).plusDays(64), new BigDecimal("300"))); // after asOf
            double expected = Math.log(1.01) * Math.sqrt(3968.0 / 3906.0) * Math.sqrt(252.0);

            BigDecimal vol = ReturnMath.annualizedVolatility(ReturnMath.normalize(s), LocalDate.of(2026, 8, 1).plusDays(63));

            assertThat(vol.doubleValue()).isCloseTo(expected, within(1e-12));
        }

        @Test
        void needsSixtyFourCloses() {
            List<PricePoint> s = alternating(63, LocalDate.of(2026, 8, 1));

            assertThat(ReturnMath.annualizedVolatility(s, s.get(62).date())).isNull();
        }
    }

    @Nested
    class MaxDrawdown {

        @Test
        void largestFallFromARunningPeak() {
            // Peaks 120 → 90: 90/120 − 1 = −0.25; later 130 → 104: −0.20. Worst is −0.25
            List<PricePoint> s = series(p("2026-01-02", "100"), p("2026-02-02", "120"), p("2026-03-02", "90"),
                    p("2026-04-01", "110"), p("2026-05-04", "130"), p("2026-06-01", "104"));

            assertThat(ReturnMath.maxDrawdown(s, Periods.oneYear(FRI), FRI)).isEqualByComparingTo("-0.25");
        }

        @Test
        void neverFallingIsZeroNotNull() {
            List<PricePoint> s = series(p("2026-10-30", "100"), p("2026-11-02", "100"), p("2026-11-06", "101"));

            assertThat(ReturnMath.maxDrawdown(s, Periods.oneYear(FRI), FRI)).isEqualByComparingTo("0");
        }

        @Test
        void onlyClosesInsideTheWindowCount() {
            // The crash in 2025-06 is before the window; inside it 80 → 76: −0.05
            List<PricePoint> s = series(p("2025-05-02", "200"), p("2025-06-02", "50"), p("2026-01-02", "80"),
                    p("2026-02-02", "76"), p("2026-03-02", "90"));

            assertThat(ReturnMath.maxDrawdown(s, Periods.oneYear(FRI), FRI)).isEqualByComparingTo("-0.05");
        }

        @Test
        void singleCloseIsNull() {
            assertThat(ReturnMath.maxDrawdown(series(p("2026-11-06", "100")), Periods.oneYear(FRI), FRI)).isNull();
        }
    }

    @Test
    void normalizeSortsAndKeepsOnePricePerDay() {
        List<PricePoint> s = ReturnMath.normalize(List.of(p("2026-11-06", "103"), p("2026-10-30", "100"),
                p("2026-11-06", "104")));

        assertThat(s).extracting(PricePoint::date).containsExactly(LocalDate.of(2026, 10, 30), FRI);
        assertThat(s.get(1).price()).isEqualByComparingTo("104");
    }

    @Test
    void pricesMustBePositive() {
        assertThatThrownBy(() -> p("2026-11-06", "0")).isInstanceOf(IllegalArgumentException.class);
    }
}
