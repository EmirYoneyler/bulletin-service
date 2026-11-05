package com.fonbulteni.bulletinservice.ingest.tefas;

/** TEFAS could not be reached after all retries (5xx, 429, timeouts). Callers skip the day and log. */
public class TefasUnavailableException extends TefasException {

    public TefasUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
