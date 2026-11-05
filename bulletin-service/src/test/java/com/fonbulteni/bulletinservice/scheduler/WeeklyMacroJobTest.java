package com.fonbulteni.bulletinservice.scheduler;

import com.fonbulteni.bulletinservice.entity.IngestRun;
import com.fonbulteni.bulletinservice.ingest.evds.MacroIngestService;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class WeeklyMacroJobTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @MockitoBean
    MacroIngestService ingestService;

    @Autowired
    WeeklyMacroJob job;

    @Autowired
    LockProvider lockProvider;

    @Test
    void runsWhenLockIsFree() {
        IngestRun run = IngestRun.started(IngestRun.Source.EVDS, IngestRun.Trigger.MANUAL,
                LocalDate.of(2025, 9, 28), LocalDate.of(2026, 11, 2), 4, Instant.now());
        given(ingestService.ingestRecent(IngestRun.Trigger.MANUAL)).willReturn(run);

        assertThat(job.run(IngestRun.Trigger.MANUAL)).contains(run);
    }

    @Test
    void skippedWhileLockIsHeldAndIndependentOfTheTefasLock() {
        SimpleLock tefas = lockProvider.lock(new LockConfiguration(Instant.now(), DailyPriceJob.LOCK_NAME,
                Duration.ofMinutes(5), Duration.ZERO)).orElseThrow();
        SimpleLock evds = lockProvider.lock(new LockConfiguration(Instant.now(), WeeklyMacroJob.LOCK_NAME,
                Duration.ofMinutes(5), Duration.ZERO)).orElseThrow();
        try {
            assertThat(job.run(IngestRun.Trigger.MANUAL)).isEmpty();
            job.scheduledRun();
        } finally {
            evds.unlock();
        }
        verifyNoInteractions(ingestService);

        // A running TEFAS job does not block EVDS.
        given(ingestService.ingestRecent(IngestRun.Trigger.MANUAL)).willReturn(null);
        try {
            job.run(IngestRun.Trigger.MANUAL);
        } finally {
            tefas.unlock();
        }
        org.mockito.Mockito.verify(ingestService).ingestRecent(IngestRun.Trigger.MANUAL);
    }

    @Test
    void runsMondayAt0700IstanbulBeforeTheBulletin() throws Exception {
        Scheduled scheduled = WeeklyMacroJob.class.getMethod("scheduledRun").getAnnotation(Scheduled.class);
        String configured = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))
                .getProperty("jobs.weekly-macro.cron");

        assertThat(scheduled.zone()).isEqualTo("Europe/Istanbul");
        assertThat(configured).isEqualTo("0 0 7 * * MON");
        assertThat(CronExpression.parse(configured).next(ZonedDateTime.of(2026, 11, 5, 12, 0, 0, 0, ISTANBUL)))
                .isEqualTo(ZonedDateTime.of(2026, 11, 9, 7, 0, 0, 0, ISTANBUL));
    }
}
