package com.fonbulteni.bulletinservice.bulletin.llm;

/**
 * @param jsonMode    ask the model for a JSON object (OpenAI {@code response_format})
 * @param maxTokens   upper bound on generated tokens
 * @param temperature sampling temperature; low values keep a financial text sober
 */
public record LlmRequest(String system, String user, boolean jsonMode, int maxTokens, double temperature) {

    public LlmRequest {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("user message is required");
        }
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
    }
}
