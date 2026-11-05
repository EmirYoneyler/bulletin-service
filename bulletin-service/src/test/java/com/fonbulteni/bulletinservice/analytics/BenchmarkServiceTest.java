package com.fonbulteni.bulletinservice.analytics;

import com.fonbulteni.bulletinservice.repository.MacroPointRepository;
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
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/** Over the real macro_point table. Values are synthetic (no EVDS key yet, E1-5b); expectations by hand. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class BenchmarkServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final LocalDate FRI = LocalDate.of(2026, 11, 6);

    @Autowired
    BenchmarkService service;

    @Autowired
    MacroPointRepository points;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("truncate macro_point restart identity");
    }

    private void point(String series, String date, String value) {
        points.upsert(series, LocalDate.parse(date), new BigDecimal(value), Instant.parse("2026-11-09T04:00:00Z"));
    }

    @Test
    void computesEveryBenchmarkFromStoredSeries() {
        point("TP.DK.USD.A.YTL", "2025-12-31", "8.00");
        point("TP.DK.USD.A.YTL", "2026-10-30", "10.00");
        point("TP.DK.USD.A.YTL", "2026-11-06", "10.50");
        point("TP.MK.G.BILESIK", "2026-10-30", "40000");
        point("TP.MK.G.BILESIK", "2026-11-06", "39000");
        point("TP.TRY.MT06", "2026-10-30", "45.00");          // percent, weekly (Friday)
        point("TP.MK.KUL.YTL", "2026-09-01", "1000");          // monthly gold
        point("TP.MK.KUL.YTL", "2026-10-01", "1100");
        point("TP.TUKFIY2025.GENEL", "2025-12-01", "170");     // monthly CPI index
        point("TP.TUKFIY2025.GENEL", "2026-09-01", "200");
        point("TP.TUKFIY2025.GENEL", "2026-10-01", "204");

        Benchmarks b = service.benchmarks(FRI);

        assertThat(b.usdTry().week()).isEqualByComparingTo("0.05");            // 10.5/10 − 1
        assertThat(b.usdTry().monthToDate()).isEqualByComparingTo("0.05");
        assertThat(b.usdTry().yearToDate()).isEqualByComparingTo("0.3125");    // 10.5/8 − 1
        assertThat(b.usdTry().oneYear()).isNull();                             // no close a year ago
        assertThat(b.bist100().week()).isEqualByComparingTo("-0.025");         // 39000/40000 − 1
        assertThat(b.depositRate()).isEqualByComparingTo("0.45");
        assertThat(b.depositRateDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(b.deposit().oneYear()).isEqualByComparingTo("0.45");        // full year at 45 %
        assertThat(b.gold().month()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(b.gold().monthOverMonth()).isEqualByComparingTo("0.1");     // 1100/1000 − 1
        assertThat(b.inflation().note()).isEqualTo("Ekim 2026 TÜFE'si kullanıldı.");
        assertThat(b.inflation().monthlyRate()).isEqualByComparingTo("0.02"); // 204/200 − 1
        assertThat(b.inflation().periodInflation().yearToDate()).isEqualByComparingTo("0.2"); // 204/170 − 1
        // USD year to date in real terms: 1.3125 / 1.2 − 1 = 0.09375
        assertThat(b.inflation().realOf(b.usdTry()).yearToDate()).isEqualByComparingTo("0.09375");
    }

    @Test
    void staleDepositRateIsNotUsed() {
        point("TP.TRY.MT06", "2026-10-02", "45.00"); // 35 days before asOf

        Benchmarks b = service.benchmarks(FRI);

        assertThat(b.depositRate()).isNull();
        assertThat(b.deposit()).isEqualTo(PeriodValues.EMPTY);
    }

    @Test
    void withoutEvdsDataEverythingIsEmpty() {
        Benchmarks b = service.benchmarks(FRI);

        assertThat(b.usdTry()).isEqualTo(PeriodValues.EMPTY);
        assertThat(b.bist100()).isEqualTo(PeriodValues.EMPTY);
        assertThat(b.deposit()).isEqualTo(PeriodValues.EMPTY);
        assertThat(b.gold()).isEqualTo(MonthlyChange.EMPTY);
        assertThat(b.inflation()).isEqualTo(Inflation.EMPTY);
    }
}
