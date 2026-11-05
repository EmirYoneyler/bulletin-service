package com.fonbulteni.bulletinservice.analytics;

import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.entity.FundPrice;
import com.fonbulteni.bulletinservice.repository.FundPriceRepository;
import com.fonbulteni.bulletinservice.repository.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** End to end over the real fund_price table; expected values worked out by hand. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FundStatsServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final LocalDate FRI = LocalDate.of(2026, 11, 6);

    @Autowired
    FundStatsService service;

    @Autowired
    FundRepository funds;

    @Autowired
    FundPriceRepository prices;

    @Autowired
    JdbcTemplate jdbc;

    private Fund fund;

    @BeforeEach
    void seed() {
        jdbc.execute("truncate fund_price, funds restart identity cascade");
        fund = funds.save(Fund.builder().name("Test Fonu").tefasCode("TST").build());
    }

    private void price(String date, String value) {
        prices.upsert(fund.getId(), LocalDate.parse(date), new BigDecimal(value), null, null,
                FundPrice.Source.TEFAS.name(), Instant.parse("2026-11-06T16:30:00Z"));
    }

    @Test
    void computesEveryPeriodFromStoredPrices() {
        price("2025-11-06", "40");   // 1y start (last close on or before 6 Nov 2025)
        price("2025-12-31", "80");   // YTD start
        price("2026-10-30", "100");  // MTD and week start
        price("2026-11-06", "103");  // as of
        price("2026-11-09", "999");  // after asOf: must be ignored

        FundStats stats = service.compute(fund, FRI);

        assertThat(stats.tefasCode()).isEqualTo("TST");
        assertThat(stats.lastPriceDate()).isEqualTo(FRI);
        assertThat(stats.lastPrice()).isEqualByComparingTo("103");
        assertThat(stats.return1w()).isEqualByComparingTo("0.03");     // 103/100 − 1
        assertThat(stats.returnMtd()).isEqualByComparingTo("0.03");    // 103/100 − 1
        assertThat(stats.returnYtd()).isEqualByComparingTo("0.2875");  // 103/80 − 1
        assertThat(stats.return1y()).isEqualByComparingTo("1.575");    // 103/40 − 1
        assertThat(stats.volatility63d()).isNull();                    // only 4 closes, needs 64
        assertThat(stats.maxDrawdown1y()).isEqualByComparingTo("0");   // 80 → 100 → 103 never fell
    }

    @Test
    void fundWithoutPricesHasOnlyNulls() {
        FundStats stats = service.compute(fund, FRI);

        assertThat(stats.lastPriceDate()).isNull();
        assertThat(stats.lastPrice()).isNull();
        assertThat(stats.return1w()).isNull();
        assertThat(stats.returnMtd()).isNull();
        assertThat(stats.returnYtd()).isNull();
        assertThat(stats.return1y()).isNull();
        assertThat(stats.volatility63d()).isNull();
        assertThat(stats.maxDrawdown1y()).isNull();
    }

    @Test
    void oneYearStartFoundWithinTheGapAllowanceBeforeTheWindow() {
        // Long holiday: the last close before the 1y period (starts 7 Nov 2025) is 24 Oct 2025, 14 days earlier.
        price("2025-10-24", "50");
        price("2026-11-06", "60");

        assertThat(service.compute(fund, FRI).return1y()).isEqualByComparingTo("0.2"); // 60/50 − 1
    }
}
