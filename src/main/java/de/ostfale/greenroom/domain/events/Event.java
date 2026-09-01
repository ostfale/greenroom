package de.ostfale.greenroom.domain.events;

import org.springframework.data.annotation.Id;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static de.ostfale.greenroom.domain.Texts.optional;
import static de.ostfale.greenroom.domain.Texts.required;

/**
 * One evening. Never a "Meetup" — that word means meetup.com here.
 *
 * <p>An evening starts as a topic: a {@code DRAFT} with at least one talk, no date and no
 * venue. Date and venue arrive later, and the status says how far that has come. What the
 * status promises, the record enforces: a {@code DATE_CONFIRMED} evening has a date, a
 * {@code VENUE_CONFIRMED} one has a location, and a {@code PUBLISHED} one has talks that
 * are worth announcing.
 *
 * <p>There is no title. The evening is called by its {@code motto} if it has one, otherwise
 * by the title of its talk — with one talk nothing is maintained twice, with several the
 * evening gets a name of its own.
 */
public record Event(
        @Id Long id,
        LocalDate date,
        String motto,
        EventStatus status,
        EventMode mode,
        Long locationId,
        List<Talk> talks,
        List<String> tags) {

    public Event {
        if (status == null) {
            throw new IllegalArgumentException("Event :: an event needs a status");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Event :: an event needs a mode");
        }
        if (talks == null || talks.isEmpty()) {
            throw new IllegalArgumentException("Event :: an event needs at least one talk");
        }
        if (status.requiresADate() && date == null) {
            throw new IllegalArgumentException("Event :: " + status + " needs a date");
        }
        if (status.requiresAVenue() && locationId == null) {
            throw new IllegalArgumentException("Event :: " + status + " needs a location");
        }
        if (status.requiresPublishableTalks() && !talks.stream().allMatch(Talk::isReadyToPublish)) {
            throw new IllegalArgumentException(
                    "Event :: " + status + " needs a title and an abstract on every talk");
        }
        motto = optional(motto);
        talks = List.copyOf(talks);
        tags = tags == null ? List.of() : List.copyOf(normalised(tags));
    }

    /** A topic: somebody we want to hear, and nothing settled yet. */
    public static Event draftFor(Talk talk) {
        return new Event(null, null, null, EventStatus.DRAFT, EventMode.ONSITE, null, List.of(talk), List.of());
    }

    /**
     * Moves the evening on. The transition itself is guarded by {@link EventStatus}; what
     * the new status requires is guarded by this record.
     *
     * @throws IllegalStateException if the state machine does not allow the step
     */
    public Event moveTo(EventStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("Event :: " + status + " does not move to " + target);
        }
        return new Event(id, date, motto, target, mode, locationId, talks, tags);
    }

    public Event withDate(LocalDate newDate) {
        return new Event(id, newDate, motto, status, mode, locationId, talks, tags);
    }

    public Event withMotto(String newMotto) {
        return new Event(id, date, newMotto, status, mode, locationId, talks, tags);
    }

    public Event withMode(EventMode newMode) {
        return new Event(id, date, motto, status, newMode, locationId, talks, tags);
    }

    public Event withLocation(Long newLocationId) {
        return new Event(id, date, motto, status, mode, newLocationId, talks, tags);
    }

    public Event withTalks(List<Talk> newTalks) {
        return new Event(id, date, motto, status, mode, locationId, newTalks, tags);
    }

    public Event withAdditionalTalk(Talk talk) {
        List<Talk> more = new ArrayList<>(talks);
        more.add(talk);
        return withTalks(more);
    }

    /**
     * The keywords as they were picked from the list in the settings. Copied, not
     * referenced: renaming or deleting a tag later must not rewrite what an evening was
     * announced with — the same reason the speaker's biography is copied onto the talk.
     */
    public Event withTags(List<String> newTags) {
        return new Event(id, date, motto, status, mode, locationId, talks, newTags);
    }

    public boolean carries(String tag) {
        return tags.stream().anyMatch(own -> own.equalsIgnoreCase(tag));
    }

    /** What to put in a list or a heading. Null while a single talk still has no title. */
    public String displayName() {
        return motto != null ? motto : talks.getFirst().title();
    }

    /** Whether the announcement could go out: every talk carries a title and an abstract. */
    public boolean allTalksAreReadyToPublish() {
        return talks.stream().allMatch(Talk::isReadyToPublish);
    }

    /** Several talks make it a special day rather than the regular evening. */
    public boolean hasSeveralTalks() {
        return talks.size() > 1;
    }

    private static List<String> normalised(List<String> tags) {
        List<String> kept = new ArrayList<>();
        for (String tag : tags) {
            String word = required(tag, "Event :: a tag needs a word");
            if (kept.stream().anyMatch(seen -> seen.equalsIgnoreCase(word))) {
                throw new IllegalArgumentException("Event :: the tag " + word + " is on this event twice");
            }
            kept.add(word);
        }
        return kept;
    }
}
