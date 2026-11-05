package com.fonbulteni.bulletinservice.scheduler;

import com.fonbulteni.bulletinservice.common.InvalidRequestException;
import com.fonbulteni.bulletinservice.entity.IngestRun;
import com.fonbulteni.bulletinservice.ingest.tefas.TefasPriceIngestService;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Locking behaviour against a real shedlock table; the ingest work itself is mocked. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class DailyPriceJobTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final LocalDate DAY = LocalDate.of(2026, 11, 4);

    @MockitoBean
    TefasPriceIngestService ingestService;

    @Autowired
    DailyPriceJob job;

    @Autowired
    LockProvider lockProvider;

    private static IngestRun finishedRun(IngestRun.Trigger trigger) {
        IngestRun run = IngestRun.started(IngestRun.Source.TEFAS, trigger, DAY, DAY, 0, Instant.now());
        run.finish(0, 0, 0, null, Instant.now());
        return run;
    }

    private SimpleLock holdLockElsewhere() {
        return lockProvider.lock(new LockConfiguration(Instant.now(), DailyPriceJob.LOCK_NAME,
                Duration.ofMinutes(5), Duration.ZERO)).orElseThrow();
    }

    @Test
    void manualRunExecutesWhenLockIsFree() {
        given(ingestService.ingestRecent(IngestRun.Trigger.MANUAL)).willReturn(finishedRun(IngestRun.Trigger.MANUAL));

        assertThat(job.run(IngestRun.Trigger.MANUAL)).get()
                .extracting(IngestRun::getTrigger).isEqualTo(IngestRun.Trigger.MANUAL);
    }

    @Test
    void scheduledRunUsesScheduledTrigger() {
        given(ingestService.ingestRecent(IngestRun.Trigger.SCHEDULED))
                .willReturn(finishedRun(IngestRun.Trigger.SCHEDULED));

        job.scheduledRun();

        verify(ingestService).ingestRecent(IngestRun.Trigger.SCHEDULED);
    }

    @Test
    void everyPathIsSkippedWhileAnotherIngestHoldsTheLock() {
        SimpleLock held = holdLockElsewhere();
        try {
            assertThat(job.run(IngestRun.Trigger.MANUAL)).isEmpty();
            assertThat(job.backfill("AAV", DAY.minusDays(7), DAY)).isEmpty();
            job.scheduledRun(); // logs and returns
        } finally {
            held.unlock();
        }
        verifyNoInteractions(ingestService);
    }

    @Test
    void failureInsideTheLockPropagatesAndReleasesTheLock() {
        given(ingestService.backfill("AAV", DAY, DAY.minusDays(1)))
                .willThrow(new InvalidRequestException("INVALID_RANGE", "geçersiz"));

        assertThatThrownBy(() -> job.backfill("AAV", DAY, DAY.minusDays(1)))
                .isInstanceOf(InvalidRequestException.class);

        given(ingestService.ingestRecent(any())).willReturn(finishedRun(IngestRun.Trigger.MANUAL));
        assertThat(job.run(IngestRun.Trigger.MANUAL)).isPresent();
        verify(ingestService, never()).ingestRecent(IngestRun.Trigger.SCHEDULED);
    }
}
