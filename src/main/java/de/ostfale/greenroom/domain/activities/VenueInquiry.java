package de.ostfale.greenroom.domain.activities;

import org.springframework.data.annotation.Id;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static de.ostfale.greenroom.domain.Texts.optional;

/**
 * A request that went out to a place, with what came back. The second question of an
 * evening: the speakers have said yes, so the day is set, and what is asked is whether a
 * room is free on it.
 *
 * <p>The mirror image of {@link SpeakerInquiry}, and a separate aggregate for exactly that
 * reason: there the person is fixed and the date is asked, here the date is fixed and the
 * place is asked. They share {@link ContactChannel} and {@link InquiryOutcome}, nothing
 * else.
 *
 * <p>{@code forDate} is not optional, and that one difference is the whole asymmetry: a
 * place cannot be asked about an evening that has no day yet, while a speaker can well be
 * asked about a date that is still a proposal. It is a copy all the same — if the evening
 * later moves, what was asked stays what was asked.
 *
 * <p>{@code contactName} is copied for the reason the announced biography is copied: the
 * person who leaves the company must not rewrite who was written to back then.
 *
 * <p>{@code answeredOn} is set with the answer and only with it, so the evening reads as
 * a history and not only as a state.
 *
 * <p>Places are asked one after another, but that order is a matter of judgement rather
 * than an invariant: the page points at an inquiry that is still open, and whoever plans
 * the evening decides whether to ask the next one anyway.
 */
public record VenueInquiry(
        @Id Long id,
        Long eventId,
        Long locationId,
        String contactName,
        LocalDate forDate,
        LocalDate sentAt,
        ContactChannel channel,
        InquiryOutcome outcome,
        LocalDate answeredOn,
        String note) {

    public VenueInquiry {
        if (eventId == null) {
            throw new IllegalArgumentException("VenueInquiry :: an inquiry belongs to an event");
        }
        if (locationId == null) {
            throw new IllegalArgumentException("VenueInquiry :: an inquiry goes to a location");
        }
        if (forDate == null) {
            throw new IllegalArgumentException("VenueInquiry :: a place is asked about a date the evening already has");
        }
        if (sentAt == null) {
            throw new IllegalArgumentException("VenueInquiry :: an inquiry is logged after it went out");
        }
        if (channel == null) {
            throw new IllegalArgumentException("VenueInquiry :: an inquiry needs a channel");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("VenueInquiry :: an inquiry needs an outcome");
        }
        if ((outcome == InquiryOutcome.PENDING) != (answeredOn == null)) {
            throw new IllegalArgumentException("VenueInquiry :: an answer is dated, and only an answer is");
        }
        contactName = optional(contactName);
        note = optional(note);
    }

    /** One that has just gone out. Nobody has answered yet, and that is the normal state. */
    public static VenueInquiry sent(Long eventId, Long locationId, String contactName,
                                    LocalDate forDate, LocalDate sentAt, ContactChannel channel) {
        return new VenueInquiry(null, eventId, locationId, contactName, forDate, sentAt, channel,
                InquiryOutcome.PENDING, null, null);
    }

    /**
     * The answer came in, on the day it came in.
     *
     * @throws IllegalStateException    if this inquiry was answered before
     * @throws IllegalArgumentException if the answer is {@code PENDING}, which is the
     *                                  absence of an answer rather than one, or if the day
     *                                  it arrived is missing
     */
    public VenueInquiry answered(InquiryOutcome answer, LocalDate on) {
        if (outcome != InquiryOutcome.PENDING) {
            throw new IllegalStateException("VenueInquiry :: this inquiry was already answered");
        }
        if (answer == null || answer == InquiryOutcome.PENDING) {
            throw new IllegalArgumentException("VenueInquiry :: PENDING is not an answer");
        }
        if (on == null) {
            throw new IllegalArgumentException("VenueInquiry :: an answer is dated, and only an answer is");
        }
        return new VenueInquiry(id, eventId, locationId, contactName, forDate, sentAt, channel,
                answer, on, note);
    }

    public VenueInquiry withNote(String newNote) {
        return new VenueInquiry(id, eventId, locationId, contactName, forDate, sentAt, channel,
                outcome, answeredOn, newNote);
    }

    /** Still waiting for a word — the one place that is currently being waited for. */
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
