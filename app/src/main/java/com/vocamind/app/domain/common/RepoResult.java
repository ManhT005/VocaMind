package com.vocamind.app.domain.common;

/** Success value or a {@link RepoError}. Failure is never an empty or zero value. */
public final class RepoResult<T> {
    private final T value;
    private final RepoError error;

    private RepoResult(T value, RepoError error) {
        this.value = value;
        this.error = error;
    }

    public static <T> RepoResult<T> success(T value) {
        if (value == null) throw new IllegalArgumentException("value required; use Optional/empty list");
        return new RepoResult<>(value, null);
    }

    public static <T> RepoResult<T> failure(RepoError error) {
        if (error == null) throw new IllegalArgumentException("error required");
        return new RepoResult<>(null, error);
    }

    public boolean isSuccess() { return error == null; }

    public T get() {
        if (error != null) throw new IllegalStateException("failure has no value: " + error);
        return value;
    }

    public RepoError getError() {
        if (error == null) throw new IllegalStateException("success has no error");
        return error;
    }
}
