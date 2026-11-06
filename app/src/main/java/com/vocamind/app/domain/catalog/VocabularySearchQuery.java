package com.vocamind.app.domain.catalog;

import java.text.Normalizer;
import java.util.*;

/** Token input, independent of SQLite syntax. Accents are preserved (NFC + ROOT lowercase). */
public final class VocabularySearchQuery {
    public static final int MAX_CODE_POINTS = 256;
    public static final int MAX_TOKENS = 32;
    private final List<String> tokens;
    private VocabularySearchQuery(List<String> tokens) {
        this.tokens = Collections.unmodifiableList(new ArrayList<>(tokens));
    }
    public static VocabularySearchQuery parse(String input) {
        String raw = input == null ? "" : input;
        if (raw.codePointCount(0, raw.length()) > MAX_CODE_POINTS)
            throw new IllegalArgumentException("Search input too long");
        String text = Normalizer.normalize(raw, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset); offset += Character.charCount(cp);
            int type = Character.getType(cp);
            if (Character.isLetterOrDigit(cp) || (word.length() > 0
                    && (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK))) {
                word.appendCodePoint(cp);
            } else if (word.length() > 0) { tokens.add(word.toString()); word.setLength(0); }
        }
        if (word.length() > 0) tokens.add(word.toString());
        if (tokens.size() > MAX_TOKENS) throw new IllegalArgumentException("Too many search tokens");
        return new VocabularySearchQuery(tokens);
    }
    public List<String> getTokens() { return tokens; }
    public String getText() { return String.join(" ", tokens); }
    public boolean isEmpty() { return tokens.isEmpty(); }
}
