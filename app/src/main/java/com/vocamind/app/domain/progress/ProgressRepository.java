package com.vocamind.app.domain.progress;

/**
 * DATA-07 boundary: one consistent logical read of distinct progress counts and completed attempts.
 * Synchronous: caller MUST execute off the Android main thread. No scheduling/lifecycle implied.
 * Return nonnull success (including a valid empty DB) or failure. Map storage errors to READ_FAILED
 * and model/score/duplicate/invariant violations to INVALID_DATA; never substitute zero/empty.
 * History includes all completed attempts, sorted by completedAt DESC, id DESC.
 */
@FunctionalInterface
public interface ProgressRepository {
    ProgressReadResult readProgress();
}
