package com.fonbulteni.bulletinservice.common;

/** Base type for "resource does not exist" errors; mapped to HTTP 404. */
public abstract class NotFoundException extends RuntimeException {

    private final String code;

    protected NotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
