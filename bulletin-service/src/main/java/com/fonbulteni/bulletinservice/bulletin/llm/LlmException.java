package com.fonbulteni.bulletinservice.bulletin.llm;

/** Any LLM failure (unreachable, rate limited, timed out, unusable answer). Triggers the template fallback. */
public class LlmException extends RuntimeException {

    public enum Kind { UNAVAILABLE, RATE_LIMITED, TIMEOUT, BAD_RESPONSE, BAD_REQUEST }

    private final Kind kind;

    public LlmException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
