package de.ostfale.greenroom.domain.event;

import static de.ostfale.greenroom.domain.Texts.required;

/**
 * A keyword on an evening — "Spring", "Architektur", "Einsteiger". Written the way it is
 * typed; two tags that differ only in case are the same tag.
 */
public record Tag(String name) {

    public Tag {
        name = required(name, "Tag :: a tag needs a name");
    }

    public static Tag of(String name) {
        return new Tag(name);
    }

    public boolean isSameAs(Tag other) {
        return other != null && name.equalsIgnoreCase(other.name);
    }
}
