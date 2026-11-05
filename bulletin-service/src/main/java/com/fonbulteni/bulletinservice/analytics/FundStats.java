package com.fonbulteni.bulletinservice.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Statistics of one fund as of a date. Returns, volatility and drawdown are fractions
 * ({@code 0.032} = %3,2); any field is {@code null} when the data to compute it is missing.
 *
 * @param lastPriceDate date of the last close on or before {@code asOf}
 * @param maxDrawdown1y non-positive; {@code -0.25} means a 25 % fall from a peak within the last year
 */
public record FundStats(
        Long fundId,
        String tefasCode,
        LocalDate asOf,
        LocalDate lastPriceDate,
        BigDecimal lastPrice,
        BigDecimal return1w,
        BigDecimal returnMtd,
        BigDecimal returnYtd,
        BigDecimal return1y,
        BigDecimal volatility63d,
        BigDecimal maxDrawdown1y) {

    /** The four period returns, e.g. for {@link Inflation#realOf(PeriodValues)}. */
    public PeriodValues returns() {
        return new PeriodValues(return1w, returnMtd, returnYtd, return1y);
    }
}
