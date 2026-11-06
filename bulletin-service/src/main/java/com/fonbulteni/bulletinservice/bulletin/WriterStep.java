package com.fonbulteni.bulletinservice.bulletin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fonbulteni.bulletinservice.analytics.facts.BulletinFacts;
import com.fonbulteni.bulletinservice.bulletin.llm.LlmClient;
import com.fonbulteni.bulletinservice.bulletin.llm.LlmException;
import com.fonbulteni.bulletinservice.bulletin.llm.LlmRequest;
import com.fonbulteni.bulletinservice.bulletin.llm.LlmResponse;
import com.fonbulteni.bulletinservice.bulletin.llm.WriterContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * First step of the bulletin pipeline (AGENT_PROMPT §5, §4.2): facts → prompt → {@link LlmClient} →
 * {@link WriterOutput} with placeholders.
 *
 * <p>The prompt carries only {@link WriterContext}: keys, Turkish descriptions and directions. No values,
 * no e-mail, no name, no unit counts (§6.1, §6.10). One call is one attempt; retries, guards and the
 * template fallback belong to the pipeline (E4-3), which passes the client to use and the reasons the
 * previous attempt was rejected.
 */
@Component
public class WriterStep {

    public static final String PROMPT = "prompts/writer.v1.md";
    static final int MAX_TOKENS = 1200;
    static final double TEMPERATURE = 0.3;

    private final LlmClient defaultClient;
    private final ObjectMapper mapper;
    private final PromptTemplate prompt;

    @Autowired
    public WriterStep(LlmClient llmClient, ObjectMapper mapper) {
        this(llmClient, mapper, PromptTemplate.load(PROMPT));
    }

    WriterStep(LlmClient llmClient, ObjectMapper mapper, PromptTemplate prompt) {
        this.defaultClient = llmClient;
        this.mapper = mapper;
        this.prompt = prompt;
        prompt.section("system"); // fail at startup, not on Monday morning, if the file is incomplete
        prompt.section("user");
        prompt.section("feedback");
    }

    public String promptVersion() {
        return prompt.version();
    }

    /** One attempt with the configured provider. */
    public WriterResult write(BulletinFacts facts) {
        return write(defaultClient, facts, List.of());
    }

    /**
     * One attempt with the given client.
     *
     * @param feedback why the previous attempt was rejected (Turkish, one reason per item); empty on the
     *                 first attempt
     * @throws LlmException the call failed or the answer is not the expected JSON
     *                      ({@link LlmException.Kind#BAD_RESPONSE})
     */
    public WriterResult write(LlmClient client, BulletinFacts facts, List<String> feedback) {
        LlmRequest request = new LlmRequest(prompt.section("system"), userMessage(facts, feedback), true,
                MAX_TOKENS, TEMPERATURE);
        LlmResponse response = client.complete(request);
        WriterOutput output = WriterOutputParser.parse(response.text(), facts.fundCodes(), mapper);
        return new WriterResult(output, response.text(), client.name(), response.model(), prompt.version(),
                response.inputTokens(), response.outputTokens(), response.durationMs());
    }

    String userMessage(BulletinFacts facts, List<String> feedback) {
        String feedbackBlock = feedback.isEmpty() ? "" : "\n" + prompt.render("feedback", Map.of(
                "violations", feedback.stream().map(f -> "- " + f).collect(Collectors.joining("\n"))));
        return prompt.render("user", Map.of(
                "facts", WriterContext.from(facts).embed(mapper),
                "feedback", feedbackBlock));
    }
}
