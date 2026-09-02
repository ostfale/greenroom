package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.activities.InquiryOutcome;
import de.ostfale.greenroom.domain.activities.VenueInquiry;

import java.util.List;
import java.util.Optional;

/**
 * Asking a place whether the room is free on a day that is already set, and writing down
 * what came back. The counterpart of {@link ManageSpeakerInquiries}, and its own port for
 * the same reason: the two questions are asked in a fixed order and about different things.
 */
public interface ManageVenueInquiries {

    /** Every inquiry that went out for that evening, newest first. */
    List<VenueInquiry> forEvent(Long eventId);

    /**
     * Writes down an inquiry that has gone out.
     *
     * @throws IllegalArgumentException if the evening has no date yet — there is nothing to
     *                                  ask a place about before the day is set
     */
    VenueInquiry send(VenueInquiry inquiry);

    /**
     * Writes down the answer.
     *
     * @throws IllegalStateException    if that inquiry was answered before — asking again
     *                                  is a new inquiry, not a correction of the old one
     * @throws IllegalArgumentException if there is no such inquiry, or the answer is
     *                                  {@code PENDING}
     */
    VenueInquiry answer(Long inquiryId, InquiryOutcome outcome);

    /**
     * The place this evening is currently waiting on, if there is one. Places are asked one
     * after another, so the page can point at the open inquiry before the next goes out —
     * a hint, never a refusal: whoever plans the evening decides.
     */
    Optional<VenueInquiry> waitingOn(Long eventId);
}
