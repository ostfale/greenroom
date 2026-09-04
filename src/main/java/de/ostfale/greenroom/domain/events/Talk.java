package de.ostfale.greenroom.domain.events;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import org.springframework.data.annotation.Id;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static de.ostfale.greenroom.domain.Texts.optional;
import static de.ostfale.greenroom.domain.Texts.required;

/**
 * One presentation inside an event. A talk is never entered on its own:
 * it comes into being together with the person who gives it, because that is how a talk is
 * found — by reading an article, watching a video or hearing someone speak, and then
 * approaching that person.
 *
 * <p>Title and abstract may be missing for a long time. They are what {@code PUBLISHED}
 * waits for, not what the talk needs in order to exist.
 *
 * <p>The field is called {@code abstractText} for one reason only: {@code abstract} is a
 * Java keyword. Everywhere else the word is "abstract".
 *
 * <p>{@code startsAt} is the time this talk begins, and it sits here rather than on the
 * {@link Event} for the reason the evening has several of them: with one talk the evening
 * starts when it does, with three they start one after another. It may be missing — for
 * the years that were written down before anybody noted the time.
 *
 * <p>The tags sit here for the same reason. A word says what is talked about, and it is
 * the talk that is about something — an evening with a Spring talk and a Kotlin talk is
 * not an evening about both. The evening reads its own words off its talks.
 *
 * <p>They are copied from the list in the settings, never referenced: renaming or deleting
 * a tag there must not rewrite what an evening was announced with — the same reason the
 * speaker's biography is copied onto the talk.
 */
public record Talk(
        @Id Long id,
        String title,
        String abstractText,
        LocalTime startsAt,
        List<TalkSpeaker> speakers,
        List<String> tags) {

    /** What a JUG evening begins at unless somebody says otherwise. */
    public static final LocalTime USUALLY = LocalTime.of(19, 0);

    public Talk {
        if (speakers == null || speakers.isEmpty()) {
            throw new RuleViolated(Rule.TALK_NEEDS_A_SPEAKER);
        }
        Set<Long> seen = new HashSet<>();
        for (TalkSpeaker speaker : speakers) {
            if (!seen.add(speaker.speakerId())) {
                throw new RuleViolated(Rule.SPEAKER_TWICE_ON_TALK, speaker.speakerId());
            }
        }
        title = optional(title);
        abstractText = optional(abstractText);
        speakers = List.copyOf(speakers);
        tags = tags == null ? List.of() : List.copyOf(normalised(tags));
    }

    /** A talk that is nothing yet but a person we want to hear, at the usual hour. */
    public static Talk by(TalkSpeaker speaker) {
        return new Talk(null, null, null, USUALLY, List.of(speaker), List.of());
    }

    public Talk withTitle(String newTitle) {
        return new Talk(id, newTitle, abstractText, startsAt, speakers, tags);
    }

    public Talk withAbstract(String newAbstract) {
        return new Talk(id, title, newAbstract, startsAt, speakers, tags);
    }

    /** When it begins. Empty is allowed: an evening of ten years ago may not say. */
    public Talk withStartsAt(LocalTime newStart) {
        return new Talk(id, title, abstractText, newStart, speakers, tags);
    }

    public Talk withSpeakers(List<TalkSpeaker> newSpeakers) {
        return new Talk(id, title, abstractText, startsAt, newSpeakers, tags);
    }

    /** The words this talk is filed under, as they were ticked off the maintained list. */
    public Talk withTags(List<String> newTags) {
        return new Talk(id, title, abstractText, startsAt, speakers, newTags);
    }

    /** Whether this talk is filed under that word. Case is not part of a keyword. */
    public boolean carries(String tag) {
        return tags.stream().anyMatch(own -> own.equalsIgnoreCase(tag));
    }

    /** Adds a second voice — a panel, a pair, a guest. */
    public Talk withAdditionalSpeaker(TalkSpeaker speaker) {
        List<TalkSpeaker> more = new ArrayList<>(speakers);
        more.add(speaker);
        return withSpeakers(more);
    }

    /**
     * Whether this talk no longer holds the evening back: an event may only be published
     * once every talk carries a title and an abstract.
     */
    public boolean isReadyToPublish() {
        return title != null && abstractText != null;
    }

    /** Whether this talk is given by the speaker with that id. */
    public boolean isGivenBy(Long speakerId) {
        return speakers.stream().anyMatch(speaker -> speaker.speakerId().equals(speakerId));
    }

    /** Trimmed, and no word twice however it was capitalised. */
    private static List<String> normalised(List<String> tags) {
        List<String> kept = new ArrayList<>();
        for (String tag : tags) {
            String word = required(tag, Rule.TAG_NEEDS_A_WORD);
            if (kept.stream().anyMatch(seen -> seen.equalsIgnoreCase(word))) {
                throw new RuleViolated(Rule.TAG_TWICE_ON_TALK, word);
            }
            kept.add(word);
        }
        return kept;
    }
}
