package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.events.EventMode;
import de.ostfale.greenroom.domain.locations.Location;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Turning what a form sends into what a record accepts. An empty field is not an empty
 * value here but the absence of one, which is what the records expect.
 *
 * <p>Two forms build an evening out of the same fields — the one that starts a new one and
 * the one that writes down a past one — and read them the same way. They read them here,
 * so that a date, a mode or a picked address means one thing in this application and not
 * two that drift apart.
 *
 * <p>Nothing in here asks anybody anything: what has to be looked up first is handed in
 * already loaded. That keeps this a plain conversion, testable the way the records are.
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

    /**
     * The time of day, or {@code null} when the field was left empty. A browser sends
     * {@code HH:mm}, and {@code HH:mm:ss} where somebody typed seconds — both parse.
     */
    static LocalTime time(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new RuleViolated(Rule.TIME_UNREADABLE, value);
        }
    }

    /**
     * How the evening is held. Empty is what an evening ordinarily is: on site. The years
     * worth entering by hand are the ones that were not.
     */
    static EventMode mode(String value) {
        if (value == null || value.isBlank()) {
            return EventMode.ONSITE;
        }
        try {
            return EventMode.valueOf(value.strip());
        } catch (IllegalArgumentException e) {
            throw new RuleViolated(Rule.EVENT_NEEDS_A_MODE, value);
        }
    }

    /**
     * The person who speaks. Not optional: a talk is found by approaching somebody, so
     * there is no evening and no talk without one.
     */
    static Long speakerId(String value) {
        Long picked = speakerIdOrNone(value);
        if (picked == null) {
            throw new RuleViolated(Rule.NO_SPEAKER_CHOSEN);
        }
        return picked;
    }

    /** The same, where the select is still allowed to stand on nobody. */
    static Long speakerIdOrNone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.strip());
        } catch (NumberFormatException e) {
            throw new RuleViolated(Rule.NO_SPEAKER_CHOSEN);
        }
    }

    /** The host. Empty is a valid answer: an evening may well have no venue yet. */
    static Long locationId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.strip());
        } catch (NumberFormatException e) {
            throw new RuleViolated(Rule.NO_LOCATION_CHOSEN);
        }
    }

    /**
     * Which of that place's addresses was picked, checked against the place itself: a
     * position that is not there is a stale page or a tampered form, and either way not an
     * address. Asked here so it refuses on the form rather than on the page that reads it
     * back.
     *
     * <p>Empty means the address the place has today. So does a form that names no place:
     * a position points into one place's list, and at another the same number is another
     * building.
     */
    static Integer addressAt(Location place, String value) {
        if (place == null || value == null || value.isBlank()) {
            return null;
        }
        int picked;
        try {
            picked = Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw new RuleViolated(Rule.NO_ADDRESS_AT_POSITION, value);
        }
        place.addressAt(picked);
        return picked;
    }
}
