package de.ostfale.greenroom.domain.speakers;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.RuleViolated;
import org.springframework.data.annotation.Id;

import java.util.Set;

/**
 * A small picture of a speaker, for the detail page and later for the announcement.
 *
 * <p>Kept in its own table on purpose: a photo is a few hundred kilobytes, and the list of
 * speakers must not drag them along on every render.
 */
public record SpeakerPhoto(@Id Long id, Long speakerId, String contentType, byte[] data) {

    /** Anything a browser shows without help. */
    private static final Set<String> ACCEPTED =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    /** Small means small. Two megabytes is already generous for a portrait. */
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    public SpeakerPhoto {
        if (speakerId == null) {
            throw new RuleViolated(Rule.PHOTO_NEEDS_A_STORED_SPEAKER);
        }
        if (data == null || data.length == 0) {
            throw new RuleViolated(Rule.PHOTO_IS_EMPTY);
        }
        if (data.length > MAX_BYTES) {
            throw new RuleViolated(Rule.PHOTO_TOO_LARGE, data.length);
        }
        // A client may send no content type at all; Set.of would answer that with an NPE.
        contentType = contentType == null ? "" : contentType.strip().toLowerCase();
        if (!ACCEPTED.contains(contentType)) {
            throw new RuleViolated(Rule.PHOTO_NOT_A_KIND_WE_SHOW, contentType);
        }
        data = data.clone();
    }

    public static SpeakerPhoto of(Long speakerId, String contentType, byte[] data) {
        return new SpeakerPhoto(null, speakerId, contentType, data);
    }

    /** A copy — the bytes of a stored photo are nobody else's to change. */
    @Override
    public byte[] data() {
        return data.clone();
    }

    public int size() {
        return data.length;
    }
}
