package de.ostfale.greenroom.domain.activities;

import org.springframework.data.annotation.Id;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static de.ostfale.greenroom.domain.Texts.optional;

/**
 * A request that went out to a speaker, with what came back. The first question of an
 * evening: the person is already on the talk, so what is asked about is the date.
 *
 * <p>{@code askedAbout} is a copy of the date that was proposed, not a look at the event.
 * If the speaker says no and the evening moves, what was asked stays what was asked — the
 * same reason the announced biography and the keywords are copied.
 *
 * <p>An inquiry is answered once. Asking again after a refusal is a new inquiry, so the
 * history keeps both attempts instead of overwriting the first.
 */
public record SpeakerInquiry(
        @Id Long id,
        Long eventId,
        Long speakerId,
        LocalDate askedAbout,
        LocalDate sentAt,
        ContactChannel channel,
        InquiryOutcome outcome,
        String note) {

    public SpeakerInquiry {
        if (eventId == null) {
            throw new IllegalArgumentException("SpeakerInquiry :: an inquiry belongs to an event");
        }
        if (speakerId == null) {
            throw new IllegalArgumentException("SpeakerInquiry :: an inquiry goes to a speaker");
        }
        if (sentAt == null) {
            throw new IllegalArgumentException("SpeakerInquiry :: an inquiry is logged after it went out");
        }
        if (channel == null) {
            throw new IllegalArgumentException("SpeakerInquiry :: an inquiry needs a channel");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("SpeakerInquiry :: an inquiry needs an outcome");
        }
        note = optional(note);
    }

    /** One that has just gone out. Nobody has answered yet, and that is the normal state. */
    public static SpeakerInquiry sent(Long eventId, Long speakerId, LocalDate askedAbout,
                                      LocalDate sentAt, ContactChannel channel) {
        return new SpeakerInquiry(null, eventId, speakerId, askedAbout, sentAt, channel,
                InquiryOutcome.PENDING, null);
    }

    /**
     * The answer came in.
     *
     * @throws IllegalStateException    if this inquiry was answered before
     * @throws IllegalArgumentException if the answer is {@code PENDING}, which is the
     *                                  absence of an answer rather than one
     */
    public SpeakerInquiry answered(InquiryOutcome answer) {
        if (outcome != InquiryOutcome.PENDING) {
            throw new IllegalStateException("SpeakerInquiry :: this inquiry was already answered");
        }
        if (answer == null || answer == InquiryOutcome.PENDING) {
            throw new IllegalArgumentException("SpeakerInquiry :: PENDING is not an answer");
        }
        return new SpeakerInquiry(id, eventId, speakerId, askedAbout, sentAt, channel, answer, note);
    }

    public SpeakerInquiry withNote(String newNote) {
        return new SpeakerInquiry(id, eventId, speakerId, askedAbout, sentAt, channel, outcome, newNote);
    }

    /** Still waiting for a word. */
    public boolean isOpen() {
        return outcome == InquiryOutcome.PENDING;
    }

    public boolean isAccepted() {
        return outcome == InquiryOutcome.ACCEPTED;
    }

    /** How long this has been waiting — the number a planning tool exists to show. */
    public long daysWaiting(LocalDate today) {
        return isOpen() ? ChronoUnit.DAYS.between(sentAt, today) : 0;
    }
}
