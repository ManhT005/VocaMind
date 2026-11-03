package com.vocamind.app.domain.learning;

/** Neither failure may be reported to the UI as a committed review. */
public final class ReviewWriteException extends RuntimeException {
    public enum Reason { CONFLICT, STORAGE }
    private final Reason reason;
    public ReviewWriteException(Reason reason, Throwable cause) {
        super(reason == Reason.CONFLICT ? "Review state changed; reload before rating again"
                : "Unable to persist review", cause);
        this.reason = java.util.Objects.requireNonNull(reason);
    }
    public Reason getReason() { return reason; }
}
