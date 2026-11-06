package com.vocamind.app.domain.learning;

/** Typed review failure. No failure may be rendered as a committed review. */
public final class ReviewWriteException extends RuntimeException {
    public enum Reason { NOT_FOUND, READ_FAILED, INVALID_DATA, CONFLICT, STORAGE }
    private final Reason reason;
    public ReviewWriteException(Reason reason, Throwable cause) {
        super("Review failed: " + reason, cause);
        this.reason = java.util.Objects.requireNonNull(reason);
    }
    public Reason getReason() { return reason; }
}
