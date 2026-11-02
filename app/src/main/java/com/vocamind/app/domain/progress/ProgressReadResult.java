package com.vocamind.app.domain.progress;

/** Small read outcome shared by repository/use case. Failure is never an empty snapshot. */
public final class ProgressReadResult {
    public enum Error { READ_FAILED, INVALID_DATA }
    private final ProgressSnapshot snapshot;
    private final Error error;

    private ProgressReadResult(ProgressSnapshot snapshot, Error error) {
        this.snapshot = snapshot;
        this.error = error;
    }
    public static ProgressReadResult success(ProgressSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot required");
        return new ProgressReadResult(snapshot, null);
    }
    public static ProgressReadResult failure(Error error) {
        if (error == null) throw new IllegalArgumentException("error required");
        return new ProgressReadResult(null, error);
    }
    public boolean isSuccess() { return snapshot != null; }
    public ProgressSnapshot getSnapshot() {
        if (!isSuccess()) throw new IllegalStateException("failure has no snapshot");
        return snapshot;
    }
    public Error getError() {
        if (isSuccess()) throw new IllegalStateException("success has no error");
        return error;
    }
}
