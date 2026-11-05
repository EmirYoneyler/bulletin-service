package com.fonbulteni.bulletinservice.bulletin.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * One client for every OpenAI-compatible chat API: Ollama, Google Gemini and Groq (AGENT_PROMPT §4.1).
 * {@code POST {baseUrl}/chat/completions}. Every failure becomes an {@link LlmException}, so the caller
 * can fall back to the template writer. Never logs prompts or the API key.
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String providerName;
    private final String model;
    private final String apiKey;
    private final boolean jsonResponseFormat;
    private final String baseUrl;
    private final Duration timeout;

    public OpenAiCompatibleLlmClient(RestClient restClient, ObjectMapper mapper, String providerName,
                                     LlmProperties properties) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.providerName = providerName;
        this.model = properties.effectiveModel();
        this.apiKey = properties.hasApiKey() ? properties.apiKey().trim() : null;
        this.jsonResponseFormat = properties.jsonResponseFormat();
        this.baseUrl = properties.effectiveBaseUrl();
        this.timeout = properties.effectiveTimeout();
    }

    @Override
    public String name() {
        return providerName;
    }

    public String model() {
        return model;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Duration timeout() {
        return timeout;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long started = System.nanoTime();
        String body = requestBody(request);
        String raw;
        try {
            raw = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (apiKey != null) {
                            h.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                throw new LlmException(LlmException.Kind.RATE_LIMITED,
                        providerName + " hız sınırına takıldı (429).", e);
            }
            throw new LlmException(LlmException.Kind.BAD_REQUEST,
                    providerName + " isteği reddetti: HTTP " + e.getStatusCode().value(), e);
        } catch (HttpServerErrorException e) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE,
                    providerName + " sunucu hatası: HTTP " + e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            boolean timedOut = e.getCause() instanceof SocketTimeoutException || e.getCause() instanceof HttpTimeoutException;
            throw new LlmException(timedOut ? LlmException.Kind.TIMEOUT : LlmException.Kind.UNAVAILABLE,
                    providerName + (timedOut ? " zaman aşımına uğradı (" + timeout.toSeconds() + " sn)." : " erişilemedi."), e);
        }
        return parse(raw, (System.nanoTime() - started) / 1_000_000);
    }

    private String requestBody(LlmRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        ArrayNode messages = body.putArray("messages");
        if (request.system() != null && !request.system().isBlank()) {
            messages.addObject().put("role", "system").put("content", request.system());
        }
        messages.addObject().put("role", "user").put("content", request.user());
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        body.put("stream", false);
        if (request.jsonMode() && jsonResponseFormat) {
            body.putObject("response_format").put("type", "json_object");
        }
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new LlmException(LlmException.Kind.BAD_REQUEST, "İstek gövdesi yazılamadı.", e);
        }
    }

    private LlmResponse parse(String raw, long durationMs) {
        JsonNode root;
        try {
            root = raw == null ? null : mapper.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new LlmException(LlmException.Kind.BAD_RESPONSE, providerName + " JSON olmayan bir yanıt döndürdü.", e);
        }
        JsonNode content = root == null ? null : root.path("choices").path(0).path("message").path("content");
        if (content == null || !content.isTextual() || content.asText().isBlank()) {
            throw new LlmException(LlmException.Kind.BAD_RESPONSE, providerName + " yanıtında metin yok.", null);
        }
        JsonNode usage = root.path("usage");
        String responseModel = root.path("model").asText(model);
        return new LlmResponse(content.asText(), usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0), durationMs, responseModel);
    }
}
