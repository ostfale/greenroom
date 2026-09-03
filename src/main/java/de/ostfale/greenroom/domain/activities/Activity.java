package de.ostfale.greenroom.domain.activities;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import org.springframework.data.annotation.Id;

import java.time.LocalDate;

import static de.ostfale.greenroom.domain.Texts.required;

/**
 * One line of what happened while an evening was planned: a mail that went out, or one
 * that came back, on the day it did, in whatever words describe it. Written by hand and
 * only by hand — nothing in the application adds a line on its own.
 *
 * <p>Append-only. There is no {@code with…} method and no way to change or drop an entry,
 * because a log that can be corrected afterwards is a note, not a history. A line that
 * turned out wrong is answered by the next line.
 */
public record Activity(
        @Id Long id,
        Long eventId,
        LocalDate happenedOn,
        ActivityKind kind,
        String what) {

    public Activity {
        if (eventId == null) {
            throw new RuleViolated(Rule.ACTIVITY_BELONGS_TO_AN_EVENT);
        }
        if (happenedOn == null) {
            throw new RuleViolated(Rule.ACTIVITY_IS_DATED);
        }
        if (kind == null) {
            throw new RuleViolated(Rule.ACTIVITY_NEEDS_A_KIND);
        }
        what = required(what, Rule.ACTIVITY_NEEDS_A_TEXT);
    }

    /** A line that has not been written yet. */
    public static Activity of(Long eventId, LocalDate happenedOn, ActivityKind kind, String what) {
        return new Activity(null, eventId, happenedOn, kind, what);
    }
}
