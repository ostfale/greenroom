package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Turning what a form sends into what a record accepts. An empty field is not an empty
 * value here but the absence of one, which is what the records expect.
 */
final class FormValues {

    private FormValues() {
    }

    /** The date, or {@code null} when the field was left empty. */
    static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new RuleViolated(Rule.DATE_UNREADABLE, value);
        }
    }
}
