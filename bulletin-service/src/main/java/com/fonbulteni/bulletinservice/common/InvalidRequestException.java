package com.fonbulteni.bulletinservice.common;

/** A request that is well-formed but not acceptable (e.g. an impossible date range); mapped to HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    private final String code;

    public InvalidRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
