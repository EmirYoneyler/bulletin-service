package com.fonbulteni.bulletinservice.bulletin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fonbulteni.bulletinservice.analytics.facts.BulletinFacts;
import com.fonbulteni.bulletinservice.analytics.facts.Fact;
import com.fonbulteni.bulletinservice.analytics.facts.FactFormatter;
import com.fonbulteni.bulletinservice.bulletin.llm.LlmClient;
import com.fonbulteni.bulletinservice.bulletin.llm.LlmException;
import com.fonbulteni.bulletinservice.bulletin.llm.LlmRequest;
import com.fonbulteni.bulletinservice.bulletin.llm.LlmResponse;
import com.fonbulteni.bulletinservice.bulletin.llm.TemplateLlmClient;
import com.fonbulteni.bulletinservice.bulletin.llm.TextRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WriterStepTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Answers with a fixed text and remembers what it was asked. */
    static final class FakeLlm implements LlmClient {
        final List<LlmRequest> requests = new ArrayList<>();
        private final Function<LlmRequest, LlmResponse> answer;

        FakeLlm(Function<LlmRequest, LlmResponse> answer) {
            this.answer = answer;
        }

        static FakeLlm answering(String text) {
            return new FakeLlm(r -> new LlmResponse(text, 900, 150, 4200, "fake-7b"));
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            requests.add(request);
            return answer.apply(request);
        }

        @Override
        public String name() {
            return "ollama";
        }
    }

    private static final String ANSWER = """
            {"summary": "Fonların çoğu artıda kapattı; ortalama değişim {{portfolio.return.1w}}.",
             "fundNotes": [{"code": "AAV", "text": "AAV değer kazandı; haftalık değişim {{fund.AAV.return.1w}}."}],
             "benchmarkNote": ""}""";

    private WriterStep step(LlmClient client) {
        return new WriterStep(client, mapper);
    }

    @Test
    void promptCarriesKeysDescriptionsAndDirectionsButNoValue() {
        FakeLlm llm = FakeLlm.answering(ANSWER);
        BulletinFacts facts = SampleFacts.week();

        step(llm).write(facts);

        LlmRequest request = llm.requests.get(0);
        assertThat(request.system()).contains("Metne hiçbir rakam yazma");
        assertThat(request.user())
                .contains("<<<FACTS", "FACTS>>>")
                .contains("fund.AAV.return.1w", "AAV fonunun haftalık getirisi", "\"UP\"", "\"DOWN\"", "\"FLAT\"")
                .contains("bench.CPI.month")
                .doesNotContain("fund.IJB.return.1w"); // missing facts are not offered
        for (Fact fact : facts.facts()) {
            if (!fact.missing()) {
                assertThat(request.user()).as(fact.key())
                        .doesNotContain(fact.value().toPlainString())
                        .doesNotContain(FactFormatter.format(fact));
            }
        }
    }

    @Test
    void promptListsReadyToCopyPlaceholdersAndEveryFund() {
        FakeLlm llm = FakeLlm.answering(ANSWER);

        step(llm).write(SampleFacts.week());

        String user = llm.requests.get(0).user();
        assertThat(user)
                .contains("- {{portfolio.return.1w}}: İzlenen fonların haftalık ortalama getirisi [UP]")
                .contains("- {{fund.AAV.return.1w}}: AAV fonunun haftalık getirisi (önceki cuma → bu cuma) [UP]")
                .contains("- {{fund.AHU.return.1w}}: AHU fonunun haftalık getirisi (önceki cuma → bu cuma) [DOWN]")
                .contains("- {{fund.AU1.return.1w}}: AU1 fonunun haftalık getirisi (önceki cuma → bu cuma) [FLAT]")
                .contains("- IJB: —")
                .contains("- {{bench.GOLD.return.mom}}: ")
                .contains("Şu fonların her biri için bir not yaz: AAV, AHU, AU1, IJB.")
                .doesNotContain("${");
        // the list offers exactly the keys of the context block, nothing more
        assertThat(PlaceholderRenderer.keys(user)).allSatisfy(key ->
                assertThat(SampleFacts.week().get(key)).as(key).hasValueSatisfying(f -> assertThat(f.missing()).isFalse()));
    }

    @Test
    void asksForJsonWithALowTemperatureAndATokenLimit() {
        FakeLlm llm = FakeLlm.answering(ANSWER);

        step(llm).write(SampleFacts.week());

        LlmRequest request = llm.requests.get(0);
        assertThat(request.jsonMode()).isTrue();
        assertThat(request.temperature()).isEqualTo(WriterStep.TEMPERATURE);
        assertThat(request.maxTokens()).isEqualTo(WriterStep.MAX_TOKENS);
    }

    @Test
    void returnsParsedTextWithProviderModelTokensAndPromptVersion() {
        WriterResult result = step(FakeLlm.answering(ANSWER)).write(SampleFacts.week());

        assertThat(result.output().summary()).startsWith("Fonların çoğu");
        assertThat(result.output().noteFor("AAV")).isPresent();
        assertThat(result.rawText()).isEqualTo(ANSWER);
        assertThat(result.provider()).isEqualTo("ollama");
        assertThat(result.model()).isEqualTo("fake-7b");
        assertThat(result.promptVersion()).isEqualTo("writer.v1");
        assertThat(result.inputTokens()).isEqualTo(900);
        assertThat(result.outputTokens()).isEqualTo(150);
        assertThat(result.durationMs()).isEqualTo(4200);
    }

    @Test
    void rejectionReasonsAreSentBackOnlyOnARetry() {
        FakeLlm llm = FakeLlm.answering(ANSWER);
        WriterStep step = step(llm);

        step.write(SampleFacts.week());
        step.write(llm, SampleFacts.week(), List.of("Metinde rakam var: \"3,2\"", "Bilinmeyen anahtar: fund.XYZ.return.1w"));

        assertThat(llm.requests.get(0).user()).doesNotContain("reddedildi");
        assertThat(llm.requests.get(1).user())
                .contains("Önceki denemen şu nedenlerle reddedildi")
                .contains("- Metinde rakam var: \"3,2\"\n- Bilinmeyen anahtar: fund.XYZ.return.1w")
                .contains("<<<FACTS");
    }

    @Test
    void anAnswerThatIsNotTheSchemaIsABadResponse() {
        WriterStep step = step(FakeLlm.answering("Bu hafta fonlar iyi gitti."));

        assertThatThrownBy(() -> step.write(SampleFacts.week()))
                .isInstanceOf(LlmException.class)
                .extracting("kind").isEqualTo(LlmException.Kind.BAD_RESPONSE);
    }

    @Test
    void clientFailuresReachThePipelineUnchanged() {
        LlmException timeout = new LlmException(LlmException.Kind.TIMEOUT, "zaman aşımı", null);
        WriterStep step = step(new FakeLlm(r -> {
            throw timeout;
        }));

        assertThatThrownBy(() -> step.write(SampleFacts.week())).isSameAs(timeout);
    }

    @Test
    void theTemplateWriterUnderstandsThePromptAndUsesOnlyOfferedKeys() {
        BulletinFacts facts = SampleFacts.week();
        TemplateLlmClient template = new TemplateLlmClient(mapper);

        WriterResult result = step(template).write(template, facts, List.of());

        assertThat(result.provider()).isEqualTo("template");
        assertThat(result.model()).isEqualTo(TemplateLlmClient.MODEL);
        assertThat(result.output().fundNotes()).extracting(WriterOutput.FundNote::code)
                .containsExactly("AAV", "AHU", "AU1", "IJB");
        assertThat(result.output().benchmarkNote()).isNotEmpty();
        for (String text : result.output().texts()) {
            assertThat(TextRules.withoutAllowedDigitTokens(text, facts.fundCodes())).as(text).doesNotContainPattern("\\d");
            Matcher m = TextRules.PLACEHOLDER.matcher(text);
            while (m.find()) {
                assertThat(facts.get(m.group(1))).as(m.group()).hasValueSatisfying(f -> assertThat(f.missing()).isFalse());
            }
        }
    }
}
