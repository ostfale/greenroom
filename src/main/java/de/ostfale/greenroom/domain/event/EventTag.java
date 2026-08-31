package de.ostfale.greenroom.domain.event;

/**
 * A tag put on an evening. Points at the maintained list rather than repeating its
 * wording, so renaming a tag in the settings reaches every evening that carries it.
 */
public record EventTag(Long tagId) {

    public EventTag {
        if (tagId == null) {
            throw new IllegalArgumentException("EventTag :: a tag on an event points at a stored tag");
        }
    }

    public static EventTag of(Long tagId) {
        return new EventTag(tagId);
    }
}
