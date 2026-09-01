package de.ostfale.greenroom.application.port.out;

import de.ostfale.greenroom.TestDatabase;
import de.ostfale.greenroom.TestcontainersConfiguration;
import de.ostfale.greenroom.domain.activities.ContactChannel;
import de.ostfale.greenroom.domain.activities.InquiryOutcome;
import de.ostfale.greenroom.domain.activities.SpeakerInquiry;
import de.ostfale.greenroom.domain.events.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static de.ostfale.greenroom.Fixtures.EVENING;
import static de.ostfale.greenroom.Fixtures.aReadyTalk;
import static de.ostfale.greenroom.Fixtures.aSpeaker;
import static org.assertj.core.api.Assertions.assertThat;

/** Against a real Postgres: the order of the list and the hold on a speaker. */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SpeakerInquiryRepositoryTest {

    @Autowired
    private SpeakerInquiryRepository inquiries;

    @Autowired
    private EventRepository events;

    @Autowired
    private SpeakerRepository speakers;

    @Autowired
    private TestDatabase database;

    private Long speakerId;
    private Long eventId;

    @BeforeEach
    void anEveningToAskAbout() {
        database.empty();
        speakerId = speakers.save(aSpeaker()).id();
        eventId = events.save(Event.draftFor(aReadyTalk(speakerId)).withDate(EVENING)).id();
    }

    @Test
    void storesAndReadsBackAnInquiry() {
        SpeakerInquiry saved = inquiries.save(SpeakerInquiry.sent(
                eventId, speakerId, EVENING, LocalDate.of(2026, 9, 1), ContactChannel.EMAIL));

        assertThat(saved.id()).isNotNull();
        SpeakerInquiry read = inquiries.findById(saved.id()).orElseThrow();
        assertThat(read.askedAbout()).isEqualTo(EVENING);
        assertThat(read.channel()).isEqualTo(ContactChannel.EMAIL);
        assertThat(read.outcome()).isEqualTo(InquiryOutcome.PENDING);
    }

    @Test
    void theListOfAnEveningIsNewestFirst() {
        inquiries.save(SpeakerInquiry.sent(eventId, speakerId, EVENING,
                LocalDate.of(2026, 9, 1), ContactChannel.EMAIL).withNote("erste"));
        inquiries.save(SpeakerInquiry.sent(eventId, speakerId, EVENING,
                LocalDate.of(2026, 9, 8), ContactChannel.PHONE).withNote("zweite"));

        assertThat(inquiries.findByEvent(eventId)).extracting(SpeakerInquiry::note)
                .containsExactly("zweite", "erste");
    }

    @Test
    void anEveningSeesOnlyItsOwnInquiries() {
        Long other = events.save(Event.draftFor(aReadyTalk(speakerId))).id();
        inquiries.save(SpeakerInquiry.sent(eventId, speakerId, EVENING,
                LocalDate.of(2026, 9, 1), ContactChannel.EMAIL));

        assertThat(inquiries.findByEvent(other)).isEmpty();
        assertThat(inquiries.findByEvent(eventId)).hasSize(1);
    }

    @Test
    void aSpeakerThatWasAskedIsKnownToBeAsked() {
        assertThat(inquiries.wasAsked(speakerId)).isFalse();

        inquiries.save(SpeakerInquiry.sent(eventId, speakerId, EVENING,
                LocalDate.of(2026, 9, 1), ContactChannel.EMAIL));

        assertThat(inquiries.wasAsked(speakerId)).isTrue();
    }

    /** The evening goes, its correspondence goes with it. */
    @Test
    void theInquiriesGoWhenTheEveningDoes() {
        inquiries.save(SpeakerInquiry.sent(eventId, speakerId, EVENING,
                LocalDate.of(2026, 9, 1), ContactChannel.EMAIL));

        events.deleteById(eventId);

        assertThat(inquiries.findByEvent(eventId)).isEmpty();
    }
}
