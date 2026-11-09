package com.vocamind.app.domain.progress;

/**
 * RepositoryProvider wires this boundary to ProgressRepository.readProgress(), returning its
 * success/failure unchanged. Synchronous; caller owns background execution and refresh.
 */
@FunctionalInterface
public interface GetProgressUseCase {
    ProgressReadResult execute();
}
