package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.application.port.out.MailMessage;
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
     * Sends the mail and, only once it has gone out, writes the inquiry down. In that
     * order on purpose: an inquiry that was never sent must not appear in the history,
     * while a mail that went out and could not be recorded can still be noted by hand.
     *
     * @throws de.ostfale.greenroom.application.port.out.SendMail.MailNotSent
     *         if the mail server refused it — nothing is recorded then
     */
    SpeakerInquiry sendByMail(SpeakerInquiry inquiry, MailMessage mail);

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
