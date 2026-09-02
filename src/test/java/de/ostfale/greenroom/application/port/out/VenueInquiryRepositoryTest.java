package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.domain.activities.ContactChannel;
import de.ostfale.greenroom.domain.activities.InquiryOutcome;
import de.ostfale.greenroom.domain.activities.VenueInquiry;
import de.ostfale.greenroom.domain.events.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static de.ostfale.greenroom.Fixtures.EVENING;
import static de.ostfale.greenroom.Fixtures.aLocation;
import static de.ostfale.greenroom.Fixtures.aReadyTalk;
import static de.ostfale.greenroom.Fixtures.aSpeaker;
import static org.assertj.core.api.Assertions.assertThat;

/** Against a real Postgres: the order of the list and the hold on a place. */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class VenueInquiryRepositoryTest {

    @Autowired
    private VenueInquiryRepository inquiries;

    @Autowired
    private EventRepository events;

    @Autowired
    private SpeakerRepository speakers;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private TestDatabase database;

    private Long locationId;
    private Long eventId;

    @BeforeEach
    void anEveningWithADateToAskAPlaceAbout() {
        database.empty();
        Long speakerId = speakers.save(aSpeaker()).id();
        locationId = locations.save(aLocation()).id();
        eventId = events.save(Event.draftFor(aReadyTalk(speakerId)).withDate(EVENING)).id();
    }

    private VenueInquiry sentOn(LocalDate day, String note) {
        return VenueInquiry.sent(eventId, locationId, "Max Muster", EVENING, day, ContactChannel.EMAIL)
                .withNote(note);
    }

    @Test
    void storesAndReadsBackAnInquiry() {
        VenueInquiry saved = inquiries.save(sentOn(LocalDate.of(2026, 9, 1), null));

        assertThat(saved.id()).isNotNull();
        VenueInquiry read = inquiries.findById(saved.id()).orElseThrow();
        assertThat(read.forDate()).isEqualTo(EVENING);
        assertThat(read.contactName()).isEqualTo("Max Muster");
        assertThat(read.channel()).isEqualTo(ContactChannel.EMAIL);
        assertThat(read.outcome()).isEqualTo(InquiryOutcome.PENDING);
    }

    @Test
    void theListOfAnEveningIsNewestFirst() {
        inquiries.save(sentOn(LocalDate.of(2026, 9, 1), "erste"));
        inquiries.save(sentOn(LocalDate.of(2026, 9, 8), "zweite"));

        assertThat(inquiries.findByEvent(eventId)).extracting(VenueInquiry::note)
                .containsExactly("zweite", "erste");
    }

    @Test
    void anEveningSeesOnlyItsOwnInquiries() {
        Long other = events.save(Event.draftFor(aReadyTalk(speakers.findAll().getFirst().id()))).id();
        inquiries.save(sentOn(LocalDate.of(2026, 9, 1), null));

        assertThat(inquiries.findByEvent(other)).isEmpty();
        assertThat(inquiries.findByEvent(eventId)).hasSize(1);
    }

    @Test
    void aPlaceThatWasAskedIsKnownToBeAsked() {
        assertThat(inquiries.wasAsked(locationId)).isFalse();

        inquiries.save(sentOn(LocalDate.of(2026, 9, 1), null));

        assertThat(inquiries.wasAsked(locationId)).isTrue();
    }

    /** The evening goes, its correspondence goes with it. */
    @Test
    void theInquiriesGoWhenTheEveningDoes() {
        inquiries.save(sentOn(LocalDate.of(2026, 9, 1), null));

        events.deleteById(eventId);

        assertThat(inquiries.findByEvent(eventId)).isEmpty();
    }

    /** The answer replaces nothing: it is written onto the inquiry that was waiting. */
    @Test
    void theAnswerIsStoredOnTheInquiryThatWasSent() {
        VenueInquiry open = inquiries.save(sentOn(LocalDate.of(2026, 9, 1), null));

        inquiries.save(open.answered(InquiryOutcome.DECLINED, LocalDate.of(2026, 9, 5)));

        assertThat(inquiries.findByEvent(eventId)).singleElement()
                .extracting(VenueInquiry::outcome).isEqualTo(InquiryOutcome.DECLINED);
    }
}
