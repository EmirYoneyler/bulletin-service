package com.fonbulteni.bulletinservice.bulletin.llm;

/**
 * A text generator behind one interface (AGENT_PROMPT §4.1). Implementations: the always-available
 * {@link TemplateLlmClient} and {@link OpenAiCompatibleLlmClient} (Ollama, Gemini, Groq). No paid
 * provider is a default.
 */
public interface LlmClient {

    /**
     * @throws LlmException on any failure; the pipeline then falls back to the template writer
     */
    LlmResponse complete(LlmRequest request);

    /** Short provider name stored with each run, e.g. {@code template} or {@code ollama}. */
    String name();
}
