package de.ostfale.greenroom.domain.activities;

import org.springframework.data.annotation.Id;

import java.time.LocalDate;

import static de.ostfale.greenroom.domain.Texts.required;

/**
 * Something that happened while an evening was planned and has no field of its own: a
 * phone call, a mail to a sponsor, a thing to remember. What the inquiries already record
 * is not written here a second time — the history mixes the two when it is shown.
 *
 * <p>Append-only. There is no {@code with…} method and no way to change or drop an entry,
 * because a log that can be corrected afterwards is a note, not a history. A line that
 * turned out wrong is answered by the next line.
 *
 * <p>The direction says what kind of line it is. {@code OUTGOING} and {@code INCOMING} went
 * over a channel and carry it; a {@code NOTE} went nowhere, so it has none — that is what
 * separates something we did from something we wrote down.
 */
public record Activity(
        @Id Long id,
        Long eventId,
        LocalDate happenedOn,
        ActivityDirection direction,
        ContactChannel channel,
        String what) {

    public Activity {
        if (eventId == null) {
            throw new IllegalArgumentException("Activity :: an entry belongs to an event");
        }
        if (happenedOn == null) {
            throw new IllegalArgumentException("Activity :: an entry is dated");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Activity :: an entry needs a direction");
        }
        if (direction == ActivityDirection.NOTE && channel != null) {
            throw new IllegalArgumentException("Activity :: a note went nowhere, so it has no channel");
        }
        if (direction != ActivityDirection.NOTE && channel == null) {
            throw new IllegalArgumentException("Activity :: something that went out or came in needs a channel");
        }
        what = required(what, "Activity :: an entry needs to say what happened");
    }

    /** Something we did or that reached us, over a channel. */
    public static Activity over(Long eventId, LocalDate happenedOn, ActivityDirection direction,
                                ContactChannel channel, String what) {
        return new Activity(null, eventId, happenedOn, direction, channel, what);
    }

    /** Something worth writing down that went nowhere. */
    public static Activity noted(Long eventId, LocalDate happenedOn, String what) {
        return new Activity(null, eventId, happenedOn, ActivityDirection.NOTE, null, what);
    }
}
