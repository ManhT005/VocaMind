package com.vocamind.app.data.local;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Internal data-layer input contract; stay below API 26 SQLite's bind limit. */
public final class DaoInputs {
    public static final int BATCH_SIZE = 900;
    private DaoInputs() { }

    public static void id(String value) {
        require(value != null && !value.trim().isEmpty(), "ID must not be blank");
    }

    public static List<List<String>> batches(List<String> values) {
        require(values != null, "ID collection required");
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) { id(value); unique.add(value); }
        List<String> ids = new ArrayList<>(unique);
        List<List<String>> result = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += BATCH_SIZE) {
            result.add(new ArrayList<>(ids.subList(start, Math.min(start + BATCH_SIZE, ids.size()))));
        }
        return result;
    }

    public static void limit(int limit) { require(limit > 0, "Limit must be positive"); }
    public static void range(long start, long end) { require(start < end, "Range must be nonempty and increasing"); }
    public static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
}
