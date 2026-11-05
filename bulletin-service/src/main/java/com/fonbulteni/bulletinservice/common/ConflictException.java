package com.fonbulteni.bulletinservice.common;

/** Base type for "conflicts with existing data" errors; mapped to HTTP 409. */
public abstract class ConflictException extends RuntimeException {

    private final String code;

    protected ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
