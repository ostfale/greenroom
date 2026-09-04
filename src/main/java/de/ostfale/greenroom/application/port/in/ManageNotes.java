package de.ostfale.greenroom.application.port.in;

import de.ostfale.greenroom.domain.RuleViolated;
import de.ostfale.greenroom.domain.notes.Note;

import java.util.List;
import java.util.Optional;

/**
 * The slip box: write something down, throw it away again. Nothing else — a note that
 * needed maintaining would be one more thing to maintain.
 */
public interface ManageNotes {

    /** Newest first. */
    List<Note> all();

    /**
     * Writes a note down and stamps it with the moment. Two texts rather than the
     * aggregate, because the stamp is not the caller's to make.
     *
     * @throws RuleViolated if there is no title
     */
    Note add(String title, String text);

    Optional<Note> byId(Long id);

    /**
     * Puts a thought right. The stamp stays what it was — it says when the note was
     * written, not when it was last touched.
     *
     * @throws RuleViolated if there is no such note, or no title
     */
    Note change(Long id, String title, String text);

    /** Throws the slip away. A note that is not there is not an error. */
    void remove(Long id);
}
