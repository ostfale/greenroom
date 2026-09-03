package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.domain.activities.Activity;
import de.ostfale.greenroom.domain.activities.ActivityKind;
import de.ostfale.greenroom.domain.events.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;

import static de.ostfale.greenroom.Fixtures.aReadyTalk;
import static de.ostfale.greenroom.Fixtures.aSpeaker;
import static org.assertj.core.api.Assertions.assertThat;

/** Against a real Postgres: the order of the log, and that it only ever grows. */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ActivityRepositoryTest {

    @Autowired
    private ActivityRepository activities;

    @Autowired
    private EventRepository events;

    @Autowired
    private SpeakerRepository speakers;

    @Autowired
    private TestDatabase database;

    private Long eventId;

    @BeforeEach
    void anEveningToWriteAbout() {
        database.empty();
        Long speakerId = speakers.save(aSpeaker()).id();
        eventId = events.save(Event.draftFor(aReadyTalk(speakerId))).id();
    }

    @Test
    void storesAndReadsBackAnEntry() {
        Activity saved = activities.save(Activity.of(eventId, LocalDate.of(2026, 9, 2),
                ActivityKind.MAIL_SENT, "Termin angefragt"));

        assertThat(saved.id()).isNotNull();
        assertThat(activities.findByEvent(eventId)).singleElement().satisfies(read -> {
            assertThat(read.what()).isEqualTo("Termin angefragt");
            assertThat(read.kind()).isEqualTo(ActivityKind.MAIL_SENT);
        });
    }

    /** A history is read forwards. */
    @Test
    void theLogIsOldestFirst() {
        activities.save(anEntry(LocalDate.of(2026, 9, 8), "zweite"));
        activities.save(anEntry(LocalDate.of(2026, 9, 1), "erste"));

        assertThat(activities.findByEvent(eventId)).extracting(Activity::what)
                .containsExactly("erste", "zweite");
    }

    @Test
    void anEveningSeesOnlyItsOwnEntries() {
        Long other = events.save(Event.draftFor(aReadyTalk(speakers.findAll().getFirst().id()))).id();
        activities.save(anEntry(LocalDate.of(2026, 9, 2), "Antwort gekommen"));

        assertThat(activities.findByEvent(other)).isEmpty();
        assertThat(activities.findByEvent(eventId)).hasSize(1);
    }

    /** The one deletion there is: the evening goes, its history goes with it. */
    @Test
    void theEntriesGoWhenTheEveningDoes() {
        activities.save(anEntry(LocalDate.of(2026, 9, 2), "Antwort gekommen"));

        events.deleteById(eventId);

        assertThat(activities.findByEvent(eventId)).isEmpty();
    }

    /**
     * Append-only is a decision, so the port declares no way to break it: not a
     * CrudRepository, and nothing on it that deletes or hands an entry back for editing.
     */
    @Test
    void thePortOffersNoWayToDeleteOrChange() {
        assertThat(Arrays.stream(ActivityRepository.class.getMethods()).map(Method::getName))
                .containsExactlyInAnyOrder("save", "findByEvent");
    }

    private Activity anEntry(LocalDate on, String what) {
        return Activity.of(eventId, on, ActivityKind.MAIL_RECEIVED, what);
    }
}
