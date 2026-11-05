package com.fonbulteni.bulletinservice.scheduler;

import com.fonbulteni.bulletinservice.entity.IngestRun;
import com.fonbulteni.bulletinservice.ingest.tefas.TefasPriceIngestService;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Daily TEFAS price ingestion (AGENT_PROMPT §7.9). Every TEFAS ingestion path (the cron run, the
 * manual admin trigger and the backfill) takes the same cluster-wide lock, so at most one of them
 * talks to TEFAS at a time.
 */
@Component
public class DailyPriceJob {

    public static final String LOCK_NAME = "tefas-ingest";
    /** Safety net if an instance dies while holding the lock. A daily run takes seconds. */
    static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(30);

    private static final Logger log = LoggerFactory.getLogger(DailyPriceJob.class);

    private final LockingTaskExecutor lockingTaskExecutor;
    private final TefasPriceIngestService ingestService;
    private final Clock clock;

    public DailyPriceJob(LockingTaskExecutor lockingTaskExecutor, TefasPriceIngestService ingestService, Clock clock) {
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.ingestService = ingestService;
        this.clock = clock;
    }

    @Scheduled(cron = "${jobs.daily-price.cron:0 30 19 * * MON-FRI}", zone = "Europe/Istanbul")
    public void scheduledRun() {
        if (run(IngestRun.Trigger.SCHEDULED).isEmpty()) {
            log.info("Daily TEFAS price job skipped: lock '{}' is held elsewhere", LOCK_NAME);
        }
    }

    /** Runs now. Empty if another TEFAS ingestion currently holds the lock. */
    public Optional<IngestRun> run(IngestRun.Trigger trigger) {
        return withLock(() -> ingestService.ingestRecent(trigger));
    }

    /** One fund's history under the same lock. Empty if the lock is held. */
    public Optional<IngestRun> backfill(String code, LocalDate from, LocalDate to) {
        return withLock(() -> ingestService.backfill(code, from, to));
    }

    private Optional<IngestRun> withLock(LockingTaskExecutor.TaskWithResult<IngestRun> task) {
        LockConfiguration lock = new LockConfiguration(clock.instant(), LOCK_NAME, LOCK_AT_MOST_FOR, Duration.ZERO);
        try {
            LockingTaskExecutor.TaskResult<IngestRun> result = lockingTaskExecutor.executeWithLock(task, lock);
            return result.wasExecuted() ? Optional.ofNullable(result.getResult()) : Optional.empty();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
