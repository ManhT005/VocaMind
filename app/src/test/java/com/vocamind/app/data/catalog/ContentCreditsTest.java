package com.vocamind.app.data.catalog;

import com.vocamind.app.domain.catalog.CatalogSource;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = {26,35})
public class ContentCreditsTest {
    private final String row = "{\"sourceId\":\"a\",\"snapshotDate\":\"date\",\"authors\":[\"Synthetic A\"],\"creditText\":\"Test credit\"}";
    private ContentCredits parse(String rows) {
        return ContentCredits.parse("{\"schemaVersion\":1,\"sources\":[" + rows + "]}");
    }
    private CatalogSource source(String id, String date) {
        return new CatalogSource(id,"Title","https://example.com","Test",null,date);
    }
    @Test public void onlyExactPairMatchesAndMissingIsTransparent() {
        ContentCredits credits = parse(row);
        assertEquals(List.of("Synthetic A"), credits.enrich(source("a","date")).authors);
        assertEquals("Test credit", credits.enrich(source("a","date")).creditText);
        assertTrue(credits.enrich(source("a","other-date")).authors.isEmpty());
        assertTrue(credits.enrich(source("b","date")).authors.isEmpty());
        assertTrue(ContentCredits.empty().enrich(source("a","date")).authors.isEmpty());
    }
    @Test public void differentSourcesRetainTheirOwnAuthorsAndNotices() {
        ContentCredits credits = parse(row + "," + row.replace("\"a\"", "\"b\"")
                .replace("Synthetic A", "Synthetic B").replace("Test credit", "Second notice"));
        assertEquals(List.of("Synthetic A"), credits.enrich(source("a","date")).authors);
        assertEquals(List.of("Synthetic B"), credits.enrich(source("b","date")).authors);
        assertEquals("Second notice", credits.enrich(source("b","date")).creditText);
    }
    @Test public void rejectsDuplicateVersionAndMalformedFields() {
        assertThrows(IllegalArgumentException.class, () -> parse(row + "," + row));
        for (String json : List.of("{", "{\"schemaVersion\":2,\"sources\":[]}",
                "{\"schemaVersion\":\"1\",\"sources\":[]}",
                "{\"schemaVersion\":1,\"sources\":{}}")) {
            assertThrows(IllegalArgumentException.class, () -> ContentCredits.parse(json));
        }
        for (String bad : List.of(row.replace("Synthetic A", " "),row.replace("[\"Synthetic A\"]", "[3]"),
                row.replace("\"sourceId\":\"a\"", "\"sourceId\":null"))) {
            assertThrows(IllegalArgumentException.class, () -> parse(bad));
        }
    }
}
