package com.fonbulteni.bulletinservice.bulletin;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTemplateTest {

    private static final String SAMPLE = """
            ---
            version: sample.v3
            ---

            Notes for humans, not sent.

            # system

            Role text.

            ## Rules

            Write {{fund.AAV.return.1w}} as is.

            # user

            Context: ${facts}
            ${feedback}
            """;

    @Test
    void writerPromptFileHasVersionAndTheThreeSections() {
        PromptTemplate prompt = PromptTemplate.load(WriterStep.PROMPT);

        assertThat(prompt.version()).isEqualTo("writer.v1");
        assertThat(prompt.sections()).containsOnlyKeys("system", "user", "feedback");
        // the change notes in the preamble are for people, not for the model
        assertThat(prompt.sections().values()).noneMatch(s -> s.contains("Değişiklik notu"));
    }

    @Test
    void writerSystemPromptStatesTheRulesAndTheOutputSchema() {
        String system = PromptTemplate.load(WriterStep.PROMPT).section("system");

        assertThat(system)
                .contains("Metne hiçbir rakam yazma")
                .contains("Yalnızca bağlamda verilen anahtarları kullan")
                .contains("tavsiye")
                .contains("Gelecek hakkında cümle kurma")
                .contains("<<<FACTS")
                .contains("\"summary\"", "\"fundNotes\"", "\"benchmarkNote\"");
        assertThat(system).doesNotContain("${");
    }

    @Test
    void sectionsKeepDeeperHeadingsAndSkipThePreamble() {
        PromptTemplate prompt = PromptTemplate.parse("sample.md", SAMPLE);

        assertThat(prompt.version()).isEqualTo("sample.v3");
        assertThat(prompt.section("system")).isEqualTo("Role text.\n\n## Rules\n\nWrite {{fund.AAV.return.1w}} as is.");
        assertThat(prompt.sections().values()).noneMatch(s -> s.contains("Notes for humans"));
    }

    @Test
    void windowsLineEndingsAreAccepted() {
        PromptTemplate prompt = PromptTemplate.parse("sample.md", SAMPLE.replace("\n", "\r\n"));

        assertThat(prompt.section("user")).isEqualTo("Context: ${facts}\n${feedback}");
    }

    @Test
    void renderInsertsValuesLiterallyAndLeavesFactPlaceholdersAlone() {
        PromptTemplate prompt = PromptTemplate.parse("sample.md", SAMPLE);

        String user = prompt.render("user", Map.of("facts", "a $1 \\n {{x}}", "feedback", ""));

        assertThat(user).isEqualTo("Context: a $1 \\n {{x}}");
        assertThat(prompt.render("system", Map.of())).contains("{{fund.AAV.return.1w}}");
    }

    @Test
    void aVariableWithoutValueFailsInsteadOfReachingTheModel() {
        PromptTemplate prompt = PromptTemplate.parse("sample.md", SAMPLE);

        assertThatThrownBy(() -> prompt.render("user", Map.of("facts", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("${feedback}");
    }

    @Test
    void unknownSectionIsAnError() {
        PromptTemplate prompt = PromptTemplate.parse("sample.md", SAMPLE);

        assertThatThrownBy(() -> prompt.section("critic")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedFilesAreRejected() {
        assertThatThrownBy(() -> PromptTemplate.parse("a.md", "# system\nx\n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("front matter");
        assertThatThrownBy(() -> PromptTemplate.parse("b.md", "---\nname: x\n---\n# system\nx\n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> PromptTemplate.parse("c.md", "---\nversion: c.v1\n---\nno sections\n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no sections");
        assertThatThrownBy(() -> PromptTemplate.parse("d.md", "---\nversion: d.v1\n---\n# user\na\n# user\nb\n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("twice");
    }

    @Test
    void missingFileIsReported() {
        assertThatThrownBy(() -> PromptTemplate.load("prompts/does-not-exist.md"))
                .hasMessageContaining("prompts/does-not-exist.md");
    }
}
