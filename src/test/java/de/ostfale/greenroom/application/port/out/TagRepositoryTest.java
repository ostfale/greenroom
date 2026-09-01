package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.domain.tags.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Against a real Postgres — the case-insensitive uniqueness is the part worth proving. */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class TagRepositoryTest {

    @Autowired
    private TagRepository tags;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void emptyTheTable() {
        database.empty();
    }

    @Test
    void storesAndReadsBackATag() {
        Tag saved = tags.save(Tag.named("Spring"));

        assertThat(saved.id()).isNotNull();
        assertThat(tags.findById(saved.id()).orElseThrow().name()).isEqualTo("Spring");
    }

    @Test
    void listsAlphabetically() {
        tags.save(Tag.named("Testing"));
        tags.save(Tag.named("Architektur"));

        assertThat(tags.findAllByOrderByNameAsc()).extracting(Tag::name)
                .containsExactly("Architektur", "Testing");
    }

    @Test
    void findsATagHoweverItWasTyped() {
        tags.save(Tag.named("Spring"));

        assertThat(tags.findByName("SPRING")).isPresent();
        assertThat(tags.findByName("spring")).isPresent();
        assertThat(tags.findByName("Testing")).isEmpty();
    }

    @Test
    void theDatabaseRefusesTheSameWordTwice() {
        tags.save(Tag.named("Spring"));

        assertThatThrownBy(() -> tags.save(Tag.named("SPRING")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
