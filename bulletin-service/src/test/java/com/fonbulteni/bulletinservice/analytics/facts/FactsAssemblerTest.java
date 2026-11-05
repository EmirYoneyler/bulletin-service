package com.fonbulteni.bulletinservice.analytics.facts;

import com.fonbulteni.bulletinservice.analytics.BenchmarkService;
import com.fonbulteni.bulletinservice.analytics.Benchmarks;
import com.fonbulteni.bulletinservice.analytics.FundStats;
import com.fonbulteni.bulletinservice.analytics.FundStatsService;
import com.fonbulteni.bulletinservice.analytics.Inflation;
import com.fonbulteni.bulletinservice.analytics.MonthlyChange;
import com.fonbulteni.bulletinservice.analytics.PeriodValues;
import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.portfolio.Holding;
import com.fonbulteni.bulletinservice.portfolio.HoldingsProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** Synthetic inputs, expected values by hand. Week of 2–6 Nov 2026. */
class FactsAssemblerTest {

    static final BulletinWeek WEEK = BulletinWeek.containing(LocalDate.of(2026, 11, 4));
    static final LocalDate FRI = WEEK.friday();

    private final HoldingsProvider holdings = mock(HoldingsProvider.class);
    private final FundStatsService fundStats = mock(FundStatsService.class);
    private final BenchmarkService benchmarks = mock(BenchmarkService.class);
    private final FactsAssembler assembler = new FactsAssembler(holdings, fundStats, benchmarks);

    private static Fund fund(long id, String code) {
        return Fund.builder().id(id).name(code + " FONU").tefasCode(code).build();
    }

    private static FundStats stats(Fund f, String w, String mtd, String ytd) {
        return new FundStats(f.getId(), f.getTefasCode(), FRI, FRI, new BigDecimal("1"),
                w == null ? null : new BigDecimal(w), mtd == null ? null : new BigDecimal(mtd),
                ytd == null ? null : new BigDecimal(ytd), null, null, null);
    }

    /** CPI: Oct 2026 = 204 (+2 % on Sep), Dec 2025 = 170 → year-to-date inflation 0.2. */
    private static Inflation inflation() {
        Map<YearMonth, BigDecimal> cpi = new TreeMap<>(Map.of(
                YearMonth.of(2025, 12), new BigDecimal("170"),
                YearMonth.of(2026, 9), new BigDecimal("200"),
                YearMonth.of(2026, 10), new BigDecimal("204")));
        return Inflation.from(cpi, FRI);
    }

    private void givenThreeFunds(Benchmarks bench) {
        Fund aav = fund(1, "AAV");
        Fund ahu = fund(2, "AHU");
        Fund ijb = fund(3, "IJB");
        // Provider order is not alphabetical on purpose; a manual fund without a code is ignored.
        given(holdings.holdingsOf("okur@example.com")).willReturn(List.of(
                new Holding(ijb, null), new Holding(aav, null), new Holding(Fund.builder().id(9L).name("Kodsuz").build(), null),
                new Holding(ahu, null)));
        given(fundStats.compute(eq(aav), eq(FRI))).willReturn(stats(aav, "0.03", "0.05", "0.5"));
        given(fundStats.compute(eq(ahu), eq(FRI))).willReturn(stats(ahu, "-0.01", "0.01", "0.2"));
        given(fundStats.compute(eq(ijb), eq(FRI))).willReturn(stats(ijb, null, null, null)); // no prices
        given(benchmarks.benchmarks(FRI)).willReturn(bench);
    }

    private static Benchmarks fullBenchmarks() {
        return new Benchmarks(FRI,
                new PeriodValues(new BigDecimal("0.004"), new BigDecimal("0.004"), new BigDecimal("0.3125"), null),
                new PeriodValues(new BigDecimal("-0.025"), null, null, null),
                new PeriodValues(new BigDecimal("0.007"), new BigDecimal("0.006"), new BigDecimal("0.37"), new BigDecimal("0.45")),
                new BigDecimal("0.45"), LocalDate.of(2026, 10, 30),
                new MonthlyChange(YearMonth.of(2026, 10), new BigDecimal("0.1"), new BigDecimal("0.375")),
                inflation());
    }

