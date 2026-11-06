package com.fonbulteni.bulletinservice.bulletin;

/**
 * One writer call: the parsed text plus what a run record needs (AGENT_PROMPT §5.1 {@code bulletin},
 * {@code bulletin_run}).
 *
 * @param rawText       the model's answer as received, for {@code bulletin.llm_text}
 * @param provider      {@code LlmClient.name()}: template, ollama or openai-compatible
 * @param promptVersion the {@code version} of the prompt file, for {@code bulletin.prompt_version}
 */
public record WriterResult(
        WriterOutput output,
        String rawText,
        String provider,
        String model,
        String promptVersion,
        int inputTokens,
        int outputTokens,
        long durationMs) {
}
