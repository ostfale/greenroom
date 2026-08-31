package de.ostfale.greenroom.domain.speaker;

import static de.ostfale.greenroom.domain.Texts.optional;
import static de.ostfale.greenroom.domain.Texts.required;

/**
 * Somewhere the speaker can be found: a homepage, a talk recording, a profile.
 * The label is what we show instead of the bare URL.
 */
public record SpeakerLink(
        String url,
        String label) {

    public SpeakerLink {
        url = required(url, "SpeakerLink :: a link needs a URL");
        label = optional(label);
    }

    public static SpeakerLink of(String url) {
        return new SpeakerLink(url, null);
    }

    // What to put between the anchor tags.
    public String display() {
        return label == null ? url : label;
    }
}
