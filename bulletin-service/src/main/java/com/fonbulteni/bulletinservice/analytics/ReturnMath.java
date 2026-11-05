package com.fonbulteni.bulletinservice.analytics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

/**
 * Deterministic return statistics (AGENT_PROMPT §7.3, ADR 011). Results are fractions
 * ({@code 0.032} = %3,2) in {@link MathContext#DECIMAL64}; rounding for display happens later.
 * Missing data gives {@code null}, never zero.
 */
public final class ReturnMath {

    public static final MathContext MC = MathContext.DECIMAL64;

    /**
     * A start price older than this (calendar days before the period start) counts as missing data.
     * The longest Turkish market closure (a bridged religious holiday) is 9 days.
     */
    public static final int MAX_START_GAP_DAYS = 14;

    public static final int VOLATILITY_WINDOW = 63;
    static final BigDecimal TRADING_DAYS_PER_YEAR = BigDecimal.valueOf(252);

    private ReturnMath() {
    }

    /** Sorted by date, one price per day (a later duplicate wins). */
    public static List<PricePoint> normalize(Collection<PricePoint> prices) {
        TreeMap<LocalDate, PricePoint> byDate = new TreeMap<>();
        for (PricePoint price : prices) {
            byDate.put(price.date(), price);
        }
        return List.copyOf(byDate.values());
    }

    /**
     * {@code r = P_end / P_start − 1}. {@code P_start} is the last close strictly before {@code periodStart};
     * {@code P_end} the last close on or before {@code asOf}, which must fall inside the period.
     *
     * @return null if either price is missing, the end price lies before the period, or the start price is
     *         more than {@value #MAX_START_GAP_DAYS} days older than the period start
     */
    public static BigDecimal periodReturn(List<PricePoint> sorted, LocalDate periodStart, LocalDate asOf) {
        PricePoint start = lastBefore(sorted, periodStart);
        PricePoint end = lastOnOrBefore(sorted, asOf);
        if (start == null || end == null || end.date().isBefore(periodStart)) {
            return null;
        }
        if (ChronoUnit.DAYS.between(start.date(), periodStart) > MAX_START_GAP_DAYS) {
            return null;
        }
        return end.price().divide(start.price(), MC).subtract(BigDecimal.ONE, MC);
    }

    /**
     * Annualized volatility: sample standard deviation (n − 1) of the last {@value #VOLATILITY_WINDOW} daily
     * log returns up to {@code asOf}, times √252. Needs {@value #VOLATILITY_WINDOW} + 1 prices.
     * Logarithms use {@code double}; inputs have at most 6 decimals, far inside double precision.
     */
    public static BigDecimal annualizedVolatility(List<PricePoint> sorted, LocalDate asOf) {
        List<PricePoint> upTo = onOrBefore(sorted, asOf);
        if (upTo.size() < VOLATILITY_WINDOW + 1) {
            return null;
        }
        List<PricePoint> window = upTo.subList(upTo.size() - (VOLATILITY_WINDOW + 1), upTo.size());
        List<BigDecimal> logReturns = new ArrayList<>(VOLATILITY_WINDOW);
        for (int i = 1; i < window.size(); i++) {
            double ratio = window.get(i).price().doubleValue() / window.get(i - 1).price().doubleValue();
            logReturns.add(new BigDecimal(Math.log(ratio), MC));
        }
        BigDecimal n = BigDecimal.valueOf(logReturns.size());
        BigDecimal mean = logReturns.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC)).divide(n, MC);
        BigDecimal squares = BigDecimal.ZERO;
        for (BigDecimal r : logReturns) {
            BigDecimal d = r.subtract(mean, MC);
            squares = squares.add(d.multiply(d, MC), MC);
        }
        BigDecimal variance = squares.divide(n.subtract(BigDecimal.ONE), MC);
        return variance.sqrt(MC).multiply(TRADING_DAYS_PER_YEAR.sqrt(MC), MC);
    }

    /**
     * Largest fall from a running peak to a later close, over closes in {@code [from, asOf]}, as a
     * non-positive fraction ({@code -0.25} = %25 düşüş). Zero if prices never fell below a previous peak.
     *
     * @return null with fewer than two prices in the window
     */
    public static BigDecimal maxDrawdown(List<PricePoint> sorted, LocalDate from, LocalDate asOf) {
        BigDecimal peak = null;
        BigDecimal worst = BigDecimal.ZERO;
        int count = 0;
        for (PricePoint p : sorted) {
            if (p.date().isBefore(from) || p.date().isAfter(asOf)) {
                continue;
            }
            count++;
            if (peak == null || p.price().compareTo(peak) > 0) {
                peak = p.price();
            } else {
                BigDecimal drawdown = p.price().divide(peak, MC).subtract(BigDecimal.ONE, MC);
                if (drawdown.compareTo(worst) < 0) {
                    worst = drawdown;
                }
            }
        }
        return count < 2 ? null : worst;
    }

    static PricePoint lastBefore(List<PricePoint> sorted, LocalDate date) {
        PricePoint found = null;
        for (PricePoint p : sorted) {
            if (!p.date().isBefore(date)) {
                break;
            }
            found = p;
        }
        return found;
    }

    static PricePoint lastOnOrBefore(List<PricePoint> sorted, LocalDate date) {
        return lastBefore(sorted, date.plusDays(1));
    }

    private static List<PricePoint> onOrBefore(List<PricePoint> sorted, LocalDate date) {
        List<PricePoint> result = new ArrayList<>();
        for (PricePoint p : sorted) {
            if (p.date().isAfter(date)) {
                break;
            }
            result.add(p);
        }
        return result;
    }
}
