package de.ostfale.greenroom.domain.notes;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain Java, no Spring: what a slip needs and what it may leave out. */
class NoteTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 2, 9, 14);

    @Test
    void aNoteIsStampedWhenItIsWritten() {
        assertThat(Note.written(NOW, "Testcontainers-Abend?", null).writtenAt()).isEqualTo(NOW);

        assertThatThrownBy(() -> Note.written(null, "Testcontainers-Abend?", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stamped when it is written");
    }

    /** The title is what the board shows, so there is no note without one. */
    @Test
    void aNoteNeedsATitle() {
        assertThatThrownBy(() -> Note.written(NOW, "   ", "Der Rest steht hier."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a title");
    }

    /** Often the title is already the whole thought. */
    @Test
    void theTextMayStayEmpty() {
        assertThat(Note.written(NOW, "Testcontainers-Abend?", null).text()).isNull();
        assertThat(Note.written(NOW, "Testcontainers-Abend?", "   ").text()).isNull();
        assertThat(Note.written(NOW, "Testcontainers-Abend?", " Wen fragen? ").text())
                .isEqualTo("Wen fragen?");
    }

    /** Changing a note does not re-date it: the stamp says when it was written. */
    @Test
    void whatIsPutRightKeepsTheStampItWasWrittenWith() {
        Note note = new Note(1L, NOW, "Testcontainers-Abend?", "Wen fragen?");

        Note changed = note.withTitle("Testcontainers-Abend").withText("Anna fragen");

        assertThat(changed.writtenAt()).isEqualTo(NOW);
        assertThat(changed.id()).isEqualTo(1L);
        assertThat(changed.title()).isEqualTo("Testcontainers-Abend");
        assertThat(changed.text()).isEqualTo("Anna fragen");
    }

    @Test
    void aChangeCannotTakeTheTitleAway() {
        Note note = new Note(1L, NOW, "Testcontainers-Abend?", null);

        assertThatThrownBy(() -> note.withTitle("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a title");
    }

    /** The text may be taken away again — it was optional to begin with. */
    @Test
    void aChangeMayEmptyTheText() {
        Note note = new Note(1L, NOW, "Testcontainers-Abend?", "Wen fragen?");

        assertThat(note.withText("").text()).isNull();
    }

    @Test
    void aNoteThatIsNotStoredYetHasNoId() {
        assertThat(Note.written(NOW, "Testcontainers-Abend?", null).id()).isNull();
    }
}
