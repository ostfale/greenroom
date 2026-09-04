package de.ostfale.greenroom.domain.events;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import org.springframework.data.annotation.Id;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
 * <p>The moderator is a name, not a reference: whoever leads through the evening is
 * usually one of us, and nothing else about them is planned here.
 *
 * <p>There is no title. The evening is called by its {@code motto} if it has one, otherwise
 * by the title of its talk — with one talk nothing is maintained twice, with several the
 * evening gets a name of its own.
 *
 * <p>{@code addressPosition} says which of the venue's addresses this evening was at.
 * A place keeps every address it ever had, and until now no evening said which one it
 * used: one that moved showed its new address on an evening ten years old. Empty means
 * the address the place has today, which is what a planned evening wants — it moves
 * along when the venue does. Set means that one, whatever happened since.
 *
 * <p>A position rather than a copy, and that is the one place this project references
 * what it elsewhere copies: an address is never rewritten and never dropped here, only
 * flagged inactive, so pointing at one is as stable as copying it — and it lets the old
 * address be written down once at the place instead of once per evening.
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
        Integer addressPosition,
        List<Talk> talks) {

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
    }

    /** A topic: somebody we want to hear, and nothing settled yet. */
    public static Event draftFor(Talk talk) {
        return new Event(null, null, null, null, null, EventStatus.DRAFT, EventMode.ONSITE,
                null, null, List.of(talk));
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
        return new Event(id, date, motto, moderator, notes, target, mode, locationId,
                addressPosition, talks);
    }

    public Event withDate(LocalDate newDate) {
        return new Event(id, newDate, motto, moderator, notes, status, mode, locationId,
                addressPosition, talks);
    }

    public Event withMotto(String newMotto) {
        return new Event(id, date, newMotto, moderator, notes, status, mode, locationId,
                addressPosition, talks);
    }

    /**
     * Who leads through the evening. A name and nothing else — the moderator is usually
     * one of us, and a person the tool does not otherwise have to know anything about.
     */
    public Event withModerator(String newModerator) {
        return new Event(id, date, motto, newModerator, notes, status, mode, locationId,
                addressPosition, talks);
    }

    /** Anything worth writing down that has no field of its own. */
    public Event withNotes(String newNotes) {
        return new Event(id, date, motto, moderator, newNotes, status, mode, locationId,
                addressPosition, talks);
    }

    public Event withMode(EventMode newMode) {
        return new Event(id, date, motto, moderator, notes, status, newMode, locationId,
                addressPosition, talks);
    }

    /**
     * The host. A pinned address does not travel with it: a position points into one place's
     * list, and at another place the same number means another building.
     */
    public Event withLocation(Long newLocationId) {
        Integer stays = Objects.equals(locationId, newLocationId) ? addressPosition : null;
        return new Event(id, date, motto, moderator, notes, status, mode, newLocationId,
                stays, talks);
    }

    /**
     * Which of the venue's addresses this evening was at. Empty is the one it has today.
     */
    public Event withAddressAt(Integer position) {
        return new Event(id, date, motto, moderator, notes, status, mode, locationId,
                position, talks);
    }

    public Event withTalks(List<Talk> newTalks) {
        return new Event(id, date, motto, moderator, notes, status, mode, locationId,
                addressPosition, newTalks);
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
     * The words the evening is filed under: those of its talks, each once and in the order
     * the talks stand in. Derived and never stored — a word says what is talked about, and
     * it is the talk that is about something, so an evening carrying its own list beside
     * them would be a second place to maintain and a second place to be wrong.
     */
    public List<String> tags() {
        List<String> words = new ArrayList<>();
        talks.forEach(talk -> talk.tags().forEach(word -> {
            if (words.stream().noneMatch(seen -> seen.equalsIgnoreCase(word))) {
                words.add(word);
            }
        }));
        return words;
    }

    /** Whether any of its talks is filed under that word. */
    public boolean carries(String tag) {
        return talks.stream().anyMatch(talk -> talk.carries(tag));
    }

    /**
     * Whether these words stand anywhere in what the evening says about itself: its own
     * name and notes, the title and abstract of every talk, the biographies they were
     * announced with, and the words it carries. Case is ignored, the way
     * {@link #carries} ignores it. Nothing asked is everything matched.
     *
     * <p>Not the speaker's name or company: those are records of today and belong to the
     * person, not to the evening. What that person was back then stands in the announced
     * biography, and that is searched.
     */
    public boolean mentions(String words) {
        if (words == null || words.isBlank()) {
            return true;
        }
        String looked = words.strip().toLowerCase(Locale.ROOT);
        return holds(motto, looked)
                || holds(notes, looked)
                || talks.stream().anyMatch(talk -> holds(talk.title(), looked)
                        || holds(talk.abstractText(), looked)
                        || talk.tags().stream().anyMatch(tag -> holds(tag, looked))
                        || talk.speakers().stream()
                                .anyMatch(announced -> holds(announced.announcedBio(), looked)));
    }

    private static boolean holds(String field, String looked) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(looked);
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

    /**
     * When the evening begins: the earliest of its talks. Derived, never stored — the time
     * belongs to the talk that starts, and an evening with three of them has no start of
     * its own to keep. Null while no talk says when it begins.
     */
    public LocalTime startsAt() {
        return talks.stream()
                .map(Talk::startsAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /** Several talks make it a special day rather than the regular evening. */
    public boolean hasSeveralTalks() {
        return talks.size() > 1;
    }
}
