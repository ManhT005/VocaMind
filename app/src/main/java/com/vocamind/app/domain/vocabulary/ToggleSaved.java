package com.vocamind.app.domain.vocabulary;
import com.vocamind.app.domain.common.RepoResult;
/** Despite the name this sets a target, so retries cannot invert the user's intent. */
public final class ToggleSaved {
    private final VocabularyRepository repository;
    public ToggleSaved(VocabularyRepository repository) { this.repository = repository; }
    public RepoResult<Boolean> execute(String packId, String stableId, boolean target) {
        return repository.setSaved(packId, stableId, target);
    }
}
