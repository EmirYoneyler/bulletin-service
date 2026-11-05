package com.fonbulteni.bulletinservice.analytics.facts;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Turkish number formatting for bulletins (AGENT_PROMPT §7.4). The single source for how a fact looks,
 * used by the placeholder renderer and the PDF alike.
 *
 * <ul>
 *   <li>PERCENT: one decimal, {@code %} in front, sign after it: {@code %3,2}, {@code %-0,9}</li>
 *   <li>TRY: two decimals, thousands with dots: {@code 1.250,50 TL}</li>
 *   <li>INDEX: two decimals: {@code 1.234,56}</li>
 *   <li>missing: {@code veri yok}</li>
 * </ul>
 * Rounding is HALF_UP. A value that rounds to zero is shown without a minus sign.
 */
public final class FactFormatter {

    public static final String MISSING = "veri yok";

    private static final DecimalFormatSymbols TR = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("tr-TR"));
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private FactFormatter() {
    }

    public static String format(Fact fact) {
        return format(fact.value(), fact.unit());
    }

    public static String format(BigDecimal value, Fact.Unit unit) {
        if (value == null) {
            return MISSING;
        }
        BigDecimal shown = displayed(value, unit);
        return switch (unit) {
            case PERCENT -> "%" + number(shown, "#,##0.0");
            case TRY -> number(shown, "#,##0.00") + " TL";
            case INDEX -> number(shown, "#,##0.00");
        };
    }

    /** The value exactly as it will be shown (percent already multiplied by 100), rounded HALF_UP. */
    static BigDecimal displayed(BigDecimal value, Fact.Unit unit) {
        BigDecimal rounded = unit == Fact.Unit.PERCENT
                ? value.multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
        return rounded.signum() == 0 ? rounded.abs() : rounded; // never "-0,0"
    }

    private static String number(BigDecimal value, String pattern) {
        DecimalFormat format = new DecimalFormat(pattern, TR); // DecimalFormat is not thread-safe
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }
}
