package com.fonbulteni.bulletinservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/** Record of one ingestion run: what was asked for and what happened. */
@Entity
@Table(name = "ingest_run")
@Getter
@NoArgsConstructor
public class IngestRun {

    public enum Source { TEFAS, EVDS }

    public enum Trigger { SCHEDULED, MANUAL, BACKFILL }

    /**
     * SUCCESS: every target ok · PARTIAL: some failed · FAILED: all failed · SKIPPED: not attempted
     * (e.g. no EVDS key). Targets are funds for TEFAS and series for EVDS.
     */
    public enum Status { RUNNING, SUCCESS, PARTIAL, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private Trigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "range_from", nullable = false)
    private LocalDate rangeFrom;

    @Column(name = "range_to", nullable = false)
    private LocalDate rangeTo;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "target_count", nullable = false)
    private int targetCount;

    @Column(name = "targets_failed", nullable = false)
    private int targetsFailed;

    @Column(name = "rows_written", nullable = false)
    private int rowsWritten;

    @Column(name = "rows_unchanged", nullable = false)
    private int rowsUnchanged;

    /** One line per failed target, or the reason a run was skipped. */
    @Column(columnDefinition = "text")
    private String errors;

    public static IngestRun started(Source source, Trigger trigger, LocalDate from, LocalDate to,
                                    int targetCount, Instant now) {
        IngestRun run = new IngestRun();
        run.source = source;
        run.trigger = trigger;
        run.status = Status.RUNNING;
        run.rangeFrom = from;
        run.rangeTo = to;
        run.targetCount = targetCount;
        run.startedAt = now;
        return run;
    }

    public void finish(int targetsFailed, int rowsWritten, int rowsUnchanged, String errors, Instant now) {
        this.targetsFailed = targetsFailed;
        this.rowsWritten = rowsWritten;
        this.rowsUnchanged = rowsUnchanged;
        this.errors = errors;
        this.finishedAt = now;
        if (targetsFailed == 0) {
            this.status = Status.SUCCESS;
        } else if (targetsFailed < targetCount) {
            this.status = Status.PARTIAL;
        } else {
            this.status = Status.FAILED;
        }
    }

    /** Marks a run that was deliberately not attempted, with the reason. */
    public void skip(String reason, Instant now) {
        this.status = Status.SKIPPED;
        this.errors = reason;
        this.finishedAt = now;
    }
}
