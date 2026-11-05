package com.fonbulteni.bulletinservice.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Uniform error body for every API error.
 *
 * @param code        stable, machine-readable error code (e.g. {@code FUND_NOT_FOUND})
 * @param message     human-readable explanation in Turkish
 * @param fieldErrors per-field problems; only present for validation errors
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }
}
