package com.fonbulteni.bulletinservice.analytics;

import com.fonbulteni.bulletinservice.entity.MacroPoint;
import com.fonbulteni.bulletinservice.ingest.evds.EvdsProperties;
import com.fonbulteni.bulletinservice.repository.MacroPointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.fonbulteni.bulletinservice.analytics.ReturnMath.MC;

/** Benchmark returns and inflation from stored EVDS series ({@code macro_point}), ADR 004. */
@Service
public class BenchmarkService {

    /** A deposit rate older than this is not used (the EVDS series is weekly). */
    static final int MAX_DEPOSIT_RATE_AGE_DAYS = 31;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final MacroPointRepository points;
    private final EvdsProperties.Series series;

    public BenchmarkService(MacroPointRepository points, EvdsProperties evdsProperties) {
        this.points = points;
        this.series = evdsProperties.series();
    }

    @Transactional(readOnly = true)
    public Benchmarks benchmarks(LocalDate asOf) {
        BigDecimal depositRate = null;
        LocalDate depositDate = null;
        var latestDeposit = points.findFirstBySeriesCodeAndObsDateLessThanEqualOrderByObsDateDesc(series.deposit(), asOf);
        if (latestDeposit.isPresent()
                && ChronoUnit.DAYS.between(latestDeposit.get().getObsDate(), asOf) <= MAX_DEPOSIT_RATE_AGE_DAYS) {
            depositRate = latestDeposit.get().getValue().divide(HUNDRED, MC); // EVDS gives percent
            depositDate = latestDeposit.get().getObsDate();
        }
        return new Benchmarks(
                asOf,
                periodReturns(dailySeries(series.usdtry(), asOf), asOf),
                periodReturns(dailySeries(series.bist100(), asOf), asOf),
                depositReturns(depositRate, asOf),
                depositRate,
                depositDate,
                monthlyChange(monthlySeries(series.gold(), asOf), asOf),
                Inflation.from(monthlySeries(series.cpi(), asOf), asOf));
    }

    static PeriodValues periodReturns(List<PricePoint> daily, LocalDate asOf) {
        return new PeriodValues(
                ReturnMath.periodReturn(daily, Periods.week(asOf), asOf),
                ReturnMath.periodReturn(daily, Periods.monthToDate(asOf), asOf),
                ReturnMath.periodReturn(daily, Periods.yearToDate(asOf), asOf),
                ReturnMath.periodReturn(daily, Periods.oneYear(asOf), asOf));
    }

    static PeriodValues depositReturns(BigDecimal annualRate, LocalDate asOf) {
        if (annualRate == null) {
            return PeriodValues.EMPTY;
        }
        return new PeriodValues(
                BenchmarkMath.compoundAnnual(annualRate, BenchmarkMath.days(Periods.week(asOf), asOf)),
                BenchmarkMath.compoundAnnual(annualRate, BenchmarkMath.days(Periods.monthToDate(asOf), asOf)),
                BenchmarkMath.compoundAnnual(annualRate, BenchmarkMath.days(Periods.yearToDate(asOf), asOf)),
                BenchmarkMath.compoundAnnual(annualRate, BenchmarkMath.days(Periods.oneYear(asOf), asOf)));
    }

    /**
     * Uses only completed months (before {@code asOf}'s month): the monthly figure of the running month
     * is not final yet.
     */
    static MonthlyChange monthlyChange(Map<YearMonth, BigDecimal> monthly, LocalDate asOf) {
        YearMonth latest = null;
        for (YearMonth month : monthly.keySet()) {
            if (month.isBefore(YearMonth.from(asOf))) {
                latest = month;
            }
        }
        if (latest == null) {
            return MonthlyChange.EMPTY;
        }
        BigDecimal value = monthly.get(latest);
        BigDecimal previous = monthly.get(latest.minusMonths(1));
        BigDecimal december = latest.getYear() == asOf.getYear()
                ? monthly.get(YearMonth.of(latest.getYear() - 1, 12)) : null;
        return new MonthlyChange(latest, change(value, previous), change(value, december));
    }

    private static BigDecimal change(BigDecimal value, BigDecimal base) {
        return value == null || base == null ? null : value.divide(base, MC).subtract(BigDecimal.ONE, MC);
    }

    private List<PricePoint> dailySeries(String code, LocalDate asOf) {
        LocalDate from = Periods.oneYear(asOf).minusDays(ReturnMath.MAX_START_GAP_DAYS + 1L);
        return ReturnMath.normalize(points.findBySeriesCodeAndObsDateBetweenOrderByObsDateAsc(code, from, asOf).stream()
                .filter(p -> p.getValue().signum() > 0)
                .map(p -> new PricePoint(p.getObsDate(), p.getValue()))
                .toList());
    }

    /** Monthly values keyed by month, covering the previous year's December onwards (and 13 months back). */
    Map<YearMonth, BigDecimal> monthlySeries(String code, LocalDate asOf) {
        LocalDate from = asOf.minusMonths(14).withDayOfMonth(1);
        Map<YearMonth, BigDecimal> byMonth = new TreeMap<>();
        for (MacroPoint p : points.findBySeriesCodeAndObsDateBetweenOrderByObsDateAsc(code, from, asOf)) {
            byMonth.put(YearMonth.from(p.getObsDate()), p.getValue());
        }
        return byMonth;
    }
}
