package de.ostfale.greenroom.domain;

/**
 * The two things every aggregate does with a text field: insist on it, or accept that it
 * is missing. Kept in one place so the rule reads the same everywhere.
 */
public final class Texts {

    private Texts() {
    }

    /** The stripped value, or an {@link IllegalArgumentException} if there is none. */
    public static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    /** The stripped value, or {@code null} — an empty form field is not an empty string. */
    public static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
