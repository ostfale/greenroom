package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.events.Event;

import java.time.LocalDate;
import java.util.List;

/** Everything the web adapter needs to keep the list of evenings. */
public interface ManageEvents {

    /** Newest evening first; the topics without a date sit at the end. */
    List<Event> all();

    /** The same list without what is over and done with. */
    List<Event> allStillOpen();

    /**
     * Stores an evening that has no id yet. Whether another evening is already planned for
     * the same date is a warning, not a rejection — see {@link #alreadyPlannedOn}.
     */
    Event add(Event event);

    /** Evenings already planned for that date, so the caller can warn about a clash. */
    List<Event> alreadyPlannedOn(LocalDate date);
}
