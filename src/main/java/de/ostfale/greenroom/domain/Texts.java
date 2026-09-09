package de.ostfale.greenroom.domain;

/**
 * The two things every aggregate does with a text field: insist on it, or accept that it
 * is missing. Kept in one place so the rule reads the same everywhere.
 */
public final class Texts {

    private Texts() {
    }

    /** The stripped value, or a {@link RuleViolated} naming what insisted on it. */
    public static String required(String value, Rule rule) {
        if (value == null || value.isBlank()) {
            throw new RuleViolated(rule);
        }
        return value.strip();
    }

    /** The stripped value, or {@code null} — an empty form field is not an empty string. */
    public static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * A web address as typed, made into one a browser follows. Whoever writes down a
     * homepage writes "www.firma.de", and an href without a scheme is read as a path on
     * this application. Nothing else is checked: a typo in a URL is not a rule violation,
     * and a link that leads nowhere is still what somebody wrote down.
     */
    public static String url(String value) {
        String typed = optional(value);
        return typed == null || typed.contains("://") ? typed : "https://" + typed;
    }
}
