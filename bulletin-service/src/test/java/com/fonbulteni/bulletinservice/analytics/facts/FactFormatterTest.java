package com.fonbulteni.bulletinservice.analytics.facts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FactFormatterTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "0.032      | %3,2",       // card example
            "-0.009     | %-0,9",      // card example: sign after %
            "0          | %0,0",
            "0.0325     | %3,3",       // 3.25 → HALF_UP
            "0.03249    | %3,2",
            "-0.0325    | %-3,3",      // HALF_UP rounds away from zero
            "-0.0004    | %0,0",       // rounds to zero: no '-0,0'
            "1.575      | %157,5",
            "15.75      | %1.575,0"    // thousands separator
    })
    void percent(String value, String expected) {
        assertThat(FactFormatter.format(new BigDecimal(value), Fact.Unit.PERCENT)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "1250.5      | 1.250,50 TL",   // card example
            "1250.505    | 1.250,51 TL",
            "0.004       | 0,00 TL",
            "-12         | -12,00 TL",
            "1234567.891 | 1.234.567,89 TL"
    })
    void lira(String value, String expected) {
        assertThat(FactFormatter.format(new BigDecimal(value), Fact.Unit.TRY)).isEqualTo(expected);
    }

    @Test
    void index() {
        assertThat(FactFormatter.format(new BigDecimal("9876543.215"), Fact.Unit.INDEX)).isEqualTo("9.876.543,22");
    }

    @Test
    void missingIsVeriYokForEveryUnit() {
        for (Fact.Unit unit : Fact.Unit.values()) {
            assertThat(FactFormatter.format(null, unit)).isEqualTo("veri yok");
        }
    }

    @Test
    void doesNotDependOnTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            assertThat(FactFormatter.format(new BigDecimal("1250.5"), Fact.Unit.TRY)).isEqualTo("1.250,50 TL");
            assertThat(FactFormatter.format(new BigDecimal("0.032"), Fact.Unit.PERCENT)).isEqualTo("%3,2");
        } finally {
            Locale.setDefault(previous);
        }
    }

    // --- Fact ---

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "0.0004, FLAT",   // shown as %0,0
            "0.0005, UP",     // shown as %0,1
            "-0.0005, DOWN",  // shown as %-0,1
            "0.032, UP",
            "0, FLAT"
    })
    void directionFollowsTheDisplayedValue(String value, Fact.Direction expected) {
        Fact fact = Fact.of("fund.AAV.return.1w", new BigDecimal(value), Fact.Unit.PERCENT,
                LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 6), Fact.Source.TEFAS, "return.simple");

        assertThat(fact.direction()).isEqualTo(expected);
    }

    @Test
    void missingFactHasNoDirection() {
        Fact fact = Fact.of("fund.AAV.return.ytd", null, Fact.Unit.PERCENT, null, LocalDate.of(2026, 11, 6),
                Fact.Source.TEFAS, "return.simple");

        assertThat(fact.missing()).isTrue();
        assertThat(fact.direction()).isNull();
        assertThat(FactFormatter.format(fact)).isEqualTo("veri yok");
    }

    @Test
    void rejectsMalformedKeys() {
        assertThatThrownBy(() -> Fact.of("fund AAV", BigDecimal.ONE, Fact.Unit.PERCENT, null, null,
                Fact.Source.TEFAS, "m")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Fact.of("fund.{{x}}", BigDecimal.ONE, Fact.Unit.PERCENT, null, null,
                Fact.Source.TEFAS, "m")).isInstanceOf(IllegalArgumentException.class);
    }
}
