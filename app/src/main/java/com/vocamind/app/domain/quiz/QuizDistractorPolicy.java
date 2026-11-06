package com.vocamind.app.domain.quiz;

import java.util.*;

/** Immutable, symmetric exclusions supplied by the content owner, across all packs. */
public final class QuizDistractorPolicy {
    private final Map<String, Set<String>> forbidden;

    public QuizDistractorPolicy(Collection<? extends List<String>> pairs) {
        if (pairs == null) throw new IllegalArgumentException("pairs required");
        Map<String, Set<String>> result = new HashMap<>();
        for (List<String> pair : pairs) {
            if (pair == null || pair.size() != 2) throw new IllegalArgumentException("pair requires two IDs");
            String a = id(pair.get(0)), b = id(pair.get(1));
            if (a.equals(b)) throw new IllegalArgumentException("self exclusion");
            result.computeIfAbsent(a, ignored -> new HashSet<>()).add(b);
            result.computeIfAbsent(b, ignored -> new HashSet<>()).add(a);
        }
        Map<String, Set<String>> copy = new HashMap<>();
        result.forEach((key, values) -> copy.put(key, Collections.unmodifiableSet(new HashSet<>(values))));
        forbidden = Collections.unmodifiableMap(copy);
    }

    private static String id(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{16}"))
            throw new IllegalArgumentException("policy requires stable IDs");
        return value.toLowerCase(Locale.ROOT);
    }

    /** Explicitly unrestricted content, primarily synthetic fixtures. Never a missing-file fallback. */
    public static QuizDistractorPolicy unrestricted() { return new QuizDistractorPolicy(Collections.emptyList()); }

    public boolean allows(String targetId, String candidateId) {
        if (targetId == null || candidateId == null) throw new IllegalArgumentException("IDs required");
        String target = targetId.toLowerCase(Locale.ROOT), candidate = candidateId.toLowerCase(Locale.ROOT);
        return !target.equals(candidate) && !forbidden.getOrDefault(target, Collections.emptySet()).contains(candidate);
    }
}
