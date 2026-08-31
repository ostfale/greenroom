package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.domain.speaker.Speaker;
import de.ostfale.greenroom.domain.speaker.SpeakerLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static de.ostfale.greenroom.Fixtures.aSpeaker;
import static org.assertj.core.api.Assertions.assertThat;

/** Against a real Postgres — the mapping of the link list is the part worth proving. */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SpeakerRepositoryTest {

    @Autowired
    private SpeakerRepository speakers;

    @Autowired
    private TestDatabase database;

    @BeforeEach
    void emptyTheTable() {
        database.empty();
    }

    @Test
    void storesAndReadsBackASpeaker() {
        Speaker saved = speakers.save(aSpeaker()
                .withContact("Musterfirma GmbH", "max@example.org", null)
                .withBio("Schreibt Java, seit es Generics gibt."));

        assertThat(saved.id()).isNotNull();

        Speaker loaded = speakers.findById(saved.id()).orElseThrow();
        assertThat(loaded.name()).isEqualTo("Max Muster");
        assertThat(loaded.company()).isEqualTo("Musterfirma GmbH");
        assertThat(loaded.email()).isEqualTo("max@example.org");
        assertThat(loaded.bio()).isEqualTo("Schreibt Java, seit es Generics gibt.");
        assertThat(loaded.phone()).isNull();
    }

    @Test
    void keepsTheOrderOfTheLinks() {
        Speaker saved = speakers.save(aSpeaker().withLinks(List.of(
                new SpeakerLink("https://example.org", "Blog"),
                SpeakerLink.of("https://example.org/talk"))));

        Speaker loaded = speakers.findById(saved.id()).orElseThrow();

        assertThat(loaded.links()).extracting(SpeakerLink::url).containsExactly(
                "https://example.org",
                "https://example.org/talk");
        assertThat(loaded.links().getFirst().label()).isEqualTo("Blog");
        assertThat(loaded.links().getLast().label()).isNull();
    }

    @Test
    void replacingTheLinksLeavesNoOrphansBehind() {
        Speaker saved = speakers.save(aSpeaker()
                .withLinks(List.of(SpeakerLink.of("https://example.org"))));

        Speaker updated = speakers.save(saved.withLinks(List.of(SpeakerLink.of("https://example.com"))));

        assertThat(speakers.findById(updated.id()).orElseThrow().links())
                .extracting(SpeakerLink::url)
                .containsExactly("https://example.com");
    }

    @Test
    void deletingASpeakerTakesTheLinksWithIt() {
        Speaker saved = speakers.save(aSpeaker()
                .withLinks(List.of(SpeakerLink.of("https://example.org"))));

        speakers.deleteById(saved.id());

        assertThat(speakers.findById(saved.id())).isEmpty();
    }

    @Test
    void listsAlphabetically() {
        speakers.save(Speaker.of("Zoe Zimmer", "zoe@example.org"));
        speakers.save(Speaker.of("Anna Albers", "anna@example.org"));

        assertThat(speakers.findAllByOrderByNameAsc())
                .extracting(Speaker::name)
                .containsExactly("Anna Albers", "Zoe Zimmer");
    }

    @Test
    void searchesNameAndCompanyIgnoringCase() {
        speakers.save(Speaker.of("Anna Albers", "anna@example.org")
                .withContact("Musterfirma GmbH", "anna@example.org", null));
        speakers.save(Speaker.of("Zoe Zimmer", "zoe@example.org")
                .withContact("Nordsee GmbH", "zoe@example.org", null));

        assertThat(speakers.search("albers")).extracting(Speaker::name).containsExactly("Anna Albers");
        assertThat(speakers.search("NORDSEE")).extracting(Speaker::name).containsExactly("Zoe Zimmer");
        assertThat(speakers.search("e")).hasSize(2);
    }
}
