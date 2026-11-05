package com.fonbulteni.bulletinservice.dto;

import com.fonbulteni.bulletinservice.analytics.facts.BulletinFacts;
import com.fonbulteni.bulletinservice.analytics.facts.Fact;
import com.fonbulteni.bulletinservice.analytics.facts.FactFormatter;
import com.fonbulteni.bulletinservice.analytics.facts.FactKey;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Admin preview of a bulletin's facts: raw value, Turkish display and description side by side. */
public record FactsPreviewResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        List<FactView> facts,
        List<String> fundCodes,
        List<String> rankingByWeeklyReturn,
        String topGainer,
        String topLoser,
        boolean equalWeighted,
        List<BulletinFacts.SourceNote> sources,
        List<String> notes) {

    public record FactView(
            String key,
            String description,
            BigDecimal value,
            String formatted,
            Fact.Unit unit,
            Fact.Direction direction,
            LocalDate periodStart,
            LocalDate periodEnd,
            Fact.Source source,
            String method) {
    }

    public static FactsPreviewResponse from(BulletinFacts facts) {
        List<FactView> views = facts.facts().stream()
                .map(f -> new FactView(f.key(), FactKey.describe(f.key()).orElse(null), f.value(), FactFormatter.format(f),
                        f.unit(), f.direction(), f.periodStart(), f.periodEnd(), f.source(), f.method()))
                .toList();
        return new FactsPreviewResponse(facts.weekStart(), facts.weekEnd(), views, facts.fundCodes(),
                facts.rankingByWeeklyReturn(), facts.topGainer(), facts.topLoser(), facts.equalWeighted(),
                facts.sources(), facts.notes());
    }
}
