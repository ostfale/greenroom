package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.EventStatus;
import de.ostfale.greenroom.domain.events.Talk;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Everything the web adapter needs to keep the list of evenings. The aggregate itself is
 * the argument and the result — there is no command record that would only be mapped onto
 * the same fields again. {@link #enterPast} is the one exception, and
 * {@link PastEvening} carries the reason.
 */
public interface ManageEvents {

    /** Newest evening first; the topics without a date sit at the end. */
    List<Event> all();

    /**
     * The same list, narrowed down. One way of selecting rather than a method per filter:
     * the fields of {@link EventFilter} add up, and "everything" is
     * {@link EventFilter#none()}.
     */
    List<Event> matching(EventFilter filter);

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
     * @throws RuleViolated             if the state machine does not allow the step, or
     *                                  if the new status wants something the evening has
     *                                  not got — a date, a venue, publishable talks
     * @throws IllegalArgumentException if there is no evening with that id
     */
    Event moveTo(Long eventId, EventStatus target);

    /**
     * The other evenings on that event's date that are still going to happen. Two events
     * on one day are unusual, not forbidden — the page warns, nothing is refused. An
     * evening that has no date yet, and one that was cancelled or is over, clash with
     * nothing.
     */
    List<Event> clashesWith(Event event);

    /**
     * An evening that already happened, written down in one go: date, venue, talk, speaker
     * and the status it ended in.
     *
     * <p>It does not walk the state machine, and that is the whole point of the method.
     * The chain — topic, date, venue, announced, over — is how an evening is planned, and
     * for one that is ten years past there is no planning left to retrace: it would be
     * four steps of ceremony for every row of a backlog.
     *
     * <p>Nothing is bypassed by it. What each status promises is enforced by the record
     * itself, not by the transitions: a {@code DONE} evening still has to carry a date, a
     * venue and a title and an abstract on every talk, or it is refused here just as it
     * would be on the last step of the chain.
     *
     * <p>The speaker is found by address or created: somebody who spoke before is not
     * written down a second time.
     *
     * @throws RuleViolated if the evening does not carry what the chosen status promises
     */
    Event enterPast(PastEvening past);

    /** A further talk on the same evening, with the person who gives it. */
    Event addTalk(Long eventId, Talk talk);

    /**
     * Title, abstract, start and the announced biographies of the talk at that position. The
     * biographies come back one per speaker and in their order; a list that does not match
     * is ignored, because a stale page is worth less than what is stored. Which people give
     * the talk is not the form's to change.
     *
     * @throws RuleViolated             if there is no talk at that position, or if the
     *                                  evening is already announced and the change would
     *                                  leave a talk without a title or an abstract
     * @throws IllegalArgumentException if there is no evening with that id
     */
    Event changeTalk(Long eventId, int position, String title, String abstractText,
                     LocalTime startsAt, List<String> announcedBios);

    /**
     * Drops the talk at that position.
     *
     * @throws RuleViolated             if it was the last one — an evening without a talk
     *                                  is not an evening
     * @throws IllegalArgumentException if there is no evening with that id
     */
    Event removeTalk(Long eventId, int position);
}
