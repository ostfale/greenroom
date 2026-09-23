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
 * @param ahead    what stands at the top: the evenings whose announcement is out, the
 *                 nearest date first, each with the names of the people who fill it. An
 *                 announced evening is not "further planned" — the planning is behind it,
 *                 and what is left to do with it is nothing or to close it
 * @param open     the dated evenings nobody has been told about yet, soonest first, each
 *                 with the names of the people who fill it
 * @param topics   evenings that have no date yet — the ones waiting for a slot, each with
 *                 the names of the people who would give it
 * @param counts   how much of everything there is
 * @param venues   where the evenings were held, the most used first, at most ten
 * @param speakers who gave them, the most talks first, at most ten
 */
public record Dashboard(
        List<Upcoming> ahead,
        List<Upcoming> open,
        List<Topic> topics,
        Counts counts,
        List<Tally> venues,
        List<Tally> speakers) {

    /**
     * An evening with a date, what it waits for, how far off it is, and who fills it.
     * Negative days are an evening whose day has passed while it was still being planned.
     * The names come along for the reason they do on a {@link Topic}: the evening holds its
     * people by id alone, and an evening is the people who stand on its stage.
     *
     * <p>{@code venue} is the name of the place the evening is at, null while none is
     * picked. The evening itself holds the place by id alone, and both tiles that read this
     * record want the name — the one on top because where an announced evening happens is
     * half of what there is to know about it, the one below it because a step that reads
     * "Ort nicht bestätigt" is worth more when it says which place is being waited on.
     */
    public record Upcoming(Event evening, NextStep step, long daysAway, List<String> speakers,
                           String venue) {

        /**
         * Whether naming the place is the answer to what this evening waits for. Everywhere
         * else the step says all there is to say and a name beside it is noise — which is
         * the difference between the two tiles, not a difference in what is known.
         */
        public boolean waitsOnItsVenue() {
            return step == NextStep.CONFIRM_THE_VENUE && venue != null;
        }
    }

    /**
     * An evening without a date and who would give it. The names come along because the
     * evening holds its people by id alone, and a topic is worth as much as the person
     * behind it — there is never none of them.
     */
    public record Topic(Event evening, List<String> speakers) {
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
     * How often a place hosted or how many talks a person gave, and when that last was.
     * {@code last} is null while none of those evenings carries a date.
     */
    public record Tally(Long id, String name, long times, LocalDate last) {
    }
}
