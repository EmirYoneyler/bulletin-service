package com.fonbulteni.bulletinservice.bulletin.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fonbulteni.bulletinservice.analytics.facts.BulletinFacts;
import com.fonbulteni.bulletinservice.analytics.facts.Fact;
import com.fonbulteni.bulletinservice.analytics.facts.FactKey;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateLlmClientTest {

    static final LocalDate FRI = LocalDate.of(2026, 11, 6);
    static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]+)}}");
    /** Advice and forecast roots that must never appear (AGENT_PROMPT §6.3, §7.5). */
    static final Pattern FORBIDDEN = Pattern.compile(
            "(?iu)tavsiye|öner|kesin|garanti|kaçırma|fırsat|hedef fiyat|beklen|yükselecek|düşecek|almalı|satmalı");

    private final ObjectMapper mapper = new ObjectMapper();
    private final TemplateLlmClient client = new TemplateLlmClient(mapper);

    /** Source and method do not matter to the writer; only key, value (→ direction) and description do. */
    private static Fact pct(String key, String value) {
        assertThat(FactKey.describe(key)).as(key).isPresent();
        return Fact.of(key, value == null ? null : new BigDecimal(value), Fact.Unit.PERCENT, null, FRI,
                Fact.Source.COMPUTED, "test");
    }

    /** AAV up, AHU down, AU1 (code with a digit) flat, IJB without prices. */
    static BulletinFacts facts(LocalDate weekEnd, boolean withBenchmarks) {
        List<Fact> list = new ArrayList<>(List.of(
                pct("fund.AAV.return.1w", "0.03"), pct("fund.AAV.return.mtd", "0.05"), pct("fund.AAV.return.ytd", "0.5"),
                pct("fund.AAV.real.ytd", "0.25"),
                pct("fund.AHU.return.1w", "-0.01"), pct("fund.AHU.return.mtd", "0.01"),
                pct("fund.AU1.return.1w", "0.0001"),
                pct("fund.IJB.return.1w", null), pct("fund.IJB.return.ytd", null),
                pct("portfolio.return.1w", "0.0067"), pct("portfolio.real.1w", "0.002")));
        if (withBenchmarks) {
            list.addAll(List.of(pct("bench.USDTRY.return.1w", "0.004"), pct("bench.BIST100.return.1w", "-0.025"),
                    pct("bench.DEPOSIT.return.1w", "0.007"), pct("bench.GOLD.return.mom", "0.1"),
                    pct("bench.CPI.month", "0.02")));
        }
        return new BulletinFacts(weekEnd.minusDays(4), weekEnd, list, List.of("AAV", "AHU", "AU1", "IJB"),
                List.of("AAV", "AU1", "AHU"), "AAV", "AHU", true, List.of(), List.of());
    }

    private JsonNode write(BulletinFacts facts) throws Exception {
        WriterContext context = WriterContext.from(facts);
        LlmRequest request = new LlmRequest("sistem", "Bülteni yaz.\n" + context.embed(mapper), true, 800, 0.2);
        LlmResponse response = client.complete(request);
        assertThat(response.model()).isEqualTo("template-v1");
        return mapper.readTree(response.text());
    }

    private static List<String> allTexts(JsonNode out) {
        List<String> texts = new ArrayList<>();
        texts.add(out.get("summary").asText());
        out.get("fundNotes").forEach(n -> texts.add(n.get("text").asText()));
        texts.add(out.get("benchmarkNote").asText());
        return texts;
    }

    @Test
    void producesTheWriterJsonShape() throws Exception {
        JsonNode out = write(facts(FRI, true));

        assertThat(out.get("summary").asText()).isNotBlank();
        assertThat(out.get("fundNotes")).hasSize(4);
        assertThat(out.get("fundNotes").findValuesAsText("code")).containsExactly("AAV", "AHU", "AU1", "IJB");
        assertThat(out.get("benchmarkNote").asText()).contains("{{bench.USDTRY.return.1w}}").contains("{{bench.CPI.month}}");
    }

    @Test
    void containsNoDigitsOutsidePlaceholdersFundCodesAndAllowedNames() throws Exception {
        List<String> codes = List.of("AAV", "AHU", "AU1", "IJB");
        for (String text : allTexts(write(facts(FRI, true)))) {
            assertThat(TextRules.withoutAllowedDigitTokens(text, codes)).as(text).doesNotContainPattern("\\d");
        }
    }

    @Test
    void allowedDigitTokensAreOnlyWholeNamesAndCodes() {
        List<String> codes = List.of("AU1");
        // Allowed: a placeholder, a listed fund code, the name BIST 100
        assertThat(TextRules.withoutAllowedDigitTokens("AU1 fonu {{fund.AU1.return.1w}}, BIST 100 yatay.", codes))
                .doesNotContainPattern("\\d");
        // Not allowed: a code glued into another word, an unlisted code, a bare number next to a name
        assertThat(TextRules.withoutAllowedDigitTokens("XAU1 fonu", codes)).containsPattern("\\d");
        assertThat(TextRules.withoutAllowedDigitTokens("ZZ9 fonu", codes)).containsPattern("\\d");
        assertThat(TextRules.withoutAllowedDigitTokens("BIST 100 %3,2 yükseldi", codes)).containsPattern("\\d");
    }

    @Test
    void everyPlaceholderRefersToAnAvailableFact() throws Exception {
        BulletinFacts facts = facts(FRI, true);
        Set<String> available = new HashSet<>();
        facts.facts().stream().filter(f -> !f.missing()).forEach(f -> available.add(f.key()));

        for (String text : allTexts(write(facts))) {
            Matcher m = PLACEHOLDER.matcher(text);
            while (m.find()) {
                assertThat(available).as(text).contains(m.group(1));
            }
        }
        // IJB has no data: it is mentioned without any placeholder
        assertThat(allTexts(write(facts)).get(4)).isEqualTo("IJB fonu için bu hafta yeterli fiyat verisi yok.");
    }

    @Test
    void wordingFollowsDirections() throws Exception {
        JsonNode out = write(facts(FRI, true));
        String aav = out.get("fundNotes").get(0).get("text").asText();
        String ahu = out.get("fundNotes").get(1).get("text").asText();
        String au1 = out.get("fundNotes").get(2).get("text").asText();

        assertThat(aav).containsAnyOf("artıda", "kazandı", "yükseldi").doesNotContain("ekside", "kaybetti", "geriledi");
        assertThat(ahu).containsAnyOf("ekside", "kaybetti", "geriledi").doesNotContain("artıda", "kazandı", "yükseldi");
        assertThat(au1).containsAnyOf("yatay", "belirgin bir hareket olmadı", "neredeyse değişmedi");
        assertThat(out.get("summary").asText()).contains("AAV").contains("AHU"); // top gainer and loser named
    }

    @Test
    void neverUsesAdviceOrForecastWords() throws Exception {
        for (int week = 0; week < 21; week++) {
            for (String text : allTexts(write(facts(FRI.plusWeeks(week), true)))) {
                assertThat(text).doesNotContainPattern(FORBIDDEN);
            }
        }
    }

    @Test
    void wordingVariesFromWeekToWeekButIsReproducible() throws Exception {
        Set<String> openings = new HashSet<>();
        for (int week = 0; week < 12; week++) {
            String summary = write(facts(FRI.plusWeeks(week), true)).get("summary").asText();
            openings.add(summary.substring(0, summary.indexOf('{')));
        }
        assertThat(openings).hasSizeGreaterThanOrEqualTo(3);
        assertThat(write(facts(FRI, true))).isEqualTo(write(facts(FRI, true)));
    }

    @Test
    void withoutBenchmarksTheNoteIsEmpty() throws Exception {
        assertThat(write(facts(FRI, false)).get("benchmarkNote").asText()).isEmpty();
    }

    @Test
    void contextCarriesNoValues() {
        String embedded = WriterContext.from(facts(FRI, true)).embed(mapper);

        assertThat(embedded).doesNotContain("0.03").doesNotContain("value");
        assertThat(WriterContext.extract("önce\n" + embedded + "\nsonra", mapper)).isPresent();
        assertThat(WriterContext.extract("blok yok", mapper)).isEmpty();
    }

    @Test
    void missingContextIsABadRequest() {
        assertThatThrownBy(() -> client.complete(new LlmRequest(null, "bağlam yok", true, 100, 0.2)))
                .isInstanceOf(LlmException.class)
                .extracting("kind").isEqualTo(LlmException.Kind.BAD_REQUEST);
    }
}
