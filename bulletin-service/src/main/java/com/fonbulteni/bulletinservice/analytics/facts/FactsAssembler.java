package com.fonbulteni.bulletinservice.analytics.facts;

import com.fonbulteni.bulletinservice.analytics.BenchmarkService;
import com.fonbulteni.bulletinservice.analytics.Benchmarks;
import com.fonbulteni.bulletinservice.analytics.FundStats;
import com.fonbulteni.bulletinservice.analytics.FundStatsService;
import com.fonbulteni.bulletinservice.analytics.Inflation;
import com.fonbulteni.bulletinservice.analytics.PeriodValues;
import com.fonbulteni.bulletinservice.analytics.Periods;
import com.fonbulteni.bulletinservice.portfolio.Holding;
import com.fonbulteni.bulletinservice.portfolio.HoldingsProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.fonbulteni.bulletinservice.analytics.ReturnMath.MC;

/**
 * Builds the {@link BulletinFacts} of one user and week from deterministic analytics (AGENT_PROMPT §7.4).
 * Every number in the bulletin comes from here; missing data stays a fact with a null value ("veri yok").
 * The user's e-mail only selects the funds and never ends up in the facts.
 */
@Service
public class FactsAssembler {

    private static final DateTimeFormatter TR_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final HoldingsProvider holdings;
    private final FundStatsService fundStats;
    private final BenchmarkService benchmarks;

    public FactsAssembler(HoldingsProvider holdings, FundStatsService fundStats, BenchmarkService benchmarks) {
        this.holdings = holdings;
        this.fundStats = fundStats;
        this.benchmarks = benchmarks;
    }

    public BulletinFacts assemble(String email, BulletinWeek week) {
        LocalDate asOf = week.friday();
        List<FundStats> stats = holdings.holdingsOf(email).stream()
                .map(Holding::fund)
                .filter(f -> f.getTefasCode() != null)
                .sorted(Comparator.comparing(f -> f.getTefasCode()))
                .map(f -> fundStats.compute(f, asOf))
                .toList();
        Benchmarks bench = benchmarks.benchmarks(asOf);
        Inflation inflation = bench.inflation();

        List<Fact> facts = new ArrayList<>();
        for (FundStats s : stats) {
            String code = s.tefasCode();
            facts.add(fact(FactKey.FUND_RETURN_1W.key(code), s.return1w(), FactKey.FUND_RETURN_1W, Periods.week(asOf), asOf));
            facts.add(fact(FactKey.FUND_RETURN_MTD.key(code), s.returnMtd(), FactKey.FUND_RETURN_MTD, Periods.monthToDate(asOf), asOf));
            facts.add(fact(FactKey.FUND_RETURN_YTD.key(code), s.returnYtd(), FactKey.FUND_RETURN_YTD, Periods.yearToDate(asOf), asOf));
            facts.add(fact(FactKey.FUND_RETURN_1Y.key(code), s.return1y(), FactKey.FUND_RETURN_1Y, Periods.oneYear(asOf), asOf));
            facts.add(fact(FactKey.FUND_REAL_YTD.key(code), inflation.realOf(s.returns()).yearToDate(),
                    FactKey.FUND_REAL_YTD, Periods.yearToDate(asOf), asOf));
            facts.add(fact(FactKey.FUND_VOL_63D.key(code), s.volatility63d(), FactKey.FUND_VOL_63D, null, asOf));
            facts.add(fact(FactKey.FUND_DRAWDOWN_1Y.key(code), s.maxDrawdown1y(), FactKey.FUND_DRAWDOWN_1Y, Periods.oneYear(asOf), asOf));
        }

        PeriodValues portfolio = new PeriodValues(average(stats, FundStats::return1w), average(stats, FundStats::returnMtd),
                average(stats, FundStats::returnYtd), null);
        facts.add(fact(FactKey.PORTFOLIO_RETURN_1W.key(), portfolio.week(), FactKey.PORTFOLIO_RETURN_1W, Periods.week(asOf), asOf));
        facts.add(fact(FactKey.PORTFOLIO_RETURN_MTD.key(), portfolio.monthToDate(), FactKey.PORTFOLIO_RETURN_MTD, Periods.monthToDate(asOf), asOf));
        facts.add(fact(FactKey.PORTFOLIO_RETURN_YTD.key(), portfolio.yearToDate(), FactKey.PORTFOLIO_RETURN_YTD, Periods.yearToDate(asOf), asOf));
        facts.add(fact(FactKey.PORTFOLIO_REAL_1W.key(), inflation.realOf(portfolio).week(), FactKey.PORTFOLIO_REAL_1W, Periods.week(asOf), asOf));

        addPeriods(facts, bench.usdTry(), FactKey.BENCH_USDTRY_1W, FactKey.BENCH_USDTRY_MTD, FactKey.BENCH_USDTRY_YTD, asOf);
        addPeriods(facts, bench.bist100(), FactKey.BENCH_BIST100_1W, FactKey.BENCH_BIST100_MTD, FactKey.BENCH_BIST100_YTD, asOf);
        addPeriods(facts, bench.deposit(), FactKey.BENCH_DEPOSIT_1W, FactKey.BENCH_DEPOSIT_MTD, FactKey.BENCH_DEPOSIT_YTD, asOf);
        var gold = bench.gold();
        LocalDate goldStart = gold.month() == null ? null : gold.month().atDay(1);
        LocalDate goldEnd = gold.month() == null ? null : gold.month().atEndOfMonth();
        facts.add(fact(FactKey.BENCH_GOLD_MOM.key(), gold.monthOverMonth(), FactKey.BENCH_GOLD_MOM, goldStart, goldEnd));
        facts.add(fact(FactKey.BENCH_GOLD_YTD.key(), gold.yearToDate(), FactKey.BENCH_GOLD_YTD,
                goldEnd == null ? null : LocalDate.of(goldEnd.getYear(), 1, 1), goldEnd));
        LocalDate cpiStart = inflation.cpiMonth() == null ? null : inflation.cpiMonth().atDay(1);
        LocalDate cpiEnd = inflation.cpiMonth() == null ? null : inflation.cpiMonth().atEndOfMonth();
        facts.add(fact(FactKey.BENCH_CPI_MONTH.key(), inflation.monthlyRate(), FactKey.BENCH_CPI_MONTH, cpiStart, cpiEnd));
        facts.add(fact(FactKey.BENCH_CPI_YTD.key(), inflation.periodInflation().yearToDate(), FactKey.BENCH_CPI_YTD,
                Periods.yearToDate(asOf), asOf));

        List<String> ranking = stats.stream()
                .filter(s -> s.return1w() != null)
                .sorted(Comparator.comparing(FundStats::return1w).reversed().thenComparing(FundStats::tefasCode))
                .map(FundStats::tefasCode)
                .toList();
        boolean ranked = ranking.size() >= 2;

        return new BulletinFacts(week.monday(), asOf, facts,
                stats.stream().map(FundStats::tefasCode).toList(),
                ranking,
                ranked ? ranking.get(0) : null,
                ranked ? ranking.get(ranking.size() - 1) : null,
                true, // no unit counts before E6-1
                sources(stats, bench), notes(stats, bench));
    }

