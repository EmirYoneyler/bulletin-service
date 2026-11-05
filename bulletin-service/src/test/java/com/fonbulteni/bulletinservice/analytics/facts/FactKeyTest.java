package com.fonbulteni.bulletinservice.analytics.facts;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FactKeyTest {

    @Test
    void buildsConcreteKeys() {
        assertThat(FactKey.FUND_RETURN_1W.key("AAV")).isEqualTo("fund.AAV.return.1w");
        assertThat(FactKey.BENCH_USDTRY_1W.key()).isEqualTo("bench.USDTRY.return.1w");
        assertThatThrownBy(() -> FactKey.FUND_RETURN_1W.key()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> FactKey.BENCH_CPI_MONTH.key("AAV")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void describesConcreteKeysInTurkish() {
        assertThat(FactKey.describe("fund.IJB.return.ytd")).contains("IJB fonunun yılbaşından bu yana getirisi");
        assertThat(FactKey.describe("bench.CPI.month")).contains("Son açıklanan aylık enflasyon (TÜFE)");
        assertThat(FactKey.describe("fund.AAV.return.2w")).isEmpty();      // unknown period
        assertThat(FactKey.describe("fund.aav.return.1w")).isEmpty();      // codes are upper case
        assertThat(FactKey.describe("bench.EUR.return.1w")).isEmpty();
    }

    @Test
    void everyKeyIsAValidFactKeyAndHasADescription() {
        for (FactKey k : FactKey.values()) {
            String concrete = k.perFund() ? k.key("AAV") : k.key();
            Fact fact = Fact.of(concrete, BigDecimal.ONE, k.unit(), null, LocalDate.of(2026, 11, 6), k.source(), k.method());
            assertThat(fact.key()).isEqualTo(concrete);
            assertThat(FactKey.describe(concrete)).as(concrete).isPresent();
        }
    }

    @Test
    void docsListEveryKeyWithItsUnitSourceAndMethod() throws IOException {
        // Maven runs tests from the module directory; the docs live in the repository root.
        List<String> lines = Files.readAllLines(Path.of("..", "docs", "facts-keys.md"), StandardCharsets.UTF_8);

        for (FactKey k : FactKey.values()) {
            String row = lines.stream().filter(l -> l.startsWith("| `" + k.pattern() + "` |")).findFirst()
                    .orElseThrow(() -> new AssertionError("docs/facts-keys.md has no row for " + k.pattern()));
            assertThat(row).as(k.pattern())
                    .contains("| " + k.unit() + " |")
                    .contains("| " + k.source() + " |")
                    .contains("`" + k.method() + "`");
        }
        long documentedRows = lines.stream().filter(l -> l.matches("\\| `(fund|portfolio|bench)\\..*")).count();
        assertThat(documentedRows).isEqualTo(FactKey.values().length);
    }

    @Test
    void bulletinFactsRejectsDuplicateKeysAndLooksUpByKey() {
        Fact a = Fact.of("bench.CPI.month", new BigDecimal("0.02"), Fact.Unit.PERCENT, null, null, Fact.Source.EVDS, "cpi.monthly");
        LocalDate fri = LocalDate.of(2026, 11, 6);

        BulletinFacts facts = new BulletinFacts(fri.minusDays(4), fri, List.of(a), List.of(), List.of(), null, null,
                true, List.of(), List.of());
        assertThat(facts.get("bench.CPI.month")).contains(a);
        assertThat(facts.byKey()).containsOnlyKeys("bench.CPI.month");

        assertThatThrownBy(() -> new BulletinFacts(fri.minusDays(4), fri, List.of(a, a), List.of(), List.of(), null,
                null, true, List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
