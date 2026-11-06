package com.fonbulteni.bulletinservice.bulletin.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fonbulteni.bulletinservice.analytics.facts.BulletinFacts;
import com.fonbulteni.bulletinservice.analytics.facts.Fact;
import com.fonbulteni.bulletinservice.analytics.facts.FactKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a writer may know about a bulletin: available fact keys with their Turkish description and
 * direction, plus ranking and highlights. <strong>No values</strong> (AGENT_PROMPT §4.2): the writer can
 * say "yükseldi" but cannot know by how much. Missing facts are left out so they cannot be referenced.
 *
 * <p>It travels inside the user message between {@link #START} and {@link #END}, so the template writer
 * and a real model receive exactly the same input.
 *
 * @param seed varies the template wording from week to week; not meant for the model
 */
public record WriterContext(
        int seed,
        List<FundContext> funds,
        List<FactRef> portfolio,
        List<FactRef> benchmarks,
        List<String> ranking,
        String topGainer,
        String topLoser,
        boolean equalWeighted) {

    public static final String START = "<<<FACTS";
    public static final String END = "FACTS>>>";

    public record FactRef(String key, String description, Fact.Direction direction) {
    }

    public record FundContext(String code, List<FactRef> facts) {

        public Optional<FactRef> fact(String key) {
            return facts.stream().filter(f -> f.key().equals(key)).findFirst();
        }
    }

    public static WriterContext from(BulletinFacts facts) {
        List<FundContext> funds = new ArrayList<>();
        for (String code : facts.fundCodes()) {
            funds.add(new FundContext(code, refs(facts, "fund." + code + ".")));
        }
        return new WriterContext(
                (int) facts.weekEnd().toEpochDay(),
                funds,
                refs(facts, "portfolio."),
                refs(facts, "bench."),
                facts.rankingByWeeklyReturn(),
                facts.topGainer(),
                facts.topLoser(),
                facts.equalWeighted());
    }

    private static List<FactRef> refs(BulletinFacts facts, String prefix) {
        return facts.facts().stream()
                .filter(f -> f.key().startsWith(prefix) && !f.missing())
                .map(f -> new FactRef(f.key(), FactKey.describe(f.key()).orElse(f.key()), f.direction()))
                .toList();
    }

    public Optional<FactRef> portfolioFact(String key) {
        return portfolio.stream().filter(f -> f.key().equals(key)).findFirst();
    }

    public Optional<FactRef> benchmarkFact(String key) {
        return benchmarks.stream().filter(f -> f.key().equals(key)).findFirst();
    }

    /**
     * The context as a marked block to place inside a prompt. Compact JSON: indentation roughly doubles the
     * prompt's token count, which costs minutes on a CPU-only local model (E3-2 Ollama run).
     */
    public String embed(ObjectMapper mapper) {
        try {
            return START + "\n" + mapper.writeValueAsString(this) + "\n" + END;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("WriterContext could not be serialized", e);
        }
    }

    /** Reads the context back from a prompt; empty if the prompt has no (readable) block. */
    public static Optional<WriterContext> extract(String prompt, ObjectMapper mapper) {
        if (prompt == null) {
            return Optional.empty();
        }
        int start = prompt.indexOf(START);
        int end = prompt.indexOf(END, start + 1);
        if (start < 0 || end < 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(prompt.substring(start + START.length(), end), WriterContext.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
