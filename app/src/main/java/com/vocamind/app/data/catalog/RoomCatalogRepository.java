package com.vocamind.app.data.catalog;

import androidx.annotation.WorkerThread;
import com.vocamind.app.domain.catalog.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** No Room types cross the domain boundary. Own the database in CatalogProvider. */
@WorkerThread
public final class RoomCatalogRepository implements CatalogRepository {
    private final CatalogDao dao;
    private final Supplier<ContentCredits> credits;
    public RoomCatalogRepository(CatalogDao dao) { this(dao, ContentCredits::empty); }
    public RoomCatalogRepository(CatalogDao dao, Supplier<ContentCredits> credits) {
        this.dao = dao;
        this.credits = credits;
    }
    private static <T> T read(Supplier<T> operation) {
        try { return operation.get(); }
        catch (RuntimeException e) { throw new CatalogReadException(e); }
    }
    public List<CatalogPack> getPacks() { return read(() -> {
        List<CatalogPack> out = new ArrayList<>();
        for (var p : dao.packs()) out.add(CatalogMapper.pack(p, dao.wordCount(p.id)));
        return Collections.unmodifiableList(out);
    }); }
    public Optional<CatalogPack> getPack(String id) { return read(() ->
        Optional.ofNullable(dao.pack(id)).map(p -> CatalogMapper.pack(p, dao.wordCount(p.id)))); }
    public List<CatalogVocabulary> getWords(String id) { return read(() -> {
        List<CatalogVocabulary> out = new ArrayList<>();
        for (var v : dao.words(id)) out.add(CatalogMapper.vocabulary(v));
        return Collections.unmodifiableList(out);
    }); }
    public Optional<CatalogVocabulary> getVocabulary(String id) { return read(() ->
        Optional.ofNullable(dao.vocabulary(id)).map(CatalogMapper::vocabulary)); }
    public List<CatalogSource> getSources() { return read(() -> {
        ContentCredits metadata = credits.get();
        List<CatalogSource> out = new ArrayList<>();
        for (var s : dao.sources()) out.add(metadata.enrich(CatalogMapper.source(s)));
        return Collections.unmodifiableList(out);
    }); }
    public Optional<CatalogSource> getSourceForVocabulary(String id) { return read(() ->
        Optional.ofNullable(dao.sourceForVocabulary(id)).map(s -> credits.get().enrich(CatalogMapper.source(s)))); }
}
