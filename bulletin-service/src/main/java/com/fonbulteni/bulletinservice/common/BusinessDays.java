package com.fonbulteni.bulletinservice.common;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Weekday arithmetic. Public holidays are not known here; they simply have no price rows. */
public final class BusinessDays {

    private BusinessDays() {
    }

    public static boolean isWeekday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /**
     * First day of the window ending on {@code end} that contains exactly {@code count} weekdays.
     * Wednesday 4 Nov 2026 with count 5 gives Thursday 29 Oct (Thu, Fri, Mon, Tue, Wed).
     */
    public static LocalDate startOfLastWeekdays(LocalDate end, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1");
        }
        LocalDate day = end;
        int seen = 0;
        while (true) {
            if (isWeekday(day) && ++seen == count) {
                return day;
            }
            day = day.minusDays(1);
        }
    }
}
