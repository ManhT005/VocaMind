package com.vocamind.app.domain.common;

/** Standard failure categories for repository calls. No storage exception crosses the domain. */
public enum RepoError {
    /** Caller passed an invalid argument; nothing was read or written. */
    INVALID_ARGUMENT,
    /** The referenced pack or vocabulary does not exist in the catalog. */
    NOT_FOUND,
    /** Storage read failed. Never means "empty". */
    READ_FAILED,
    /** Expected progress changed before commit. Reload; do not replay the old rating. */
    CONFLICT,
    /** Storage write failed; the write was rolled back as a whole. */
    WRITE_FAILED,
    /** Persisted data violates a domain invariant (bad box, score, stable ID...). */
    INVALID_DATA
}