    @Test
    void weekIsMondayToFriday() {
        assertThat(WEEK.monday()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(FRI.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(BulletinWeek.containing(LocalDate.of(2026, 11, 8)).friday()).isEqualTo(FRI);   // Sunday
        assertThat(BulletinWeek.latestCompleted(LocalDate.of(2026, 11, 9)).friday()).isEqualTo(FRI); // Monday bulletin
        assertThat(BulletinWeek.latestCompleted(FRI).friday()).isEqualTo(FRI);
    }

    @Test
    void fundFactsInStableOrderWithMissingValuesKept() {
        givenThreeFunds(fullBenchmarks());

        BulletinFacts facts = assembler.assemble("okur@example.com", WEEK);

        assertThat(facts.fundCodes()).containsExactly("AAV", "AHU", "IJB");
        assertThat(facts.get("fund.AAV.return.1w").orElseThrow().value()).isEqualByComparingTo("0.03");
        assertThat(facts.get("fund.AAV.return.1w").orElseThrow().direction()).isEqualTo(Fact.Direction.UP);
        assertThat(facts.get("fund.AAV.return.1w").orElseThrow().periodStart()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(facts.get("fund.AHU.return.1w").orElseThrow().direction()).isEqualTo(Fact.Direction.DOWN);
        // IJB has no prices: its facts exist with null values ("veri yok"), not zeros
        Fact ijb = facts.get("fund.IJB.return.1w").orElseThrow();
        assertThat(ijb.value()).isNull();
        assertThat(FactFormatter.format(ijb)).isEqualTo("veri yok");
        // real YTD: 1.5 / 1.2 − 1 = 0.25
        assertThat(facts.get("fund.AAV.real.ytd").orElseThrow().value()).isEqualByComparingTo("0.25");
    }

    @Test
    void portfolioIsEqualWeightedOverFundsThatHaveTheValue() {
        givenThreeFunds(fullBenchmarks());

        BulletinFacts facts = assembler.assemble("okur@example.com", WEEK);

        assertThat(facts.equalWeighted()).isTrue();
        // (0.03 + (−0.01)) / 2 = 0.01; IJB has no value and is left out
        assertThat(facts.get("portfolio.return.1w").orElseThrow().value()).isEqualByComparingTo("0.01");
        assertThat(facts.get("portfolio.return.ytd").orElseThrow().value()).isEqualByComparingTo("0.35");
        // real week: 1.01 / 1.02^(7/30) − 1
        assertThat(facts.get("portfolio.real.1w").orElseThrow().value().doubleValue())
                .isCloseTo(1.01 / Math.pow(1.02, 7.0 / 30) - 1, within(1e-12));
    }

    @Test
    void rankingAndHighlights() {
        givenThreeFunds(fullBenchmarks());

        BulletinFacts facts = assembler.assemble("okur@example.com", WEEK);

        assertThat(facts.rankingByWeeklyReturn()).containsExactly("AAV", "AHU");
        assertThat(facts.topGainer()).isEqualTo("AAV");
        assertThat(facts.topLoser()).isEqualTo("AHU");
    }

    @Test
    void benchmarkFactsSourcesAndNotes() {
        givenThreeFunds(fullBenchmarks());

        BulletinFacts facts = assembler.assemble("okur@example.com", WEEK);

        assertThat(facts.get("bench.USDTRY.return.1w").orElseThrow().value()).isEqualByComparingTo("0.004");
        assertThat(facts.get("bench.BIST100.return.1w").orElseThrow().direction()).isEqualTo(Fact.Direction.DOWN);
        assertThat(facts.get("bench.GOLD.return.mom").orElseThrow().periodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(facts.get("bench.CPI.month").orElseThrow().value()).isEqualByComparingTo("0.02");
        assertThat(facts.get("bench.CPI.ytd").orElseThrow().value()).isEqualByComparingTo("0.2");
        assertThat(facts.sources()).containsExactly(
                new BulletinFacts.SourceNote("TEFAS", "fon fiyatları, 06.11.2026"),
                new BulletinFacts.SourceNote("TCMB EVDS",
                        "Ekim 2026 TÜFE, USD/TRY, BIST 100 (Borsa İstanbul), altın, mevduat faizi"));
        assertThat(facts.notes()).containsExactly(
                "Portföy değişimi, izlenen fonların eşit ağırlıklı ortalamasıdır.",
                "Ekim 2026 TÜFE'si kullanıldı.",
                "Altın fiyatı aylıktır; Ekim 2026 değişimi kullanıldı.",
                "Mevduat getirisi 30.10.2026 tarihli brüt TL mevduat faiziyle hesaplandı.");
    }

    @Test
    void withoutEvdsDataBenchmarksAreMissingAndSaidSo() {
        givenThreeFunds(new Benchmarks(FRI, PeriodValues.EMPTY, PeriodValues.EMPTY, PeriodValues.EMPTY, null, null,
                MonthlyChange.EMPTY, Inflation.EMPTY));

        BulletinFacts facts = assembler.assemble("okur@example.com", WEEK);

        assertThat(facts.get("bench.USDTRY.return.1w").orElseThrow().missing()).isTrue();
        assertThat(facts.get("fund.AAV.real.ytd").orElseThrow().missing()).isTrue();
        assertThat(facts.sources()).extracting(BulletinFacts.SourceNote::name).containsExactly("TEFAS");
        assertThat(facts.notes()).contains("Kıyas ve enflasyon verisi (TCMB EVDS) yok; bu bölümler bültende yer almaz.");
    }

    @Test
    void factsNeverContainTheEmail() {
        givenThreeFunds(fullBenchmarks());

        BulletinFacts facts = assembler.assemble("okur@example.com", WEEK);

        assertThat(facts.toString()).doesNotContain("okur@example.com");
    }

    @Test
    void everyFactKeyIsInTheCatalog() {
        givenThreeFunds(fullBenchmarks());

        assembler.assemble("okur@example.com", WEEK).facts()
                .forEach(f -> assertThat(FactKey.describe(f.key())).as(f.key()).isPresent());
    }

    @Test
    void userWithoutFundsStillGetsBenchmarks() {
        given(holdings.holdingsOf(any())).willReturn(List.of());
        given(benchmarks.benchmarks(FRI)).willReturn(fullBenchmarks());

        BulletinFacts facts = assembler.assemble("yeni@example.com", WEEK);

        assertThat(facts.fundCodes()).isEmpty();
        assertThat(facts.get("portfolio.return.1w").orElseThrow().missing()).isTrue();
        assertThat(facts.topGainer()).isNull();
        assertThat(facts.get("bench.CPI.month")).isPresent();
    }
}
