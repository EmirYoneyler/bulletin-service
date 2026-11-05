package com.fonbulteni.bulletinservice.controller;

import com.fonbulteni.bulletinservice.common.JobAlreadyRunningException;
import com.fonbulteni.bulletinservice.dto.IngestRunResponse;
import com.fonbulteni.bulletinservice.entity.IngestRun;
import com.fonbulteni.bulletinservice.scheduler.DailyPriceJob;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Manual triggers for data ingestion. Everything under {@code /api/admin} needs {@code ROLE_ADMIN}
 * (SecurityConfig). Calls are synchronous: the response is the finished run.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminIngestController {

    private final DailyPriceJob dailyPriceJob;

    public AdminIngestController(DailyPriceJob dailyPriceJob) {
        this.dailyPriceJob = dailyPriceJob;
    }

    /** Same work as the 19:30 cron run, right now. */
    @PostMapping("/jobs/daily-price/run")
    public IngestRunResponse runDailyPriceJob() {
        return dailyPriceJob.run(IngestRun.Trigger.MANUAL)
                .map(IngestRunResponse::from)
                .orElseThrow(JobAlreadyRunningException::new);
    }

    /** Loads one fund's history, e.g. {@code ?code=AAV&from=2026-01-01&to=2026-11-04}. */
    @PostMapping("/ingest/tefas")
    public IngestRunResponse backfillTefas(
            @RequestParam String code,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dailyPriceJob.backfill(code, from, to)
                .map(IngestRunResponse::from)
                .orElseThrow(JobAlreadyRunningException::new);
    }
}
