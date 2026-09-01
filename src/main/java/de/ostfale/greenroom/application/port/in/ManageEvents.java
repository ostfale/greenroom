package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;

import java.time.LocalDate;
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
     * the same date is a warning, not a rejection — see {@link #alreadyPlannedOn}.
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

    /** Evenings already planned for that date, so the caller can warn about a clash. */
    List<Event> alreadyPlannedOn(LocalDate date);
}
