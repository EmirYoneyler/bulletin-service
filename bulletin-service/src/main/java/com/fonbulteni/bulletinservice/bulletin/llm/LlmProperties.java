package com.fonbulteni.bulletinservice.bulletin.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * LLM settings ({@code llm.*}, AGENT_PROMPT §4.1). The default provider needs no key at all.
 *
 * @param baseUrl            OpenAI-compatible root, e.g. {@code http://ollama:11434/v1}; a trailing slash is ignored
 * @param apiKey             sent as a Bearer token when set; Ollama needs none
 * @param timeout            whole-request timeout; default 120 s for Ollama (CPU can be slow), 30 s otherwise
 * @param jsonResponseFormat send {@code response_format: json_object}; turn off for providers that reject it
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        @DefaultValue("template") Provider provider,
        String baseUrl,
        String model,
        String apiKey,
        Duration timeout,
        @DefaultValue("true") boolean jsonResponseFormat) {

    public enum Provider { TEMPLATE, OLLAMA, OPENAI_COMPATIBLE }

    static final String OLLAMA_BASE_URL = "http://ollama:11434/v1";
    static final String OLLAMA_MODEL = "qwen2.5:7b-instruct";

    public String effectiveBaseUrl() {
        String url = blank(baseUrl) ? (provider == Provider.OLLAMA ? OLLAMA_BASE_URL : null) : baseUrl.trim();
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String effectiveModel() {
        return blank(model) ? (provider == Provider.OLLAMA ? OLLAMA_MODEL : null) : model.trim();
    }

    public Duration effectiveTimeout() {
        return timeout != null ? timeout : provider == Provider.OLLAMA ? Duration.ofSeconds(120) : Duration.ofSeconds(30);
    }

    public boolean hasApiKey() {
        return !blank(apiKey);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
