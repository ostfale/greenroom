package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.events.Event;
import de.ostfale.greenroom.domain.events.NextStep;

import java.time.LocalDate;
import java.util.List;

/**
 * What the overview shows, assembled once when the page asks. A read model and nothing
 * else: it is never stored, nothing is written through it, and every number in it is
 * counted from the rows that are there.
 *
 * <p>The counts are the smaller half of it on purpose. What a Markdown note cannot do is
 * say which evening is next and what it is still waiting for — that is the reason this
 * page exists, and the numbers ride along at the bottom.
 *
 * @param next     the nearest evening still being planned, or {@code null} when there is none
 * @param open     the other dated evenings still being planned, soonest first
 * @param topics   evenings that have no date yet — the ones waiting for a slot
 * @param counts   how much of everything there is
 * @param venues   where the evenings were held, the most used first
 * @param speakers who gave them, the most often first
 */
public record Dashboard(
        Upcoming next,
        List<Upcoming> open,
        List<Event> topics,
        Counts counts,
        List<Tally> venues,
        List<Tally> speakers) {

    /**
     * An evening with a date, what it waits for, and how far off it is. Negative days are
     * an evening whose day has passed while it was still being planned.
     */
    public record Upcoming(Event evening, NextStep step, long daysAway) {
    }

    public record Counts(
            long events,
            long thisYear,
            long done,
            long speakers,
            long locations,
            long locationsInUse,
            long tags,
            long notes) {
    }

    /**
     * How often a place hosted or a person spoke, and when that last was. {@code last} is
     * null while none of those evenings carries a date.
     */
    public record Tally(Long id, String name, long evenings, LocalDate last) {
    }
}
