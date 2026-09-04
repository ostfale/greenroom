package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.events.EventMode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Turning what a form sends into what a record accepts. An empty field is not an empty
 * value here but the absence of one, which is what the records expect.
 *
 * <p>Every page reads its fields here, so that a date, a mode or a number of seats means
 * one thing in this application and not one per form. Two forms build an evening out of
 * the same fields — the one that starts a new one and the one that writes down a past one
 * — and that is where reading them twice would drift apart first.
 *
 * <p>Nothing in here asks anybody anything, which keeps it a plain conversion, testable
 * the way the records are. A value that can only be checked against something stored is
 * turned by whoever can look that something up: {@link ChosenAddress} is the one of those.
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
     * How many people fit in. Empty means nobody has counted; anything that is not a
     * number is a mistake worth naming.
     */
    static Integer seats(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            throw new RuleViolated(Rule.CAPACITY_IS_A_NUMBER_OF_SEATS, value);
        }
    }

    /**
     * What a filter sends, or {@code null} when the select was left on "alle". The one
     * value here that refuses nothing: a filter nobody can read narrows the list by
     * nothing, and a list somebody is looking through is not a form to be filled in
     * correctly.
     */
    static Long filterNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
