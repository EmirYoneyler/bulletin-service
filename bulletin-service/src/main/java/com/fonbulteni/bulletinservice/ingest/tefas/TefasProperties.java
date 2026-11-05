package com.fonbulteni.bulletinservice.ingest.tefas;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * TEFAS client settings ({@code tefas.*}). Defaults follow AGENT_PROMPT §6.6 and ADR 001.
 *
 * @param baseUrl         service root; the history call is {@code POST {baseUrl}/fonGnlBlgSiraliGetir}
 * @param userAgent       sent on every request so TEFAS can identify the bot
 * @param requestInterval minimum gap between two consecutive requests
 * @param maxAttempts     attempts per request for transient failures (5xx, 429, I/O)
 * @param initialBackoff  wait after the first failure; doubles on each further failure
 * @param pageSize        rows requested per page ({@code bitSira - basSira + 1})
 */
@ConfigurationProperties(prefix = "tefas")
public record TefasProperties(
        @DefaultValue("https://www.tefas.gov.tr/api/funds") String baseUrl,
        @DefaultValue("fonbulteni-bot/1.0 (+github.com/EmirYoneyler/bulletin-service)") String userAgent,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout,
        @DefaultValue("1500ms") Duration requestInterval,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("2s") Duration initialBackoff,
        @DefaultValue("100") int pageSize) {

    public TefasProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("tefas.max-attempts en az 1 olmalı.");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("tefas.page-size en az 1 olmalı.");
        }
    }
}
