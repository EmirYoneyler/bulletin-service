package com.fonbulteni.bulletinservice.analytics.facts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * One number that may appear in a bulletin, with where it came from (AGENT_PROMPT §7.4). The writer
 * never sees {@code value}; it only refers to {@code key} through a placeholder and may use
 * {@code direction}.
 *
 * @param value     null means "veri yok", never zero
 * @param direction derived from the value <em>as displayed</em>: anything shown as {@code %0,0} is FLAT
 */
public record Fact(
        String key,
        BigDecimal value,
        Unit unit,
        LocalDate periodStart,
        LocalDate periodEnd,
        Source source,
        String method,
        Direction direction) {

    public enum Unit { PERCENT, TRY, INDEX }

    public enum Source { TEFAS, EVDS, KAP, COMPUTED }

    public enum Direction { UP, DOWN, FLAT }

    private static final Pattern KEY = Pattern.compile("[a-z]+(\\.[A-Za-z0-9]+)+");

    public Fact {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid fact key: " + key);
        }
        if (unit == null || source == null || method == null) {
            throw new IllegalArgumentException("unit, source and method are required: " + key);
        }
    }

    public static Fact of(String key, BigDecimal value, Unit unit, LocalDate periodStart, LocalDate periodEnd,
                          Source source, String method) {
        return new Fact(key, value, unit, periodStart, periodEnd, source, method, directionOf(value, unit));
    }

    public boolean missing() {
        return value == null;
    }

    static Direction directionOf(BigDecimal value, Unit unit) {
        if (value == null) {
            return null;
        }
        int sign = FactFormatter.displayed(value, unit).signum();
        return sign > 0 ? Direction.UP : sign < 0 ? Direction.DOWN : Direction.FLAT;
    }
}
