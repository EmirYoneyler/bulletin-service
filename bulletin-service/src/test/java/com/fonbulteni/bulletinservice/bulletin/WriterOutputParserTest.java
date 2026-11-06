package com.fonbulteni.bulletinservice.bulletin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fonbulteni.bulletinservice.bulletin.WriterOutput.FundNote;
import com.fonbulteni.bulletinservice.bulletin.llm.LlmException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WriterOutputParserTest {

    private static final List<String> FUNDS = List.of("AAV", "AHU", "AU1");
    private static final String VALID = """
            {"summary": "Fonların çoğu artıda; ortalama {{portfolio.return.1w}}.",
             "fundNotes": [{"code": "AAV", "text": "AAV değer kazandı; {{fund.AAV.return.1w}}."},
                           {"code": "AHU", "text": "AHU ekside kapattı."}],
             "benchmarkNote": "Dolar/TL {{bench.USDTRY.return.1w}} değişti."}""";

    private final ObjectMapper mapper = new ObjectMapper();

    private WriterOutput parse(String raw) {
        return WriterOutputParser.parse(raw, FUNDS, mapper);
    }

    private void assertBad(String raw, String messagePart) {
        assertThatThrownBy(() -> parse(raw))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining(messagePart)
                .extracting("kind").isEqualTo(LlmException.Kind.BAD_RESPONSE);
    }

    @Test
    void readsTheWriterSchema() {
        WriterOutput out = parse(VALID);

        assertThat(out.summary()).isEqualTo("Fonların çoğu artıda; ortalama {{portfolio.return.1w}}.");
        assertThat(out.fundNotes()).containsExactly(
                new FundNote("AAV", "AAV değer kazandı; {{fund.AAV.return.1w}}."),
                new FundNote("AHU", "AHU ekside kapattı."));
        assertThat(out.benchmarkNote()).isEqualTo("Dolar/TL {{bench.USDTRY.return.1w}} değişti.");
        assertThat(out.noteFor("AHU")).contains("AHU ekside kapattı.");
        assertThat(out.noteFor("AU1")).isEmpty();
        assertThat(out.texts()).hasSize(4);
    }

    @Test
    void acceptsAMarkdownFenceOrChatterAroundTheObject() {
        assertThat(parse("```json\n" + VALID + "\n```")).isEqualTo(parse(VALID));
        assertThat(parse("İşte bülten:\n" + VALID + "\nUmarım beğenirsin.")).isEqualTo(parse(VALID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Üzgünüm, bunu yazamam.", "", "}{"})
    void anAnswerWithoutJsonIsBad(String raw) {
        assertBad(raw, "JSON değil");
    }

    @Test
    void brokenJsonIsBad() {
        assertBad("{\"summary\": \"yarım", "JSON değil");
        assertBad("{\"summary\": \"x\",}", "JSON bozuk");
        assertBad(null, "boş yanıt");
    }

    @Test
    void summaryIsRequiredText() {
        assertBad("{\"fundNotes\": []}", "'summary'");
        assertBad("{\"summary\": \"  \"}", "'summary'");
        assertBad("{\"summary\": 3.2}", "'summary'");
    }

    @Test
    void aNoteForAFundOutsideTheBulletinIsBad() {
        assertBad("{\"summary\": \"x\", \"fundNotes\": [{\"code\": \"XYZ\", \"text\": \"uydurma\"}]}",
                "bültende olmayan bir fon kodu var: XYZ");
    }

    @Test
    void twoNotesForTheSameFundAreBad() {
        assertBad("{\"summary\": \"x\", \"fundNotes\": [{\"code\": \"AAV\", \"text\": \"a\"},"
                + " {\"code\": \"aav\", \"text\": \"b\"}]}", "aynı fon için iki not var: AAV");
    }

    @Test
    void malformedNotesAreBad() {
        assertBad("{\"summary\": \"x\", \"fundNotes\": {\"AAV\": \"a\"}}", "liste değil");
        assertBad("{\"summary\": \"x\", \"fundNotes\": [{\"code\": \"AAV\"}]}", "'code' ve 'text'");
        assertBad("{\"summary\": \"x\", \"fundNotes\": [\"AAV iyi\"]}", "'code' ve 'text'");
        assertBad("{\"summary\": \"x\", \"benchmarkNote\": [\"a\"]}", "'benchmarkNote'");
    }

    @Test
    void codesAreNormalizedNotesFollowTheBulletinOrderAndBlankNotesAreDropped() {
        WriterOutput out = parse("""
                {"summary": "x", "fundNotes": [
                  {"code": " au1 ", "text": " AU1 yatay. "},
                  {"code": "AHU", "text": "   "},
                  {"code": "AAV", "text": "AAV yükseldi."}]}""");

        assertThat(out.fundNotes()).containsExactly(
                new FundNote("AAV", "AAV yükseldi."),
                new FundNote("AU1", "AU1 yatay."));
    }

    @Test
    void benchmarkNoteAndFundNotesMayBeMissing() {
        WriterOutput out = parse("{\"summary\": \"Veri yok.\", \"benchmarkNote\": null}");

        assertThat(out.fundNotes()).isEmpty();
        assertThat(out.benchmarkNote()).isEmpty();
        assertThat(out.texts()).containsExactly("Veri yok.");
    }

    @Test
    void mapChangesEveryTextAndKeepsTheStructure() {
        WriterOutput out = parse(VALID).map(String::toUpperCase);

        assertThat(out.summary()).startsWith("FONLARIN");
        assertThat(out.fundNotes()).extracting(FundNote::code).containsExactly("AAV", "AHU");
        assertThat(out.benchmarkNote()).startsWith("DOLAR");
    }
}
