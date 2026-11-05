package com.fonbulteni.bulletinservice.ingest.tefas;

/** Base type for TEFAS client failures. */
public abstract class TefasException extends RuntimeException {

    protected TefasException(String message, Throwable cause) {
        super(message, cause);
    }
}
