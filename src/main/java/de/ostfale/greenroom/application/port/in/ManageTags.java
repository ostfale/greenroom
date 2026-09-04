package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.tags.Tag;

import java.util.List;
import java.util.Optional;

/** The list of tags, maintained in the settings. */
public interface ManageTags {

    List<Tag> all();

    Optional<Tag> byId(Long id);

    /**
     * Stores a tag that has no id yet.
     *
     * @throws RuleViolated if that word is already on the list, however it was spelled
     */
    Tag add(Tag tag);

    /**
     * Renames the tag. What an evening was announced with does not move with it: the event
     * keeps the word it was given, not a reference to this list.
     *
     * @throws RuleViolated if there is no such tag, or if another one already carries
     *                      that word
     */
    Tag rename(Long id, String name);

    /** Drops the tag from the list. Evenings that carry the word keep it. */
    void remove(Long id);
}
