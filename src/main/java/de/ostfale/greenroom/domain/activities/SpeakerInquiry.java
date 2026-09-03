package de.ostfale.greenroom.domain.activities;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
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
 *
 * <p>{@code answeredOn} is set with the answer and only with it: waiting and having been
 * answered are the two states of an inquiry, and the date is what makes both of them
 * readable as a history rather than as a status.
 */
public record SpeakerInquiry(
        @Id Long id,
        Long eventId,
        Long speakerId,
        LocalDate askedAbout,
        LocalDate sentAt,
        ContactChannel channel,
        InquiryOutcome outcome,
        LocalDate answeredOn,
        String note) {

    public SpeakerInquiry {
        if (eventId == null) {
            throw new RuleViolated(Rule.INQUIRY_BELONGS_TO_AN_EVENT);
        }
        if (speakerId == null) {
            throw new RuleViolated(Rule.INQUIRY_NEEDS_A_SPEAKER);
        }
        if (sentAt == null) {
            throw new RuleViolated(Rule.INQUIRY_NEEDS_A_SENT_DATE);
        }
        if (channel == null) {
            throw new RuleViolated(Rule.INQUIRY_NEEDS_A_CHANNEL);
        }
        if (outcome == null) {
            throw new RuleViolated(Rule.INQUIRY_NEEDS_AN_OUTCOME);
        }
        if ((outcome == InquiryOutcome.PENDING) != (answeredOn == null)) {
            throw new RuleViolated(Rule.INQUIRY_ANSWER_IS_DATED);
        }
        note = optional(note);
    }

    /** One that has just gone out. Nobody has answered yet, and that is the normal state. */
    public static SpeakerInquiry sent(Long eventId, Long speakerId, LocalDate askedAbout,
                                      LocalDate sentAt, ContactChannel channel) {
        return new SpeakerInquiry(null, eventId, speakerId, askedAbout, sentAt, channel,
                InquiryOutcome.PENDING, null, null);
    }

    /**
     * The answer came in, on the day it came in.
     *
     * @throws RuleViolated if this inquiry was answered before, if the answer is
     *                      {@code PENDING} — which is the absence of an answer rather than
     *                      one — or if the day it arrived is missing
     */
    public SpeakerInquiry answered(InquiryOutcome answer, LocalDate on) {
        if (outcome != InquiryOutcome.PENDING) {
            throw new RuleViolated(Rule.INQUIRY_ALREADY_ANSWERED);
        }
        if (answer == null || answer == InquiryOutcome.PENDING) {
            throw new RuleViolated(Rule.PENDING_IS_NOT_AN_ANSWER);
        }
        if (on == null) {
            throw new RuleViolated(Rule.INQUIRY_ANSWER_IS_DATED);
        }
        return new SpeakerInquiry(id, eventId, speakerId, askedAbout, sentAt, channel, answer, on, note);
    }

    public SpeakerInquiry withNote(String newNote) {
        return new SpeakerInquiry(id, eventId, speakerId, askedAbout, sentAt, channel, outcome,
                answeredOn, newNote);
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
