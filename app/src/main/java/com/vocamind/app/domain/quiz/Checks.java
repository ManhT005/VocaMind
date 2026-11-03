package com.vocamind.app.domain.quiz;

final class Checks {
    private Checks() {}
    static String text(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name);
        return value;
    }
}
