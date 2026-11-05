package com.fonbulteni.bulletinservice.ingest.tefas;

import com.fonbulteni.bulletinservice.common.FundNotFoundException;
import com.fonbulteni.bulletinservice.common.InvalidRequestException;
import com.fonbulteni.bulletinservice.entity.Fund;
import com.fonbulteni.bulletinservice.entity.IngestRun;
import com.fonbulteni.bulletinservice.repository.FundRepository;
import com.fonbulteni.bulletinservice.repository.IngestRunRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class TefasPriceIngestServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Wednesday 4 Nov 2026, 20:00 in Istanbul. */
    static final Instant NOW = Instant.parse("2026-11-04T17:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
    static final LocalDate WINDOW_START = LocalDate.of(2026, 10, 29);

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Europe/Istanbul"));
        }
    }

    @MockitoBean
    TefasClient tefas;

    @Autowired
    TefasPriceIngestService service;

    @Autowired
    FundRepository funds;

    @Autowired
    IngestRunRepository runs;

    @Autowired
    JdbcTemplate jdbc;

    private Fund aav;
    private Fund aaj;

    @BeforeEach
    void seed() {
        jdbc.execute("truncate fund_price, ingest_run, funds restart identity cascade");
        aav = funds.save(Fund.builder().name("Hisse Fonu").tefasCode("AAV").category(Fund.Category.HISSE).build());
        aaj = funds.save(Fund.builder().name("Emeklilik Fonu").tefasCode("AAJ").category(Fund.Category.EMEKLILIK).build());
        funds.save(Fund.builder().name("Elle girilmiş, kodsuz fon").build());
    }

    private static TefasPriceDto price(String code, LocalDate date, String value) {
        return new TefasPriceDto(code, code + " FONU", date, new BigDecimal(value), new BigDecimal("1000"), 10,
                new BigDecimal("123456.78"));
    }

    private void tefasReturnsTwoAavAndOneAajPrice() {
        given(tefas.fetchHistory("AAV", TefasFundType.YAT, WINDOW_START, TODAY)).willReturn(List.of(
                price("AAV", LocalDate.of(2026, 11, 3), "55.176134"),
                price("AAV", LocalDate.of(2026, 11, 4), "55.444077")));
        given(tefas.fetchHistory("AAJ", TefasFundType.EMK, WINDOW_START, TODAY)).willReturn(List.of(
                price("AAJ", LocalDate.of(2026, 11, 4), "0.123456")));
    }

    private int priceRows() {
        return jdbc.queryForObject("select count(*) from fund_price", Integer.class);
    }

    // --- daily run ---

    @Test
    void recentRunCoversLastFiveWeekdaysForEveryCodedFund() {
        tefasReturnsTwoAavAndOneAajPrice();

        IngestRun run = service.ingestRecent(IngestRun.Trigger.SCHEDULED);

        // Pension fund asked with its own type; the fund without a TEFAS code is never requested.
        verify(tefas).fetchHistory("AAV", TefasFundType.YAT, WINDOW_START, TODAY);
        verify(tefas).fetchHistory("AAJ", TefasFundType.EMK, WINDOW_START, TODAY);
        assertThat(run.getStatus()).isEqualTo(IngestRun.Status.SUCCESS);
        assertThat(run.getTrigger()).isEqualTo(IngestRun.Trigger.SCHEDULED);
        assertThat(run.getFundCount()).isEqualTo(2);
        assertThat(run.getRowsWritten()).isEqualTo(3);
        assertThat(run.getRowsUnchanged()).isZero();
        assertThat(run.getRangeFrom()).isEqualTo(WINDOW_START);
        assertThat(run.getRangeTo()).isEqualTo(TODAY);
        assertThat(run.getStartedAt()).isEqualTo(NOW);
        assertThat(run.getFinishedAt()).isEqualTo(NOW);
        assertThat(priceRows()).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select price from fund_price where fund_id = ? and price_date = '2026-11-04'",
                BigDecimal.class, aav.getId())).isEqualByComparingTo("55.444077");
    }

    @Test
    void secondIdenticalRunWritesNothing() {
        tefasReturnsTwoAavAndOneAajPrice();
        service.ingestRecent(IngestRun.Trigger.SCHEDULED);

        IngestRun again = service.ingestRecent(IngestRun.Trigger.MANUAL);

        assertThat(again.getRowsWritten()).isZero();
        assertThat(again.getRowsUnchanged()).isEqualTo(3);
        assertThat(priceRows()).isEqualTo(3);
        assertThat(runs.count()).isEqualTo(2);
    }

    @Test
    void oneFailingFundDoesNotStopTheOthers() {
        given(tefas.fetchHistory("AAV", TefasFundType.YAT, WINDOW_START, TODAY))
                .willThrow(new TefasUnavailableException("TEFAS'a 3 denemede ulaşılamadı (AAV).", null));
        given(tefas.fetchHistory("AAJ", TefasFundType.EMK, WINDOW_START, TODAY))
                .willReturn(List.of(price("AAJ", TODAY, "0.123456")));

        IngestRun run = service.ingestRecent(IngestRun.Trigger.SCHEDULED);

        assertThat(run.getStatus()).isEqualTo(IngestRun.Status.PARTIAL);
        assertThat(run.getFundsFailed()).isEqualTo(1);
        assertThat(run.getRowsWritten()).isEqualTo(1);
        assertThat(run.getErrors()).startsWith("AAV: TEFAS'a 3 denemede ulaşılamadı");
        assertThat(runs.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(IngestRun.Status.PARTIAL);
    }

    @Test
    void unexpectedErrorIsRecordedNotThrown() {
        given(tefas.fetchHistory(eq("AAV"), any(), any(), any())).willThrow(new IllegalStateException("boom"));
        given(tefas.fetchHistory(eq("AAJ"), any(), any(), any())).willThrow(new TefasRequestRejectedException("red"));

        IngestRun run = service.ingestRecent(IngestRun.Trigger.SCHEDULED);

        assertThat(run.getStatus()).isEqualTo(IngestRun.Status.FAILED);
        assertThat(run.getFundsFailed()).isEqualTo(2);
        assertThat(run.getErrors()).contains("AAJ: red").contains("AAV: beklenmeyen hata (IllegalStateException)");
        assertThat(priceRows()).isZero();
    }

    @Test
    void noCodedFundsIsAnEmptySuccess() {
        jdbc.execute("update funds set tefas_code = null");

        IngestRun run = service.ingestRecent(IngestRun.Trigger.SCHEDULED);

        assertThat(run.getStatus()).isEqualTo(IngestRun.Status.SUCCESS);
        assertThat(run.getFundCount()).isZero();
        verifyNoInteractions(tefas);
    }

    // --- backfill ---

    @Test
    void backfillFetchesExactlyTheRequestedRangeForOneFund() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        given(tefas.fetchHistory("AAV", TefasFundType.YAT, from, TODAY))
                .willReturn(List.of(price("AAV", LocalDate.of(2026, 8, 3), "40.000001")));

        IngestRun run = service.backfill("aav", from, TODAY);

        assertThat(run.getTrigger()).isEqualTo(IngestRun.Trigger.BACKFILL);
        assertThat(run.getFundCount()).isEqualTo(1);
        assertThat(run.getRowsWritten()).isEqualTo(1);
        verify(tefas, never()).fetchHistory(eq("AAJ"), any(), any(), any());
    }

    @Test
    void backfillRejectsBadInput() {
        assertThatThrownBy(() -> service.backfill("XYZ", TODAY.minusDays(7), TODAY))
                .isInstanceOf(FundNotFoundException.class);
        assertThatThrownBy(() -> service.backfill("AAV", TODAY, TODAY.minusDays(1)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("bitiş tarihinden sonra");
        assertThatThrownBy(() -> service.backfill("AAV", TODAY, TODAY.plusDays(1)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("bugünden sonra");
        assertThatThrownBy(() -> service.backfill("AAV", TODAY.minusYears(3), TODAY))
                .isInstanceOf(InvalidRequestException.class).extracting("code").isEqualTo("RANGE_TOO_LONG");
        verifyNoInteractions(tefas);
        assertThat(runs.count()).isZero();
    }

    @Test
    void pensionCategoryMapsToEmkEverythingElseToYat() {
        assertThat(TefasPriceIngestService.fundTypeOf(aaj)).isEqualTo(TefasFundType.EMK);
        assertThat(TefasPriceIngestService.fundTypeOf(aav)).isEqualTo(TefasFundType.YAT);
        assertThat(TefasPriceIngestService.fundTypeOf(Fund.builder().name("x").build())).isEqualTo(TefasFundType.YAT);
    }
}
