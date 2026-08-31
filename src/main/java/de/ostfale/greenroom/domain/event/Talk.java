package de.ostfale.greenroom.domain.event;

import org.springframework.data.annotation.Id;

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
 */
public record Talk(
        @Id Long id,
        String title,
        String abstractText,
        List<TalkSpeaker> speakers) {

    public Talk {
        if (speakers == null || speakers.isEmpty()) {
            throw new IllegalArgumentException("Talk :: a talk needs at least one speaker");
        }
        Set<Long> seen = new HashSet<>();
        for (TalkSpeaker speaker : speakers) {
            if (!seen.add(speaker.speakerId())) {
                throw new IllegalArgumentException(
                        "Talk :: speaker " + speaker.speakerId() + " is on this talk twice");
            }
        }
        title = optional(title);
        abstractText = optional(abstractText);
        speakers = List.copyOf(speakers);
    }

    /** A talk that is nothing yet but a person we want to hear. */
    public static Talk by(TalkSpeaker speaker) {
        return new Talk(null, null, null, List.of(speaker));
    }

    public Talk withTitle(String newTitle) {
        return new Talk(id, newTitle, abstractText, speakers);
    }

    public Talk withAbstract(String newAbstract) {
        return new Talk(id, title, newAbstract, speakers);
    }

    public Talk withSpeakers(List<TalkSpeaker> newSpeakers) {
        return new Talk(id, title, abstractText, newSpeakers);
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
