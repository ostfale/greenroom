package de.ostfale.greenroom.domain.events;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
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
 * <p>The moderator is a name, not a reference: whoever leads through the evening is
 * usually one of us, and nothing else about them is planned here.
 *
 * <p>There is no title. The evening is called by its {@code motto} if it has one, otherwise
 * by the title of its talk — with one talk nothing is maintained twice, with several the
 * evening gets a name of its own.
 */
public record Event(
        @Id Long id,
        LocalDate date,
        String motto,
        String moderator,
        String notes,
        EventStatus status,
        EventMode mode,
        Long locationId,
        List<Talk> talks,
        List<String> tags) {

    public Event {
        if (status == null) {
            throw new RuleViolated(Rule.EVENT_NEEDS_A_STATUS);
        }
        if (mode == null) {
            throw new RuleViolated(Rule.EVENT_NEEDS_A_MODE);
        }
        if (talks == null || talks.isEmpty()) {
            throw new RuleViolated(Rule.EVENT_NEEDS_ONE_TALK);
        }
        if (status.requiresADate() && date == null) {
            throw new RuleViolated(Rule.EVENT_NEEDS_A_DATE, status);
        }
        if (status.requiresAVenue() && locationId == null) {
            throw new RuleViolated(Rule.EVENT_NEEDS_A_LOCATION, status);
        }
        if (status.requiresPublishableTalks() && !talks.stream().allMatch(Talk::isReadyToPublish)) {
            throw new RuleViolated(Rule.EVENT_NEEDS_PUBLISHABLE_TALKS, status);
        }
        motto = optional(motto);
        moderator = optional(moderator);
        notes = optional(notes);
        talks = List.copyOf(talks);
        tags = tags == null ? List.of() : List.copyOf(normalised(tags));
    }

    /** A topic: somebody we want to hear, and nothing settled yet. */
    public static Event draftFor(Talk talk) {
        return new Event(null, null, null, null, null, EventStatus.DRAFT, EventMode.ONSITE, null, List.of(talk), List.of());
    }

    /**
     * Moves the evening on. The transition itself is guarded by {@link EventStatus}; what
     * the new status requires is guarded by this record.
     *
     * @throws RuleViolated if the state machine does not allow the step
     */
    public Event moveTo(EventStatus target) {
        if (!status.canMoveTo(target)) {
            throw new RuleViolated(Rule.EVENT_DOES_NOT_MOVE, status, target);
        }
        return new Event(id, date, motto, moderator, notes, target, mode, locationId, talks, tags);
    }

    public Event withDate(LocalDate newDate) {
        return new Event(id, newDate, motto, moderator, notes, status, mode, locationId, talks, tags);
    }

    public Event withMotto(String newMotto) {
        return new Event(id, date, newMotto, moderator, notes, status, mode, locationId, talks, tags);
    }

    /**
     * Who leads through the evening. A name and nothing else — the moderator is usually
     * one of us, and a person the tool does not otherwise have to know anything about.
     */
    public Event withModerator(String newModerator) {
        return new Event(id, date, motto, newModerator, notes, status, mode, locationId, talks, tags);
    }

    /** Anything worth writing down that has no field of its own. */
    public Event withNotes(String newNotes) {
        return new Event(id, date, motto, moderator, newNotes, status, mode, locationId, talks, tags);
    }

    public Event withMode(EventMode newMode) {
        return new Event(id, date, motto, moderator, notes, status, newMode, locationId, talks, tags);
    }

    public Event withLocation(Long newLocationId) {
        return new Event(id, date, motto, moderator, notes, status, mode, newLocationId, talks, tags);
    }

    public Event withTalks(List<Talk> newTalks) {
        return new Event(id, date, motto, moderator, notes, status, mode, locationId, newTalks, tags);
    }

    public Event withAdditionalTalk(Talk talk) {
        List<Talk> more = new ArrayList<>(talks);
        more.add(talk);
        return withTalks(more);
    }

    /** The talk at that position — for changing it and putting it back. */
    public Talk talkAt(int position) {
        return talks.get(known(position));
    }

    /** Replaces the talk at that position: a title found, an abstract finally written. */
    public Event withTalkChanged(int position, Talk talk) {
        List<Talk> changed = new ArrayList<>(talks);
        changed.set(known(position), talk);
        return withTalks(changed);
    }

    /**
     * Drops the talk at that position.
     *
     * @throws RuleViolated if it was the last one — an evening without a talk is not an
     *                      evening
     */
    public Event withTalkRemoved(int position) {
        List<Talk> left = new ArrayList<>(talks);
        left.remove(known(position));
        return withTalks(left);
    }

    private int known(int position) {
        if (position < 0 || position >= talks.size()) {
            throw new RuleViolated(Rule.NO_TALK_AT_POSITION, position);
        }
        return position;
    }

    /**
     * The keywords as they were picked from the list in the settings. Copied, not
     * referenced: renaming or deleting a tag later must not rewrite what an evening was
     * announced with — the same reason the speaker's biography is copied onto the talk.
     */
    public Event withTags(List<String> newTags) {
        return new Event(id, date, motto, moderator, notes, status, mode, locationId, talks, newTags);
    }

    public boolean carries(String tag) {
        return tags.stream().anyMatch(own -> own.equalsIgnoreCase(tag));
    }

    /** In that calendar year. A topic without a date belongs to no year yet. */
    public boolean isIn(int year) {
        return date != null && date.getYear() == year;
    }

    /** Whether that person speaks here, on whichever of the talks. */
    public boolean isGivenBy(Long speaker) {
        return speaker != null && talks.stream().anyMatch(talk -> talk.speakers().stream()
                .anyMatch(announced -> speaker.equals(announced.speakerId())));
    }

    public boolean isAt(Long place) {
        return place != null && place.equals(locationId);
    }

    /** What to put in a list or a heading. Null while a single talk still has no title. */
    public String displayName() {
        return motto != null ? motto : nameFromItsTalk();
    }

    /**
     * What the evening is called while it has no motto of its own: the title of its first
     * talk. Not copied into the motto — an evening that borrows the name follows it when
     * the talk is renamed, and nothing is maintained twice.
     */
    public String nameFromItsTalk() {
        return talks.getFirst().title();
    }

    /**
     * The one thing this evening is waiting for, read off what it has and what its status
     * promises. A closed evening waits for nothing; a postponed one waits for a new date,
     * whatever date still stands on it.
     *
     * <p>Today is handed in rather than taken: whether an evening is over is a question
     * about a day, and the record does not decide which day that is.
     */
    public NextStep nextStep(LocalDate today) {
        if (status.isClosed()) {
            return NextStep.NOTHING;
        }
        if (status == EventStatus.POSTPONED || date == null) {
            return NextStep.FIND_A_DATE;
        }
        if (locationId == null) {
            return NextStep.FIND_A_VENUE;
        }
        if (!allTalksAreReadyToPublish()) {
            return NextStep.WRITE_THE_ABSTRACT;
        }
        if (status != EventStatus.PUBLISHED) {
            return NextStep.ANNOUNCE_IT;
        }
        // Announced and the day is gone: somebody has to say that it happened.
        return date.isBefore(today) ? NextStep.CLOSE_IT : NextStep.NOTHING;
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
            String word = required(tag, Rule.TAG_NEEDS_A_WORD);
            if (kept.stream().anyMatch(seen -> seen.equalsIgnoreCase(word))) {
                throw new RuleViolated(Rule.TAG_TWICE_ON_EVENT, word);
            }
            kept.add(word);
        }
        return kept;
    }
}
