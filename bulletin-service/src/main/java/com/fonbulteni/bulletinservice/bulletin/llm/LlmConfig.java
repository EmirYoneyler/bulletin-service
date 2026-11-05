package com.fonbulteni.bulletinservice.bulletin.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;

/**
 * Picks the writer with {@code llm.provider}: {@code template} (default, no key), {@code ollama} or
 * {@code openai-compatible}. The template writer is always a bean of its own: it is the fallback.
 */
@Configuration(proxyBeanMethods = false)
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public TemplateLlmClient templateLlmClient(ObjectMapper mapper) {
        return new TemplateLlmClient(mapper);
    }

    @Bean
    @Primary
    public LlmClient llmClient(LlmProperties properties, TemplateLlmClient template, RestClient.Builder builder,
                               ObjectMapper mapper) {
        if (properties.provider() == LlmProperties.Provider.TEMPLATE) {
            log.info("LLM provider: template (no model, no key)");
            return template;
        }
        String name = properties.provider().name().toLowerCase(Locale.ROOT).replace('_', '-');
        if (properties.effectiveBaseUrl() == null || properties.effectiveModel() == null) {
            throw new IllegalStateException("llm.provider=" + name
                    + " için LLM_BASE_URL ve LLM_MODEL tanımlı olmalı (örnekler .env.example dosyasında).");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.effectiveTimeout());
        RestClient restClient = builder.clone()
                .baseUrl(properties.effectiveBaseUrl())
                // A Content-Length instead of a streamed body: some proxies reject chunked requests (ADR 001).
                .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
                .build();
        log.info("LLM provider: {} (model {}, {}, timeout {} s, API key {})", name, properties.effectiveModel(),
                properties.effectiveBaseUrl(), properties.effectiveTimeout().toSeconds(),
                properties.hasApiKey() ? "set" : "not set");
        return new OpenAiCompatibleLlmClient(restClient, mapper, name, properties);
    }
}
