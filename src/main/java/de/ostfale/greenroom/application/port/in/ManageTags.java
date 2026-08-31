package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.tag.Tag;

import java.util.List;

/** The list of tags, maintained in the settings. */
public interface ManageTags {

    List<Tag> all();

    /**
     * Stores a tag that has no id yet.
     *
     * @throws IllegalArgumentException if that word is already on the list, however it was
     *                                  spelled
     */
    Tag add(Tag tag);
}
