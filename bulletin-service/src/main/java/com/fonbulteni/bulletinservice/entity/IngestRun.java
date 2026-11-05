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

    public enum Source { TEFAS }

    public enum Trigger { SCHEDULED, MANUAL, BACKFILL }

    /** SUCCESS: every fund ok · PARTIAL: some funds failed · FAILED: every fund failed. */
    public enum Status { RUNNING, SUCCESS, PARTIAL, FAILED }

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

    @Column(name = "fund_count", nullable = false)
    private int fundCount;

    @Column(name = "funds_failed", nullable = false)
    private int fundsFailed;

    @Column(name = "rows_written", nullable = false)
    private int rowsWritten;

    @Column(name = "rows_unchanged", nullable = false)
    private int rowsUnchanged;

    /** One line per failed fund. */
    @Column(columnDefinition = "text")
    private String errors;

    public static IngestRun started(Source source, Trigger trigger, LocalDate from, LocalDate to,
                                    int fundCount, Instant now) {
        IngestRun run = new IngestRun();
        run.source = source;
        run.trigger = trigger;
        run.status = Status.RUNNING;
        run.rangeFrom = from;
        run.rangeTo = to;
        run.fundCount = fundCount;
        run.startedAt = now;
        return run;
    }

    public void finish(int fundsFailed, int rowsWritten, int rowsUnchanged, String errors, Instant now) {
        this.fundsFailed = fundsFailed;
        this.rowsWritten = rowsWritten;
        this.rowsUnchanged = rowsUnchanged;
        this.errors = errors;
        this.finishedAt = now;
        if (fundsFailed == 0) {
            this.status = Status.SUCCESS;
        } else if (fundsFailed < fundCount) {
            this.status = Status.PARTIAL;
        } else {
            this.status = Status.FAILED;
        }
    }
}
