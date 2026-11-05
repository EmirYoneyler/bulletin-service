package com.fonbulteni.bulletinservice.ingest.evds;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One value of an EVDS series. For monthly series {@code date} is the first day of the month.
 */
public record EvdsObservation(String seriesCode, LocalDate date, BigDecimal value) {
}
