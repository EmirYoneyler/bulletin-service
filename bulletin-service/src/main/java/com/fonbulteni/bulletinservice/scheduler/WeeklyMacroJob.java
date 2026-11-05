package com.fonbulteni.bulletinservice.scheduler;

import com.fonbulteni.bulletinservice.entity.IngestRun;
import com.fonbulteni.bulletinservice.ingest.evds.MacroIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Weekly EVDS macro ingestion (AGENT_PROMPT §7.9), Monday 07:00 Istanbul, before the 08:00 bulletin. */
@Component
public class WeeklyMacroJob {

    public static final String LOCK_NAME = "evds-ingest";
    static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(30);

    private static final Logger log = LoggerFactory.getLogger(WeeklyMacroJob.class);

    private final JobLock jobLock;
    private final MacroIngestService ingestService;

    public WeeklyMacroJob(JobLock jobLock, MacroIngestService ingestService) {
        this.jobLock = jobLock;
        this.ingestService = ingestService;
    }

    @Scheduled(cron = "${jobs.weekly-macro.cron:0 0 7 * * MON}", zone = "Europe/Istanbul")
    public void scheduledRun() {
        if (run(IngestRun.Trigger.SCHEDULED).isEmpty()) {
            log.info("Weekly EVDS macro job skipped: lock '{}' is held elsewhere", LOCK_NAME);
        }
    }

    /** Runs now. Empty if another EVDS ingestion holds the lock. */
    public Optional<IngestRun> run(IngestRun.Trigger trigger) {
        return jobLock.runExclusive(LOCK_NAME, LOCK_AT_MOST_FOR, () -> ingestService.ingestRecent(trigger));
    }
}
