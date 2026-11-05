package com.fonbulteni.bulletinservice.ingest.tefas;

import com.fonbulteni.bulletinservice.common.TimeConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Talks to the real TEFAS service. Excluded from normal builds and CI; run with
 * {@code ./mvnw test -Plive}. Two requests in total, with the production politeness settings.
 */
@Tag("live")
@EnabledIfSystemProperty(named = "live", matches = "true")
class TefasClientLiveTest {

    private final TefasClient client = new TefasClientConfig().tefasClient(
            RestClient.builder(),
            new Binder(new MapConfigurationPropertySource()).bindOrCreate("tefas", TefasProperties.class));

    private final LocalDate today = LocalDate.now(TimeConfig.ISTANBUL);

    @Test
    void securitiesFundHasRecentPrices() {
        List<TefasPriceDto> prices = client.fetchHistory("AAV", today.minusDays(14), today);

        assertRecentPrices(prices, "AAV");
    }

    @Test
    void pensionFundNeedsItsOwnFundType() {
        List<TefasPriceDto> prices = client.fetchHistory("AAJ", TefasFundType.EMK, today.minusDays(14), today);

        assertRecentPrices(prices, "AAJ");
    }

    private void assertRecentPrices(List<TefasPriceDto> prices, String code) {
        assertThat(prices).isNotEmpty();
        assertThat(prices).allSatisfy(price -> {
            assertThat(price.fundCode()).isEqualTo(code);
            assertThat(price.price()).isGreaterThan(BigDecimal.ZERO);
            assertThat(price.date()).isBetween(today.minusDays(14), today);
        });
        assertThat(prices).extracting(TefasPriceDto::date).isSorted();
    }
}
