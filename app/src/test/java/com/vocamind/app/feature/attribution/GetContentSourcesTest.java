package com.vocamind.app.feature.attribution;

import com.vocamind.app.domain.catalog.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class GetContentSourcesTest {
    @Test public void returnsEveryFieldFromRepositoryAndPreservesEmpty() {
        FakeCatalogRepository repository = new FakeCatalogRepository();
        GetContentSources query = new GetContentSources(repository);
        assertTrue(query.execute().isEmpty());
        CatalogSource first = new CatalogSource("one", "First", "https://one.example", "CC0",
                "https://one.example/license", "2026-10-25");
        CatalogSource second = new CatalogSource("two", "Second", "", "custom", null, "2026-10-26");
        repository.sources = List.of(first, second);
        assertEquals(List.of(first, second), query.execute());
        assertEquals(2, repository.calls);
    }

    @Test public void readFailureIsNotConvertedToEmpty() {
        FakeCatalogRepository repository = new FakeCatalogRepository();
        repository.failure = new CatalogReadException(new IllegalStateException("broken catalog"));
        assertSame(repository.failure, assertThrows(CatalogReadException.class,
                () -> new GetContentSources(repository).execute()));
    }
}
