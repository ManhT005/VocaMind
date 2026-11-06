package com.vocamind.app.data.repository;

import com.vocamind.app.domain.common.RepoError;
import com.vocamind.app.domain.common.RepoResult;
import java.util.function.Supplier;

/** Maps storage exceptions to {@link RepoError}; no Room/SQLite type escapes to the domain. */
final class RepositoryGuard {
    private RepositoryGuard() { }

    static <T> RepoResult<T> read(Supplier<RepoResult<T>> op) { return run(op, RepoError.READ_FAILED); }
    static <T> RepoResult<T> write(Supplier<RepoResult<T>> op) { return run(op, RepoError.WRITE_FAILED); }

    private static <T> RepoResult<T> run(Supplier<RepoResult<T>> op, RepoError storageError) {
        try {
            return op.get();
        } catch (InvalidDataException e) {
            return RepoResult.failure(RepoError.INVALID_DATA);
        } catch (RuntimeException e) {
            return RepoResult.failure(storageError);
        }
    }

    /** Thrown by mappers when persisted rows break a domain invariant. */
    static final class InvalidDataException extends RuntimeException {
        InvalidDataException(Throwable cause) { super("Persisted data is invalid", cause); }
    }
}
