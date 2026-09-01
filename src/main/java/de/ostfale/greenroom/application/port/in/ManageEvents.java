package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.Talk;

import java.util.List;
import java.util.Optional;

/**
 * Everything the web adapter needs to keep the list of evenings. The aggregate itself is
 * the argument and the result — there is no command record that would only be mapped onto
 * the same fields again.
 */
public interface ManageEvents {

    /** Newest evening first; the topics without a date sit at the end. */
    List<Event> all();

    /** The same list without what is over and done with. */
    List<Event> allStillOpen();

    Optional<Event> byId(Long id);

    /**
     * Stores an evening that has no id yet. Whether another evening is already planned for
     * the same date is a warning, not a rejection — see {@link #clashesWith}.
     */
    Event add(Event event);

    /** Stores the changed fields of an evening that is already known. */
    Event change(Event event);

    /**
     * Moves the evening one step on. A step of its own rather than something
     * {@link #change} does, so the guard runs against the status that is stored: the page
     * says where it wants to go, never which status the evening already has.
     *
     * @throws IllegalStateException    if the state machine does not allow the step
     * @throws IllegalArgumentException if the new status wants something the evening has
     *                                  not got — a date, a venue, publishable talks
     */
    Event moveTo(Long eventId, EventStatus target);

    /**
     * The other evenings on that event's date that are still going to happen. Two events
     * on one day are unusual, not forbidden — the page warns, nothing is refused. An
     * evening that has no date yet, and one that was cancelled or is over, clash with
     * nothing.
     */
    List<Event> clashesWith(Event event);

    /** A further talk on the same evening, with the person who gives it. */
    Event addTalk(Long eventId, Talk talk);

    /**
     * Title, abstract and the announced biographies of the talk at that position. The
     * biographies come back one per speaker and in their order; a list that does not match
     * is ignored, because a stale page is worth less than what is stored. Which people give
     * the talk is not the form's to change.
     *
     * @throws IllegalArgumentException if there is no talk at that position, or if the
     *                                  evening is already announced and the change would
     *                                  leave a talk without a title or an abstract
     */
    Event changeTalk(Long eventId, int position, String title, String abstractText,
                     List<String> announcedBios);

    /**
     * Drops the talk at that position.
     *
     * @throws IllegalArgumentException if it was the last one — an evening without a talk
     *                                  is not an evening
     */
    Event removeTalk(Long eventId, int position);
}
