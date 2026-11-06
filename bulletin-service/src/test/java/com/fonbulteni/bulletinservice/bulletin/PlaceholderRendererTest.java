package com.fonbulteni.bulletinservice.bulletin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fonbulteni.bulletinservice.analytics.facts.BulletinFacts;
import com.fonbulteni.bulletinservice.analytics.facts.Fact;
import com.fonbulteni.bulletinservice.analytics.facts.FactFormatter;
import com.fonbulteni.bulletinservice.bulletin.WriterOutput.FundNote;
import com.fonbulteni.bulletinservice.bulletin.llm.TemplateLlmClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceholderRendererTest {

    private final BulletinFacts facts = SampleFacts.week();

    @Test
    void replacesKeysWithTurkishFormattedValues() {
        String text = "AAV haftayı artıda kapattı; haftalık değişim {{fund.AAV.return.1w}} oldu. "
                + "AHU ekside: {{fund.AHU.return.1w}}. Ay başından beri {{fund.AHU.return.mtd}}.";

        assertThat(PlaceholderRenderer.render(text, facts)).isEqualTo(
                "AAV haftayı artıda kapattı; haftalık değişim %3,2 oldu. "
                        + "AHU ekside: %-0,9. Ay başından beri %1,3.");
    }

    @Test
    void usesTheSameFormatterAsTheTables() {
        for (Fact fact : facts.facts()) {
            assertThat(PlaceholderRenderer.render("{{" + fact.key() + "}}", facts)).isEqualTo(FactFormatter.format(fact));
        }
    }

    @Test
    void liraAndIndexUnitsKeepTheirFormat() {
        LocalDate friday = SampleFacts.FRIDAY;
        BulletinFacts withValue = new BulletinFacts(friday.minusDays(4), friday, List.of(
                Fact.of("portfolio.value.total", new BigDecimal("1250.5"), Fact.Unit.TRY, null, friday,
                        Fact.Source.COMPUTED, "test"),
                Fact.of("bench.BIST100.level", new BigDecimal("10234.567"), Fact.Unit.INDEX, null, friday,
                        Fact.Source.EVDS, "test")),
                List.of(), List.of(), null, null, false, List.of(), List.of());

        assertThat(PlaceholderRenderer.render("Değer {{portfolio.value.total}}, endeks {{bench.BIST100.level}}.", withValue))
                .isEqualTo("Değer 1.250,50 TL, endeks 10.234,57.");
    }

    @Test
    void aMissingValueReadsVeriYokLikeTheTables() {
        assertThat(PlaceholderRenderer.render("IJB: {{fund.IJB.return.1w}}", facts)).isEqualTo("IJB: veri yok");
    }

    @Test
    void toleratesSpacesInsideTheBraces() {
        assertThat(PlaceholderRenderer.render("{{ fund.AAV.return.1w }} ve {{bench.CPI.month }}", facts))
                .isEqualTo("%3,2 ve %2,6");
    }

    @Test
    void anUnknownKeyFailsInsteadOfLeakingThePlaceholder() {
        assertThatThrownBy(() -> PlaceholderRenderer.render(
                "{{fund.AAV.return.1w}} ve {{fund.XYZ.return.1w}} ve {{portfolio.value.1w}}", facts))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[fund.XYZ.return.1w, portfolio.value.1w]");
    }

    @Test
    void textWithoutPlaceholdersIsUnchanged() {
        String text = "Bu hafta fonların bir kısmı yükselirken bir kısmı geriledi. Kıvırcık {parantez} kalır.";

        assertThat(PlaceholderRenderer.render(text, facts)).isEqualTo(text);
        assertThat(PlaceholderRenderer.keys(text)).isEmpty();
    }

    @Test
    void keysAreListedOnceInOrderOfAppearance() {
        assertThat(PlaceholderRenderer.keys("{{b.x}} {{ a.y }} {{b.x}}")).containsExactly("b.x", "a.y");
    }

    @Test
    void rendersEveryPartOfAWriterOutput() {
        WriterOutput output = new WriterOutput("Ortalama {{portfolio.return.1w}}.",
                List.of(new FundNote("AU1", "AU1 yatay: {{fund.AU1.return.1w}}.")),
                "Dolar/TL {{bench.USDTRY.return.1w}}, BIST 100 {{bench.BIST100.return.1w}}.");

        WriterOutput rendered = PlaceholderRenderer.render(output, facts);

        assertThat(rendered.summary()).isEqualTo("Ortalama %0,8.");
        assertThat(rendered.noteFor("AU1")).contains("AU1 yatay: %0,0.");
        assertThat(rendered.benchmarkNote()).isEqualTo("Dolar/TL %0,4, BIST 100 %-2,5.");
    }

    @Test
    void templateWriterTextRendersCompletely() {
        ObjectMapper mapper = new ObjectMapper();
        TemplateLlmClient template = new TemplateLlmClient(mapper);
        WriterOutput written = new WriterStep(template, mapper).write(facts).output();

        WriterOutput rendered = PlaceholderRenderer.render(written, facts);

        assertThat(rendered.texts()).noneMatch(t -> t.contains("{{") || t.contains("}}") || t.contains("veri yok"));
        assertThat(String.join(" ", rendered.texts())).contains("%3,2", "%-0,9", "%0,8", "%-2,5");
        assertThat(rendered.noteFor("AAV")).hasValueSatisfying(t -> assertThat(t).contains("%3,2", "%5,1", "%41,0", "%6,3"));
    }
}
