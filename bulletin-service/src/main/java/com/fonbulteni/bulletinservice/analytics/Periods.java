package com.fonbulteni.bulletinservice.analytics;

import java.time.LocalDate;

/**
 * First calendar day of each reporting period ending on {@code asOf} (AGENT_PROMPT §7.3). The start
 * price of a period is the last close strictly before this day.
 */
public final class Periods {

    private Periods() {
    }

    /** One week: previous Friday → this Friday. For a Friday {@code asOf} the start price is last Friday's. */
    public static LocalDate week(LocalDate asOf) {
        return asOf.minusDays(6);
    }

    /** Month to date: the start price is the last close of the previous month. */
    public static LocalDate monthToDate(LocalDate asOf) {
        return asOf.withDayOfMonth(1);
    }

    /** Year to date: the start price is the last close of the previous year. */
    public static LocalDate yearToDate(LocalDate asOf) {
        return asOf.withDayOfYear(1);
    }

    /** One year: the start price is the last close on or before the same date a year earlier. */
    public static LocalDate oneYear(LocalDate asOf) {
        return asOf.minusYears(1).plusDays(1);
    }
}
