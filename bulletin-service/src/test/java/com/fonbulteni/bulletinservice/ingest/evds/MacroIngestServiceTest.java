package com.fonbulteni.bulletinservice.ingest.evds;

import com.fonbulteni.bulletinservice.entity.IngestRun;
import com.fonbulteni.bulletinservice.entity.MacroPoint;
import com.fonbulteni.bulletinservice.repository.IngestRunRepository;
import com.fonbulteni.bulletinservice.repository.MacroPointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Values are synthetic test data (ADR 002: no EVDS key yet). */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MacroIngestServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Monday 2 Nov 2026, 07:00 in Istanbul. */
    static final Instant NOW = Instant.parse("2026-11-02T04:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    static final LocalDate FROM = TODAY.minusDays(MacroIngestService.LOOKBACK_DAYS);

    static final String CPI = "TP.TUKFIY2025.GENEL";
    static final String USD = "TP.DK.USD.A.YTL";
    static final String GOLD = "TP.MK.KUL.YTL";
    static final String DEPOSIT = "TP.TRY.MT06";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Europe/Istanbul"));
        }
    }

    @MockitoBean
    EvdsClient evds;

    @Autowired
    MacroIngestService service;

    @Autowired
    MacroPointRepository points;

    @Autowired
    IngestRunRepository runs;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.execute("truncate macro_point, ingest_run restart identity");
        given(evds.series()).willReturn(new EvdsProperties.Series(CPI, USD, GOLD, DEPOSIT));
    }

    private static EvdsObservation obs(String code, LocalDate date, String value) {
        return new EvdsObservation(code, date, new BigDecimal(value));
    }

    private void evdsReturnsOnePointPerSeries() {
        given(evds.isEnabled()).willReturn(true);
        given(evds.fetchSeries(CPI, FROM, TODAY)).willReturn(List.of(
                obs(CPI, LocalDate.of(2026, 9, 1), "101.00"), obs(CPI, LocalDate.of(2026, 10, 1), "102.01")));
        given(evds.fetchSeries(USD, FROM, TODAY)).willReturn(List.of(obs(USD, LocalDate.of(2026, 10, 30), "10.25")));
        given(evds.fetchSeries(GOLD, FROM, TODAY)).willReturn(List.of(obs(GOLD, LocalDate.of(2026, 10, 1), "1000")));
        given(evds.fetchSeries(DEPOSIT, FROM, TODAY)).willReturn(List.of(obs(DEPOSIT, LocalDate.of(2026, 10, 30), "12.75")));
    }

    @Test
    void withoutKeyTheRunIsRecordedAsSkippedAndNothingIsRequested() {
        given(evds.isEnabled()).willReturn(false);

        IngestRun run = service.ingestRecent(IngestRun.Trigger.SCHEDULED);

        assertThat(run.getStatus()).isEqualTo(IngestRun.Status.SKIPPED);
        assertThat(run.getSource()).isEqualTo(IngestRun.Source.EVDS);
        assertThat(run.getTargetCount()).isEqualTo(4);
        assertThat(run.getErrors()).contains("EVDS_API_KEY");
        assertThat(runs.findById(run.getId())).isPresent();
        verify(evds, never()).fetchSeries(any(), any(), any());
        assertThat(points.count()).isZero();
    }

    @Test
    void storesEverySeriesOverTheLookbackWindow() {
        evdsReturnsOnePointPerSeries();

        IngestRun run = service.ingestRecent(IngestRun.Trigger.SCHEDULED);

        assertThat(run.getStatus()).isEqualTo(IngestRun.Status.SUCCESS);
        assertThat(run.getRangeFrom()).isEqualTo(FROM);
        assertThat(run.getRangeTo()).isEqualTo(TODAY);
        assertThat(run.getRowsWritten()).isEqualTo(5);
        assertThat(points.count()).isEqualTo(5);
        assertThat(points.findBySeriesCodeAndObsDateBetweenOrderByObsDateAsc(CPI, FROM, TODAY))
                .extracting(MacroPoint::getValue)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("101.00"), new BigDecimal("102.01"));
    }

    @Test
    void secondRunWithSameValuesWritesNothing() {
        evdsReturnsOnePointPerSeries();
        service.ingestRecent(IngestRun.Trigger.SCHEDULED);

        IngestRun again = service.ingestRecent(IngestRun.Trigger.MANUAL);

        assertThat(again.getRowsWritten()).isZero();
        assertThat(again.getRowsUnchanged()).isEqualTo(5);
    }

    @Test
    void revisedValueIsUpdatedInPlace() {
        evdsReturnsOnePointPerSeries();
        service.ingestRecent(IngestRun.Trigger.SCHEDULED);
        given(evds.fetchSeries(CPI, FROM, TODAY)).willReturn(List.of(
                obs(CPI, LocalDate.of(2026, 9, 1), "101.00"), obs(CPI, LocalDate.of(2026, 10, 1), "102.05")));

        IngestRun again = service.ingestRecent(IngestRun.Trigger.MANUAL);

        assertThat(again.getRowsWritten()).isEqualTo(1);
        assertThat(points.findFirstBySeriesCodeAndObsDateLessThanEqualOrderByObsDateDesc(CPI, TODAY))
                .get().extracting(MacroPoint::getValue)
                .satisfies(v -> assertThat(v).isEqualByComparingTo("102.05"));
    }

    @Test
    void latestOnOrBeforeGivesTheMonthInForce() {
        evdsReturnsOnePointPerSeries();
        service.ingestRecent(IngestRun.Trigger.SCHEDULED);

        assertThat(points.findFirstBySeriesCodeAndObsDateLessThanEqualOrderByObsDateDesc(CPI, LocalDate.of(2026, 9, 30)))
                .get().extracting(MacroPoint::getObsDate).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(points.findFirstBySeriesCodeAndObsDateLessThanEqualOrderByObsDateDesc(CPI, LocalDate.of(2026, 8, 31)))
                .isEmpty();
    }

    @Test
    void oneFailingSeriesDoesNotStopTheOthers() {
        evdsReturnsOnePointPerSeries();
        given(evds.fetchSeries(eq(GOLD), any(), any()))
                .willThrow(new EvdsException.Unavailable("EVDS'ye ulaşılamadı (" + GOLD + ")", null));

        IngestRun run = service.ingestRecent(IngestRun.Trigger.SCHEDULED);

        assertThat(run.getStatus()).isEqualTo(IngestRun.Status.PARTIAL);
        assertThat(run.getTargetsFailed()).isEqualTo(1);
        assertThat(run.getRowsWritten()).isEqualTo(4);
        assertThat(run.getErrors()).startsWith(GOLD + ": EVDS'ye ulaşılamadı");
    }
}
