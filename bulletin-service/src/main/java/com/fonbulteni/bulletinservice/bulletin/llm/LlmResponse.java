package com.fonbulteni.bulletinservice.bulletin.llm;

/** Generated text plus the cost figures recorded per bulletin run (tokens, duration, model). */
public record LlmResponse(String text, int inputTokens, int outputTokens, long durationMs, String model) {
}
