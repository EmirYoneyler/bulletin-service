package com.fonbulteni.bulletinservice.dto;

import com.fonbulteni.bulletinservice.entity.IngestRun;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Summary of one ingestion run as returned by the admin endpoints. */
public record IngestRunResponse(
        Long id,
        IngestRun.Source source,
        IngestRun.Trigger trigger,
        IngestRun.Status status,
        LocalDate rangeFrom,
        LocalDate rangeTo,
        Instant startedAt,
        Instant finishedAt,
        int fundCount,
        int fundsFailed,
        int rowsWritten,
        int rowsUnchanged,
        List<String> errors) {

    public static IngestRunResponse from(IngestRun run) {
        List<String> errors = run.getErrors() == null ? List.of() : List.of(run.getErrors().split("\n"));
        return new IngestRunResponse(run.getId(), run.getSource(), run.getTrigger(), run.getStatus(),
                run.getRangeFrom(), run.getRangeTo(), run.getStartedAt(), run.getFinishedAt(), run.getFundCount(),
                run.getFundsFailed(), run.getRowsWritten(), run.getRowsUnchanged(), errors);
    }
}
