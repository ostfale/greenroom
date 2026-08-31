package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.domain.event.Event;
import de.ostfale.greenroom.domain.event.EventStatus;
import de.ostfale.greenroom.domain.event.EventTag;
import de.ostfale.greenroom.domain.tag.Tag;
import de.ostfale.greenroom.domain.event.Talk;
import de.ostfale.greenroom.domain.event.TalkSpeaker;
import de.ostfale.greenroom.domain.location.ContactPerson;
import de.ostfale.greenroom.domain.location.Location;
import de.ostfale.greenroom.domain.speaker.Speaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Against a real Postgres. The two-level child list — event to talk to talk speaker — is
 * the part worth proving, together with the references that leave the aggregate.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class EventRepositoryTest {

    private static final LocalDate EVENING = LocalDate.of(2026, 9, 24);

    @Autowired
    private EventRepository events;

    @Autowired
    private TagRepository tagsOnTheList;

    @Autowired
    private SpeakerRepository speakers;

    @Autowired
    private LocationRepository locations;

    private Long speakerId;
    private Long locationId;
    private Long tagId;

    // Nothing is committed: @DataJdbcTest rolls every test back, so the events never
    // outlive their test and the other repository tests still find empty tables.
    @BeforeEach
    void aSpeakerAndAVenueToPointAt() {
        speakerId = speakers.save(Speaker.of("Max Muster", "max@example.org")
                .withBio("Schreibt Java, seit es Generics gibt.")).id();
        locationId = locations.save(Location.of("Musterfirma GmbH",
                ContactPerson.of("Anna Albers", "anna@example.org"))).id();
        tagId = tagsOnTheList.save(Tag.named("Java")).id();
    }

    private Talk readyTalk() {
        return Talk.by(TalkSpeaker.of(speakerId))
                .withTitle("Records in Java 25")
                .withAbstract("Warum Records mehr sind als weniger Tippen.");
    }

    @Test
    void storesAndReadsBackAWholeEvening() {
        Event saved = events.save(Event.draftFor(readyTalk())
                .withDate(EVENING)
                .withMotto("Java-Herbst")
                .withLocation(locationId)
                .withTags(List.of(EventTag.of(tagId)))
                .moveTo(EventStatus.DATE_CONFIRMED)
                .moveTo(EventStatus.VENUE_CONFIRMED));

        assertThat(saved.id()).isNotNull();

        Event loaded = events.findById(saved.id()).orElseThrow();
        assertThat(loaded.date()).isEqualTo(EVENING);
        assertThat(loaded.motto()).isEqualTo("Java-Herbst");
        assertThat(loaded.status()).isEqualTo(EventStatus.VENUE_CONFIRMED);
        assertThat(loaded.locationId()).isEqualTo(locationId);
        assertThat(loaded.displayName()).isEqualTo("Java-Herbst");
        assertThat(loaded.tags()).extracting(EventTag::tagId).containsExactly(tagId);
        assertThat(loaded.talks()).singleElement().satisfies(talk -> {
            assertThat(talk.id()).isNotNull();
            assertThat(talk.title()).isEqualTo("Records in Java 25");
            assertThat(talk.speakers()).singleElement().satisfies(speaker -> {
                assertThat(speaker.speakerId()).isEqualTo(speakerId);
                assertThat(speaker.announcedBio()).isNull();
            });
        });
    }

    @Test
    void keepsTheOrderOfTheTalksAndOfTheirSpeakers() {
        Long second = speakers.save(Speaker.of("Zoe Zimmer", "zoe@example.org")).id();

        Event saved = events.save(Event.draftFor(readyTalk())
                .withAdditionalTalk(Talk.by(TalkSpeaker.of(second))
                        .withAdditionalSpeaker(TalkSpeaker.of(speakerId))
                        .withTitle("Zweiter Vortrag")));

        Event loaded = events.findById(saved.id()).orElseThrow();

        assertThat(loaded.talks()).extracting(Talk::title)
                .containsExactly("Records in Java 25", "Zweiter Vortrag");
        assertThat(loaded.talks().getLast().speakers()).extracting(TalkSpeaker::speakerId)
                .containsExactly(second, speakerId);
    }

    @Test
    void theAnnouncedBiographyIsStoredWithTheTalkAndNotWithTheSpeaker() {
        Speaker stored = speakers.findById(speakerId).orElseThrow();
        Event saved = events.save(Event.draftFor(
                Talk.by(TalkSpeaker.announcing(stored)).withTitle("Records in Java 25")));

        speakers.save(stored.withBio("Ganz neue Vita."));

        Event loaded = events.findById(saved.id()).orElseThrow();
        assertThat(loaded.talks().getFirst().speakers().getFirst().announcedBio())
                .isEqualTo("Schreibt Java, seit es Generics gibt.");
        assertThat(speakers.findById(speakerId).orElseThrow().bio()).isEqualTo("Ganz neue Vita.");
    }

    @Test
    void replacingTheTalksLeavesNoOrphansBehind() {
        Event saved = events.save(Event.draftFor(readyTalk()));

        Event updated = events.save(saved.withTalks(
                List.of(Talk.by(TalkSpeaker.of(speakerId)).withTitle("Doch etwas anderes"))));

        assertThat(events.findById(updated.id()).orElseThrow().talks())
                .extracting(Talk::title)
                .containsExactly("Doch etwas anderes");
    }

    @Test
    void deletingAnEveningTakesItsTalksAndTagsWithIt() {
        Event saved = events.save(Event.draftFor(readyTalk()).withTags(List.of(EventTag.of(tagId))));

        events.deleteById(saved.id());

        assertThat(events.findById(saved.id())).isEmpty();
        assertThat(speakers.findById(speakerId)).isPresent();
    }

    @Test
    void aSpeakerWhoOnceGaveATalkCannotBeDeleted() {
        events.save(Event.draftFor(readyTalk()));

        assertThatThrownBy(() -> speakers.deleteById(speakerId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listsTheNewestEveningFirstAndTheTopicsWithoutADateLast() {
        events.save(Event.draftFor(readyTalk()).withDate(EVENING.minusMonths(1)));
        events.save(Event.draftFor(readyTalk()).withDate(EVENING));
        events.save(Event.draftFor(readyTalk()).withMotto("Noch ohne Termin"));

        assertThat(events.allNewestFirst()).extracting(Event::date)
                .containsExactly(EVENING, EVENING.minusMonths(1), null);
    }

    @Test
    void findsEverythingPlannedForOneEvening() {
        events.save(Event.draftFor(readyTalk()).withDate(EVENING).withMotto("Erster"));
        events.save(Event.draftFor(readyTalk()).withDate(EVENING).withMotto("Zweiter"));
        events.save(Event.draftFor(readyTalk()).withDate(EVENING.plusDays(1)));

        assertThat(events.findByDate(EVENING)).extracting(Event::motto)
                .containsExactlyInAnyOrder("Erster", "Zweiter");
    }
}
