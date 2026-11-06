package com.vocamind.app.data.catalog;

import com.vocamind.app.domain.catalog.VocabularySearchQuery;
import java.util.ArrayList;
import java.util.List;

/** FTS4 requires '*' INSIDE quoted prefix tokens (unlike FTS5 examples).
 * Whitespace between tokens is implicit AND. Input never becomes SQL or FTS operators. */
final class FtsMatchExpression {
    private FtsMatchExpression() { }
    static String from(VocabularySearchQuery query) {
        List<String> parts = new ArrayList<>();
        for (String token : query.getTokens()) parts.add('"' + token + "*" + '"');
        return String.join(" ", parts);
    }
}
