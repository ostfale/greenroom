package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.events.Event;

import java.util.List;

import static de.ostfale.greenroom.domain.Texts.optional;

/**
 * What the list is narrowed down to. Every field is optional and they add up: some words
 * to look for, a year, a speaker, a place, a keyword, and whether what is over should be
 * left out.
 *
 * <p>The words are the one that is not a facet: the selects offer what exists, and this
 * asks for what nobody thought to make a field of. "arc42" is on no list.
 *
 * <p>Filtering happens in memory, on the list that was loaded anyway. With a few hundred
 * evenings that is the whole of it — a query built from five optional pieces would be more
 * machinery than the thing it selects.
 *
 * <p>The tags are matched against the words the evening carries, not against the list in
 * the settings, and they are matched the way {@link Event#carries} matches: ignoring case.
 * Several of them widen rather than narrow — an evening passes when it carries any one of
 * them, the way a facet works everywhere else. The fields around them still add up.
 */
public record EventFilter(
        String text,
        boolean hideClosed,
        Integer year,
        Long speakerId,
        Long locationId,
        List<String> tags) {

    public EventFilter {
        text = optional(text);
        tags = tags == null ? List.of() : tags.stream()
                .map(word -> optional(word))
                .filter(word -> word != null)
                .toList();
    }

    /** Everything, in the order it is stored. */
    public static EventFilter none() {
        return new EventFilter(null, false, null, null, null, List.of());
    }

    /**
     * The list as it opens: one year, nothing else narrowed. Its own factory because the
     * page has to tell this apart from a filter somebody picked — the way back is offered
     * for the second, not for the first.
     */
    public static EventFilter forYear(int year) {
        return new EventFilter(null, false, year, null, null, List.of());
    }

    /** Whether anything is narrowed down at all — the page offers a way back only then. */
    public boolean isSet() {
        return text != null || hideClosed || year != null || speakerId != null
                || locationId != null || !tags.isEmpty();
    }

    /**
     * An evening passes when it passes every field that is set. A topic without a date
     * falls out of a year: it belongs to none yet, and that is not the same as belonging
     * to all of them.
     */
    public boolean matches(Event event) {
        return (text == null || event.mentions(text))
                && (!hideClosed || !event.status().isClosed())
                && (year == null || event.isIn(year))
                && (speakerId == null || event.isGivenBy(speakerId))
                && (locationId == null || event.isAt(locationId))
                && (tags.isEmpty() || tags.stream().anyMatch(event::carries));
    }
}
