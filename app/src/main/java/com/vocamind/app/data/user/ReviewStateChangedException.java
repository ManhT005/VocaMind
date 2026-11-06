package com.vocamind.app.data.user;

/** Internal optimistic-write conflict; translated by the repository. */
public final class ReviewStateChangedException extends IllegalStateException {
    public ReviewStateChangedException() { super("Persisted progress differs from review snapshot"); }
}
