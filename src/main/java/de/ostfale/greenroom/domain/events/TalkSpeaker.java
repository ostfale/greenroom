package de.ostfale.greenroom.domain.events;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.speakers.Speaker;

import static de.ostfale.greenroom.domain.Texts.optional;

/**
 * A speaker as they were announced for this talk. The reference points at the
 * {@link Speaker} aggregate, which carries the current state; the biography is a copy,
 * taken when the talk was announced and never updated from the speaker again.
 *
 * <p>That is the whole reason this record exists: the person keeps changing jobs and
 * rewriting their bio, but what stood on the invitation for that evening stays.
 */
public record TalkSpeaker(Long speakerId, String announcedBio) {

    public TalkSpeaker {
        if (speakerId == null) {
            throw new RuleViolated(Rule.SPEAKER_NOT_STORED);
        }
        announcedBio = optional(announcedBio);
    }

    /** The speaker with the biography they have right now — this is the copy being made. */
    public static TalkSpeaker announcing(Speaker speaker) {
        if (speaker.id() == null) {
            throw new RuleViolated(Rule.SPEAKER_NOT_STORED);
        }
        return new TalkSpeaker(speaker.id(), speaker.bio());
    }

    /** A speaker whose biography was not written down for this evening. */
    public static TalkSpeaker of(Long speakerId) {
        return new TalkSpeaker(speakerId, null);
    }

    public TalkSpeaker withAnnouncedBio(String newBio) {
        return new TalkSpeaker(speakerId, newBio);
    }
}
