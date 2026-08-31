package de.ostfale.greenroom.domain.speaker;

/**
 * Somewhere the speaker can be found: a homepage, a talk recording, a profile.
 * The label is what we show instead of the bare URL.
 */
public record SpeakerLink(
        String url,
        String label) {

    public SpeakerLink {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("a link needs a URL");
        }
        url = url.strip();
        label = label == null || label.isBlank() ? null : label.strip();
    }

    public static SpeakerLink of(String url) {
        return new SpeakerLink(url, null);
    }

    // What to put between the anchor tags.
    public String display() {
        return label == null ? url : label;
    }
}
