package com.fonbulteni.bulletinservice.ingest.evds;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fonbulteni.bulletinservice.common.TimeConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live check against TCMB EVDS for card E1-5b. Runs only with {@code ./mvnw test -Plive} and a real
 * {@code EVDS_API_KEY} in the environment. One request per series.
 */
@Tag("live")
@EnabledIfSystemProperty(named = "live", matches = "true")
@EnabledIfEnvironmentVariable(named = "EVDS_API_KEY", matches = ".+")
class EvdsClientLiveTest {

    private final EvdsProperties properties = new Binder(new MapConfigurationPropertySource(
            Map.of("evds.api-key", System.getenv("EVDS_API_KEY") == null ? "" : System.getenv("EVDS_API_KEY"))))
            .bindOrCreate("evds", EvdsProperties.class);

    private final EvdsClient client = new EvdsClientConfig().evdsClient(RestClient.builder(), properties, new ObjectMapper());

    private final LocalDate today = LocalDate.now(TimeConfig.ISTANBUL);

    @Test
    void everyConfiguredSeriesReturnsRecentPositiveValues() {
        for (String code : properties.series().all()) {
            List<EvdsObservation> values = client.fetchSeries(code, today.minusDays(120), today);
            assertThat(values).as(code).isNotEmpty();
            assertThat(values).as(code).allSatisfy(v -> assertThat(v.value()).isGreaterThan(BigDecimal.ZERO));
        }
    }
}
