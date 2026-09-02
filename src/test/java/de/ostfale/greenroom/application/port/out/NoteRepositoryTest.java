package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.domain.notes.Note;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Against a real Postgres: the order of the board, and that a slip can be thrown away. */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class NoteRepositoryTest {

    private static final LocalDateTime MORNING = LocalDateTime.of(2026, 9, 2, 9, 14);

    @Autowired
    private NoteRepository notes;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void anEmptyBox() {
        database.empty();
    }

    @Test
    void storesAndReadsBackANote() {
        Note saved = notes.save(Note.written(MORNING, "Testcontainers-Abend?", "Wen fragen?"));

        assertThat(saved.id()).isNotNull();
        assertThat(notes.findById(saved.id()).orElseThrow()).satisfies(read -> {
            assertThat(read.writtenAt()).isEqualTo(MORNING);
            assertThat(read.title()).isEqualTo("Testcontainers-Abend?");
            assertThat(read.text()).isEqualTo("Wen fragen?");
        });
    }

    @Test
    void aNoteWithoutATextKeepsItEmpty() {
        notes.save(Note.written(MORNING, "Nur ein Stichwort", null));

        assertThat(notes.allNewestFirst()).singleElement().extracting(Note::text).isNull();
    }

    /** The board shows what was just thought of. */
    @Test
    void theBoardIsNewestFirst() {
        notes.save(Note.written(MORNING, "Zuerst", null));
        notes.save(Note.written(MORNING.plusHours(3), "Danach", null));

        assertThat(notes.allNewestFirst()).extracting(Note::title)
                .containsExactly("Danach", "Zuerst");
    }

    /** Two thoughts in the same minute still come back in a fixed order. */
    @Test
    void whatWasWrittenAtTheSameMomentIsStillOrdered() {
        Long first = notes.save(Note.written(MORNING, "Zuerst", null)).id();
        Long second = notes.save(Note.written(MORNING, "Danach", null)).id();

        assertThat(second).isGreaterThan(first);
        assertThat(notes.allNewestFirst()).extracting(Note::title)
                .containsExactly("Danach", "Zuerst");
    }

    /** Unlike an activity, a note may be thrown away: it records a thought, not an event. */
    @Test
    void aNoteCanBeThrownAway() {
        Long id = notes.save(Note.written(MORNING, "Doch nichts", null)).id();

        notes.deleteById(id);

        assertThat(notes.allNewestFirst()).isEmpty();
    }
}
