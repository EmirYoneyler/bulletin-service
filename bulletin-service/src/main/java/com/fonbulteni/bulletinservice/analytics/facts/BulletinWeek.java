package com.fonbulteni.bulletinservice.analytics.facts;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** A bulletin week: Monday to Friday. All facts of a bulletin are computed as of its Friday. */
public record BulletinWeek(LocalDate monday, LocalDate friday) {

    /** The week (Monday–Sunday) containing {@code day}; a weekend day belongs to the week that just ended. */
    public static BulletinWeek containing(LocalDate day) {
        LocalDate friday = day.with(DayOfWeek.FRIDAY);
        return new BulletinWeek(friday.minusDays(4), friday);
    }

    /** The latest week whose Friday is on or before {@code today}; the Monday bulletin covers this week. */
    public static BulletinWeek latestCompleted(LocalDate today) {
        LocalDate friday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        return new BulletinWeek(friday.minusDays(4), friday);
    }
}
