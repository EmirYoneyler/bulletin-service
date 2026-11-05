package com.fonbulteni.bulletinservice.ingest.tefas;

/** TEFAS answered but refused the request (4xx or an error message). Retrying the same request will not help. */
public class TefasRequestRejectedException extends TefasException {

    public TefasRequestRejectedException(String message) {
        super(message, null);
    }

    public TefasRequestRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
