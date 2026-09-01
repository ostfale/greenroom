package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.activities.InquiryOutcome;
import de.ostfale.greenroom.domain.activities.SpeakerInquiry;

import java.util.List;

/**
 * Asking a speaker whether a date suits them, and writing down what came back. Its own
 * port rather than part of {@code ManageEvents}: an inquiry outlives the question it was
 * about, and the evening does not carry the correspondence.
 */
public interface ManageSpeakerInquiries {

    /** Every inquiry that went out for that evening, newest first. */
    List<SpeakerInquiry> forEvent(Long eventId);

    /** Writes down an inquiry that has gone out. */
    SpeakerInquiry send(SpeakerInquiry inquiry);

    /**
     * Writes down the answer.
     *
     * @throws IllegalStateException    if that inquiry was answered before — asking again
     *                                  is a new inquiry, not a correction of the old one
     * @throws IllegalArgumentException if there is no such inquiry, or the answer is
     *                                  {@code PENDING}
     */
    SpeakerInquiry answer(Long inquiryId, InquiryOutcome outcome);
}
