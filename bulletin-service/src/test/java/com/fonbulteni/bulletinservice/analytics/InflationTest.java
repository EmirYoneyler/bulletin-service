package com.fonbulteni.bulletinservice.analytics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** CPI levels are synthetic; expected values worked out by hand (ADR 004). 6 Nov 2026 is a Friday. */
class InflationTest {

    static final LocalDate FRI = LocalDate.of(2026, 11, 6);

    /** Oct 2026 is 2 % above Sep; 20 % above Dec 2025; 27.5 % above Oct 2025. */
    static Map<YearMonth, BigDecimal> cpi() {
        Map<YearMonth, BigDecimal> m = new TreeMap<>();
        m.put(YearMonth.of(2025, 10), new BigDecimal("160"));
        m.put(YearMonth.of(2025, 12), new BigDecimal("170"));
        m.put(YearMonth.of(2026, 8), new BigDecimal("196"));
        m.put(YearMonth.of(2026, 9), new BigDecimal("200"));
        m.put(YearMonth.of(2026, 10), new BigDecimal("204"));
        return m;
    }

    @Test
    void usesLatestPublishedMonthAndItsLabel() {
        Inflation inflation = Inflation.from(cpi(), FRI);

        assertThat(inflation.cpiMonth()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(inflation.monthlyRate()).isEqualByComparingTo("0.02");          // 204/200 − 1
        assertThat(inflation.note()).isEqualTo("Ekim 2026 TÜFE'si kullanıldı.");
    }

    @Test
    void shortPeriodsAreProratedGeometrically() {
        PeriodValues p = Inflation.from(cpi(), FRI).periodInflation();

        // week: 1.02^(7/30) − 1; month to date (6 days): 1.02^(6/30) − 1
        assertThat(p.week().doubleValue()).isCloseTo(Math.pow(1.02, 7.0 / 30) - 1, within(1e-12));
        assertThat(p.monthToDate().doubleValue()).isCloseTo(Math.pow(1.02, 6.0 / 30) - 1, within(1e-12));
        assertThat(p.week().doubleValue()).isCloseTo(0.00463, within(0.00001));
    }

    @Test
    void yearToDateAndOneYearUseActualIndexRatios() {
        PeriodValues p = Inflation.from(cpi(), FRI).periodInflation();

        assertThat(p.yearToDate()).isEqualByComparingTo("0.2");   // 204/170 − 1
        assertThat(p.oneYear()).isEqualByComparingTo("0.275");    // 204/160 − 1
    }

    @Test
    void beforeThePublicationDayThePreviousMonthIsUsed() {
        // 2 Nov: October CPI is not out yet (published around the 3rd), so September is used.
        Inflation inflation = Inflation.from(cpi(), LocalDate.of(2026, 11, 2));

        assertThat(inflation.cpiMonth()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(inflation.monthlyRate().doubleValue()).isCloseTo(200.0 / 196.0 - 1, within(1e-15));
        assertThat(inflation.note()).isEqualTo("Eylül 2026 TÜFE'si kullanıldı.");
        assertThat(Inflation.from(cpi(), LocalDate.of(2026, 11, 3)).cpiMonth()).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test
    void earlyJanuaryProratesTheYearToDate() {
        Map<YearMonth, BigDecimal> m = new TreeMap<>();
        m.put(YearMonth.of(2026, 11), new BigDecimal("210"));
        m.put(YearMonth.of(2026, 12), new BigDecimal("214.2")); // +2 %
        LocalDate asOf = LocalDate.of(2027, 1, 8);

        Inflation inflation = Inflation.from(m, asOf);

        assertThat(inflation.cpiMonth()).isEqualTo(YearMonth.of(2026, 12));
        // Year to date = 8 days of 2027: 1.02^(8/30) − 1, not the meaningless Dec/Dec ratio
        assertThat(inflation.periodInflation().yearToDate().doubleValue())
                .isCloseTo(Math.pow(1.02, 8.0 / 30) - 1, within(1e-12));
    }

    @Test
    void missingNeighbourMonthsGiveNullsNotZeros() {
        Map<YearMonth, BigDecimal> m = new TreeMap<>(Map.of(YearMonth.of(2026, 10), new BigDecimal("204")));

        Inflation inflation = Inflation.from(m, FRI);

        assertThat(inflation.cpiMonth()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(inflation.monthlyRate()).isNull();
        assertThat(inflation.periodInflation()).isEqualTo(PeriodValues.EMPTY);
    }

    @Test
    void noCpiAtAll() {
        Inflation inflation = Inflation.from(new TreeMap<>(), FRI);

        assertThat(inflation).isEqualTo(Inflation.EMPTY);
        assertThat(inflation.note()).isEqualTo("TÜFE verisi yok.");
    }

    @Test
    void realReturnsPerPeriod() {
        Inflation inflation = Inflation.from(cpi(), FRI);
        PeriodValues nominal = new PeriodValues(new BigDecimal("0.03"), null, new BigDecimal("0.5"), new BigDecimal("0.275"));

        PeriodValues real = inflation.realOf(nominal);

        // week: 1.03 / 1.02^(7/30) − 1
        assertThat(real.week().doubleValue()).isCloseTo(1.03 / Math.pow(1.02, 7.0 / 30) - 1, within(1e-12));
        assertThat(real.monthToDate()).isNull();                         // nominal missing
        assertThat(real.yearToDate()).isEqualByComparingTo("0.25");      // 1.5 / 1.2 − 1
        assertThat(real.oneYear()).isEqualByComparingTo("0");            // 1.275 / 1.275 − 1
        assertThat(Inflation.EMPTY.realOf(nominal)).isEqualTo(PeriodValues.EMPTY);
    }

    @Test
    void turkishMonthNames() {
        assertThat(Inflation.monthLabel(YearMonth.of(2026, 2))).isEqualTo("Şubat 2026");
        assertThat(Inflation.monthLabel(YearMonth.of(2026, 8))).isEqualTo("Ağustos 2026");
        assertThat(Inflation.monthLabel(YearMonth.of(2026, 10))).isEqualTo("Ekim 2026");
    }
}
