package com.fonbulteni.bulletinservice.analytics;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Change of a monthly series (gold in EVDS is monthly, ADR 002): latest completed {@code month}
 * against the previous month and against December of the previous year. Fields are null when the
 * needed months are missing.
 */
public record MonthlyChange(YearMonth month, BigDecimal monthOverMonth, BigDecimal yearToDate) {

    public static final MonthlyChange EMPTY = new MonthlyChange(null, null, null);
}
