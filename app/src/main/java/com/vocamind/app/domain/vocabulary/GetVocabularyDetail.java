package com.vocamind.app.domain.vocabulary;
import com.vocamind.app.domain.common.RepoResult;
public final class GetVocabularyDetail {
    private final VocabularyRepository repository;
    public GetVocabularyDetail(VocabularyRepository repository) { this.repository = repository; }
    public RepoResult<VocabularyDetail> execute(String packId, String stableId) {
        return repository.getDetail(packId, stableId);
    }
    public RepoResult<VocabularyDetail> executeCatalog(String stableId) {
        return repository.getCatalogDetail(stableId);
    }
}
