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
 */
public record Talk(
        @Id Long id,
        String title,
        String abstractText,
        LocalTime startsAt,
        List<TalkSpeaker> speakers) {

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
    }

    /** A talk that is nothing yet but a person we want to hear, at the usual hour. */
    public static Talk by(TalkSpeaker speaker) {
        return new Talk(null, null, null, USUALLY, List.of(speaker));
    }

    public Talk withTitle(String newTitle) {
        return new Talk(id, newTitle, abstractText, startsAt, speakers);
    }

    public Talk withAbstract(String newAbstract) {
        return new Talk(id, title, newAbstract, startsAt, speakers);
    }

    /** When it begins. Empty is allowed: an evening of ten years ago may not say. */
    public Talk withStartsAt(LocalTime newStart) {
        return new Talk(id, title, abstractText, newStart, speakers);
    }

    public Talk withSpeakers(List<TalkSpeaker> newSpeakers) {
        return new Talk(id, title, abstractText, startsAt, newSpeakers);
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
}