    private static Fact fact(String key, BigDecimal value, FactKey kind, LocalDate start, LocalDate end) {
        return Fact.of(key, value, kind.unit(), start, end, kind.source(), kind.method());
    }

    private static void addPeriods(List<Fact> facts, PeriodValues values, FactKey week, FactKey mtd, FactKey ytd,
                                   LocalDate asOf) {
        facts.add(fact(week.key(), values.week(), week, Periods.week(asOf), asOf));
        facts.add(fact(mtd.key(), values.monthToDate(), mtd, Periods.monthToDate(asOf), asOf));
        facts.add(fact(ytd.key(), values.yearToDate(), ytd, Periods.yearToDate(asOf), asOf));
    }

    /** Equal-weighted mean of the funds that have the value; null if none has it. */
    static BigDecimal average(List<FundStats> stats, Function<FundStats, BigDecimal> value) {
        List<BigDecimal> present = stats.stream().map(value).filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        BigDecimal sum = present.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC));
        return sum.divide(BigDecimal.valueOf(present.size()), MC);
    }

    private static List<BulletinFacts.SourceNote> sources(List<FundStats> stats, Benchmarks bench) {
        List<BulletinFacts.SourceNote> sources = new ArrayList<>();
        stats.stream().map(FundStats::lastPriceDate).filter(Objects::nonNull).max(Comparator.naturalOrder())
                .ifPresent(d -> sources.add(new BulletinFacts.SourceNote("TEFAS", "fon fiyatları, " + TR_DATE.format(d))));
        List<String> evds = new ArrayList<>();
        if (bench.inflation().cpiMonth() != null) {
            evds.add(Inflation.monthLabel(bench.inflation().cpiMonth()) + " TÜFE");
        }
        if (bench.usdTry().week() != null || bench.usdTry().yearToDate() != null) {
            evds.add("USD/TRY");
        }
        if (bench.bist100().week() != null || bench.bist100().yearToDate() != null) {
            evds.add("BIST 100 (Borsa İstanbul)");
        }
        if (bench.gold().month() != null) {
            evds.add("altın");
        }
        if (bench.depositRate() != null) {
            evds.add("mevduat faizi");
        }
        if (!evds.isEmpty()) {
            sources.add(new BulletinFacts.SourceNote("TCMB EVDS", String.join(", ", evds)));
        }
        return sources;
    }

    private static List<String> notes(List<FundStats> stats, Benchmarks bench) {
        List<String> notes = new ArrayList<>();
        if (!stats.isEmpty()) {
            notes.add("Portföy değişimi, izlenen fonların eşit ağırlıklı ortalamasıdır.");
        }
        if (bench.inflation().cpiMonth() != null) {
            notes.add(bench.inflation().note());
        }
        if (bench.gold().month() != null) {
            notes.add("Altın fiyatı aylıktır; " + Inflation.monthLabel(bench.gold().month()) + " değişimi kullanıldı.");
        }
        if (bench.depositRate() != null) {
            notes.add("Mevduat getirisi " + TR_DATE.format(bench.depositRateDate())
                    + " tarihli brüt TL mevduat faiziyle hesaplandı.");
        }
        if (bench.inflation().cpiMonth() == null && bench.usdTry().week() == null && bench.depositRate() == null) {
            notes.add("Kıyas ve enflasyon verisi (TCMB EVDS) yok; bu bölümler bültende yer almaz.");
        }
        return notes;
    }
}
