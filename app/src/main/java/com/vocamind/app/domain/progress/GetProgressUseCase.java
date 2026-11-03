package com.vocamind.app.domain.progress;

/**
 * PROGRESS-02 implements this boundary by calling ProgressRepository.readProgress() and returning
 * its nonnull success/failure unchanged. Synchronous; caller owns background execution and refresh.
 * No production IO implementation is provided by PROGRESS-01; a fake can implement this contract.
 */
@FunctionalInterface
public interface GetProgressUseCase {
    ProgressReadResult execute();
}
