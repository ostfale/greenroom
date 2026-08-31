package de.ostfale.greenroom.domain.event;

import org.springframework.data.annotation.Id;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static de.ostfale.greenroom.domain.Texts.optional;

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
        List<EventTag> tags) {

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
        tags = tags == null ? List.of() : List.copyOf(withoutDuplicates(tags));
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

    public Event withTags(List<EventTag> newTags) {
        return new Event(id, date, motto, status, mode, locationId, talks, newTags);
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

    public boolean carries(Long tagId) {
        return tags.stream().anyMatch(own -> own.tagId().equals(tagId));
    }

    private static List<EventTag> withoutDuplicates(List<EventTag> tags) {
        List<EventTag> kept = new ArrayList<>();
        for (EventTag tag : tags) {
            if (kept.stream().anyMatch(seen -> seen.tagId().equals(tag.tagId()))) {
                throw new IllegalArgumentException("Event :: the tag " + tag.tagId() + " is on this event twice");
            }
            kept.add(tag);
        }
        return kept;
    }
}
