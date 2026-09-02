package de.ostfale.greenroom.domain.notes;

import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

import static de.ostfale.greenroom.domain.Texts.optional;
import static de.ostfale.greenroom.domain.Texts.required;

/**
 * One slip in the box: an idea, written down before it is gone. It points at nothing and
 * nothing points at it — that is the whole design. A note that had to be filed under an
 * evening would be a note that is not written down, because the evening does not exist yet.
 *
 * <p>The stamp is taken when the note is written and is never typed. The title is what the
 * board shows and is therefore required; the text may stay empty — often the title is
 * already the whole thought.
 *
 * <p>Unlike an {@code Activity} a note may be changed and thrown away. It records nothing
 * that happened, only something that was thought, and a thought may come out wrong.
 */
public record Note(
        @Id Long id,
        LocalDateTime writtenAt,
        String title,
        String text) {

    public Note {
        if (writtenAt == null) {
            throw new IllegalArgumentException("Note :: a note is stamped when it is written");
        }
        title = required(title, "Note :: a note needs a title");
        text = optional(text);
    }

    /** A slip that is not in the box yet. */
    public static Note written(LocalDateTime at, String title, String text) {
        return new Note(null, at, title, text);
    }

    /**
     * A thought that came out wrong, put right. The stamp does not move: it says when the
     * note was written, not when it was last touched.
     */
    public Note withTitle(String newTitle) {
        return new Note(id, writtenAt, newTitle, text);
    }

    public Note withText(String newText) {
        return new Note(id, writtenAt, title, newText);
    }
}
