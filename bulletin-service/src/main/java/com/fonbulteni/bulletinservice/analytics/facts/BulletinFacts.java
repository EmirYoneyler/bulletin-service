package com.fonbulteni.bulletinservice.analytics.facts;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every number a bulletin for one week may use, plus the non-numeric context the writer needs
 * (ranking, sources). Immutable. Contains no personal data (AGENT_PROMPT §6.10).
 *
 * @param weekStart              Monday of the bulletin week
 * @param weekEnd                Friday of the bulletin week, the {@code asOf} of every fact
 * @param fundCodes              funds covered, in a stable order
 * @param rankingByWeeklyReturn  fund codes from best to worst weekly return (funds without one left out)
 * @param topGainer              best weekly return; null with fewer than two ranked funds
 * @param topLoser               worst weekly return; null with fewer than two ranked funds
 * @param equalWeighted          portfolio figures are an equal-weighted average (no unit counts known)
 * @param sources                where the numbers come from, for the bulletin's "Kaynaklar" section
 * @param notes                  short Turkish notes to show as is (e.g. the CPI month used)
 */
public record BulletinFacts(
        LocalDate weekStart,
        LocalDate weekEnd,
        List<Fact> facts,
        List<String> fundCodes,
        List<String> rankingByWeeklyReturn,
        String topGainer,
        String topLoser,
        boolean equalWeighted,
        List<SourceNote> sources,
        List<String> notes) {

    /** One line of the sources section, e.g. ("TEFAS", "fon fiyatları, 06.11.2026"). */
    public record SourceNote(String name, String detail) {
    }

    public BulletinFacts {
        facts = List.copyOf(facts);
        fundCodes = List.copyOf(fundCodes);
        rankingByWeeklyReturn = List.copyOf(rankingByWeeklyReturn);
        sources = List.copyOf(sources);
        notes = List.copyOf(notes);
        if (facts.stream().map(Fact::key).distinct().count() != facts.size()) {
            throw new IllegalArgumentException("Duplicate fact keys");
        }
    }

    public Optional<Fact> get(String key) {
        return facts.stream().filter(f -> f.key().equals(key)).findFirst();
    }

    public Map<String, Fact> byKey() {
        Map<String, Fact> map = new LinkedHashMap<>();
        facts.forEach(f -> map.put(f.key(), f));
        return map;
    }
}
