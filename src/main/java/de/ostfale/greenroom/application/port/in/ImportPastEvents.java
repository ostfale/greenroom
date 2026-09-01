package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.events.Event;

/**
 * Entering the evenings of the last ten years, one form at a time. None of the planning
 * that led to them is entered with it — who was asked when is over, and the tool only has
 * to know that the evening took place.
 */
public interface ImportPastEvents {

    /**
     * Stores a past evening with its talk and its speaker in one step, and moves it as far
     * along as the data carries it: to {@code DONE} when there is a venue and the talk has
     * a title and an abstract, to {@code VENUE_CONFIRMED} or {@code DATE_CONFIRMED}
     * otherwise. The state machine is walked, not bypassed.
     *
     * @throws IllegalArgumentException if the date, the speaker or their address is missing
     */
    Event enter(PastEvening past);
}
