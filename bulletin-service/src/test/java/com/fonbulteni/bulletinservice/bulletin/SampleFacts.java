package com.fonbulteni.bulletinservice.bulletin;

import com.fonbulteni.bulletinservice.analytics.facts.BulletinFacts;
import com.fonbulteni.bulletinservice.analytics.facts.Fact;
import com.fonbulteni.bulletinservice.analytics.facts.FactKey;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Synthetic facts for writer tests (not market data): AAV up, AHU down, AU1 (a code with a digit) flat,
 * IJB without prices. Values are chosen so their Turkish form is easy to check by hand.
 */
public final class SampleFacts {

    public static final LocalDate FRIDAY = LocalDate.of(2026, 10, 30);
    public static final List<String> FUNDS = List.of("AAV", "AHU", "AU1", "IJB");

    private SampleFacts() {
    }

    public static BulletinFacts week() {
        return week(true);
    }

    public static BulletinFacts week(boolean withBenchmarks) {
        List<Fact> list = new ArrayList<>(List.of(
                pct("fund.AAV.return.1w", "0.0321"),      // %3,2
                pct("fund.AAV.return.mtd", "0.051"),      // %5,1
                pct("fund.AAV.return.ytd", "0.41"),       // %41,0
                pct("fund.AAV.real.ytd", "0.063"),        // %6,3
                pct("fund.AHU.return.1w", "-0.0089"),     // %-0,9
                pct("fund.AHU.return.mtd", "0.0125"),     // %1,3 (HALF_UP)
                pct("fund.AU1.return.1w", "0.0004"),      // %0,0 → FLAT
                pct("fund.IJB.return.1w", null),          // veri yok
                pct("fund.IJB.return.ytd", null),
                pct("portfolio.return.1w", "0.0079"),     // %0,8
                pct("portfolio.real.1w", "0.0021")));     // %0,2
        if (withBenchmarks) {
            list.addAll(List.of(
                    pct("bench.USDTRY.return.1w", "0.004"),   // %0,4
                    pct("bench.BIST100.return.1w", "-0.025"), // %-2,5
                    pct("bench.DEPOSIT.return.1w", "0.0091"), // %0,9
                    pct("bench.GOLD.return.mom", "-0.011"),   // %-1,1
                    pct("bench.CPI.month", "0.0255")));       // %2,6 (HALF_UP)
        }
        return new BulletinFacts(FRIDAY.minusDays(4), FRIDAY, list, FUNDS, List.of("AAV", "AU1", "AHU"),
                "AAV", "AHU", true, List.of(), List.of());
    }

    private static Fact pct(String key, String value) {
        if (FactKey.describe(key).isEmpty()) {
            throw new IllegalArgumentException("not a catalog key: " + key);
        }
        return Fact.of(key, value == null ? null : new BigDecimal(value), Fact.Unit.PERCENT,
                FRIDAY.minusDays(7), FRIDAY, Fact.Source.COMPUTED, "test");
    }
}
