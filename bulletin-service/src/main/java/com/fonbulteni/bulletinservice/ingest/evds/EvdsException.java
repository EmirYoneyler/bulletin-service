package com.fonbulteni.bulletinservice.ingest.evds;

/** EVDS client failures. Subclasses say whether retrying later can help. */
public class EvdsException extends RuntimeException {

    public EvdsException(String message, Throwable cause) {
        super(message, cause);
    }

    /** No {@code EVDS_API_KEY}: the client is switched off on purpose. */
    public static class Disabled extends EvdsException {
        public Disabled() {
            super("EVDS_API_KEY tanımlı değil; EVDS istemcisi devre dışı.", null);
        }
    }

    /** EVDS answered but refused the request (403 wrong key, 4xx) or sent something unreadable. */
    public static class Rejected extends EvdsException {
        public Rejected(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** EVDS unreachable (5xx, timeout, connection). The weekly job retries next week. */
    public static class Unavailable extends EvdsException {
        public Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
