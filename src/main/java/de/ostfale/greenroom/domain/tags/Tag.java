package de.ostfale.greenroom.domain.tags;

import de.ostfale.greenroom.domain.Rule;
import org.springframework.data.annotation.Id;

import static de.ostfale.greenroom.domain.Texts.required;

/**
 * A keyword for an evening — "Spring", "Architektur", "Einsteiger". Maintained as a list in
 * the settings, so the same idea is not spelled three ways across the years.
 *
 * <p>Written the way it is typed; two tags that differ only in case are the same tag.
 */
public record Tag(@Id Long id, String name) {

    public Tag {
        name = required(name, Rule.TAG_NEEDS_A_NAME);
    }

    /** A new tag, not yet stored. */
    public static Tag named(String name) {
        return new Tag(null, name);
    }

    public boolean isSameAs(Tag other) {
        return other != null && name.equalsIgnoreCase(other.name);
    }
}
