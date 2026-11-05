package com.fonbulteni.bulletinservice.ingest.evds;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * TCMB EVDS settings ({@code evds.*}, ADR 002). Without {@code apiKey} the client is disabled and the
 * application keeps running; benchmark and real-return sections are then skipped.
 */
@ConfigurationProperties(prefix = "evds")
public record EvdsProperties(
        @DefaultValue("https://evds3.tcmb.gov.tr/igmevdsms-dis") String baseUrl,
        String apiKey,
        @DefaultValue("fonbulteni-bot/1.0 (+github.com/EmirYoneyler/bulletin-service)") String userAgent,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout,
        @DefaultValue Series series) {

    /** EVDS series codes, verified on 2026-11-05 (ADR 002). */
    public record Series(
            @DefaultValue("TP.TUKFIY2025.GENEL") String cpi,
            @DefaultValue("TP.DK.USD.A.YTL") String usdtry,
            @DefaultValue("TP.MK.KUL.YTL") String gold,
            @DefaultValue("TP.TRY.MT06") String deposit) {

        public List<String> all() {
            return List.of(cpi, usdtry, gold, deposit);
        }
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
