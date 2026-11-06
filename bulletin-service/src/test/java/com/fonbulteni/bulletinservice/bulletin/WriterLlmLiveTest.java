package com.fonbulteni.bulletinservice.bulletin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fonbulteni.bulletinservice.analytics.facts.BulletinFacts;
import com.fonbulteni.bulletinservice.bulletin.llm.LlmClient;
import com.fonbulteni.bulletinservice.bulletin.llm.LlmConfig;
import com.fonbulteni.bulletinservice.bulletin.llm.LlmProperties;
import com.fonbulteni.bulletinservice.bulletin.llm.TemplateLlmClient;
import com.fonbulteni.bulletinservice.bulletin.llm.TextRules;
import com.fonbulteni.bulletinservice.bulletin.llm.WriterContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * One real writer call against an OpenAI-compatible server, by default a local Ollama
 * ({@code docker compose --profile llm up -d}, then {@code ollama pull qwen2.5:7b-instruct}).
 * Runs only with {@code ./mvnw test -Plive -Dtest=WriterLlmLiveTest} and is skipped when the server does
 * not answer. Settings: {@code LLM_PROVIDER}, {@code LLM_BASE_URL}, {@code LLM_MODEL}, {@code LLM_API_KEY},
 * {@code LLM_TIMEOUT_SECONDS} (default 300: CPU inference is slow).
 *
 * <p>Input is {@link SampleFacts}: synthetic test values, not market data. The model sees only keys and
 * directions anyway. The test asserts that the call works and the answer has the writer schema; digit
 * leaks, unknown keys and advice words are reported, not asserted, because NumberGuard (E4-1) and the
 * pipeline's retries handle them. The report goes to {@code target/live/}.
 */
@Tag("live")
@EnabledIfSystemProperty(named = "live", matches = "true")
class WriterLlmLiveTest {

    private static final Pattern ADVICE = Pattern.compile(
            "(?iu)tavsiye|öner|kesin|garanti|kaçırma|fırsat|hedef fiyat|beklen|yükselecek|düşecek|almalı|satmalı");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void realModelWritesTheWriterSchema() throws Exception {
        LlmProperties properties = new LlmProperties(
                LlmProperties.Provider.valueOf(env("LLM_PROVIDER", "ollama").toUpperCase(Locale.ROOT).replace('-', '_')),
                env("LLM_BASE_URL", "http://localhost:11434/v1"),
                env("LLM_MODEL", "qwen2.5:7b-instruct"),
                env("LLM_API_KEY", ""),
                Duration.ofSeconds(Long.parseLong(env("LLM_TIMEOUT_SECONDS", "300"))),
                true);
        assumeTrue(reachable(properties.effectiveBaseUrl() + "/models", properties.apiKey()),
                "LLM server not reachable at " + properties.effectiveBaseUrl());
        TemplateLlmClient template = new TemplateLlmClient(mapper);
        LlmClient client = new LlmConfig().llmClient(properties, template, RestClient.builder(), mapper);
        BulletinFacts facts = SampleFacts.week();
        WriterStep step = new WriterStep(client, mapper);

        WriterResult result = step.write(client, facts, List.of());

        assertThat(result.output().summary()).isNotBlank();
        Path report = writeReport(result, facts);
        System.out.println("Writer live report: " + report.toAbsolutePath());
    }

    private Path writeReport(WriterResult result, BulletinFacts facts) throws Exception {
        WriterContext context = WriterContext.from(facts);
        Set<String> offered = new HashSet<>();
        context.funds().forEach(f -> f.facts().forEach(r -> offered.add(r.key())));
        context.portfolio().forEach(r -> offered.add(r.key()));
        context.benchmarks().forEach(r -> offered.add(r.key()));

        List<String> findings = new ArrayList<>();
        for (String text : result.output().texts()) {
            Matcher digits = Pattern.compile("\\S*\\d\\S*")
                    .matcher(TextRules.withoutAllowedDigitTokens(text, facts.fundCodes()));
            while (digits.find()) {
                findings.add("Rakam: `" + digits.group() + "`");
            }
            PlaceholderRenderer.keys(text).stream().filter(k -> !offered.contains(k))
                    .forEach(k -> findings.add("Verilmeyen anahtar: `" + k + "`"));
            Matcher advice = ADVICE.matcher(text);
            while (advice.find()) {
                findings.add("Tavsiye/tahmin kökü: `" + advice.group() + "`");
            }
        }
        List<String> missingNotes = facts.fundCodes().stream()
                .filter(code -> result.output().noteFor(code).isEmpty()).toList();
        if (!missingNotes.isEmpty()) {
            findings.add("Notu olmayan fon: " + missingNotes);
        }

        String rendered;
        boolean renderable = result.output().texts().stream()
                .flatMap(t -> PlaceholderRenderer.keys(t).stream()).allMatch(k -> facts.get(k).isPresent());
        if (renderable) {
            WriterOutput out = PlaceholderRenderer.render(result.output(), facts);
            StringBuilder sb = new StringBuilder(out.summary()).append("\n\n");
            out.fundNotes().forEach(n -> sb.append("- **").append(n.code()).append("**: ").append(n.text()).append('\n'));
            if (!out.benchmarkNote().isEmpty()) {
                sb.append('\n').append(out.benchmarkNote()).append('\n');
            }
            rendered = sb.toString();
        } else {
            rendered = "_Bilinmeyen anahtar olduğu için render edilmedi._\n";
        }

        String report = String.join("\n",
                "- Sağlayıcı: `" + result.provider() + "`, model: `" + result.model() + "`",
                "- İstem: `" + result.promptVersion() + "`",
                "- Süre: " + result.durationMs() + " ms, token: " + result.inputTokens() + " girdi / "
                        + result.outputTokens() + " çıktı",
                "",
                "### Ham çıktı (yer tutuculu)",
                "",
                "```json",
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(rawJson(result.rawText()))),
                "```",
                "",
                "### Render sonrası (sentetik değerlerle)",
                "",
                rendered,
                "### Kontroller (bilgi amaçlı)",
                "",
                findings.isEmpty() ? "- Bulgu yok" : String.join("\n", findings.stream().map(f -> "- " + f).toList()),
                "");
        Path dir = Path.of("target", "live");
        Files.createDirectories(dir);
        Path file = dir.resolve("writer-" + result.provider() + "-" + result.model().replaceAll("[^A-Za-z0-9.]", "_") + ".md");
        Files.writeString(file, report, StandardCharsets.UTF_8);
        return file;
    }

    private static String rawJson(String raw) {
        return raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1);
    }

    private static boolean reachable(String url, String apiKey) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET();
            if (apiKey != null && !apiKey.isBlank()) {
                request.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<Void> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
                    .send(request.build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
