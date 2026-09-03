package de.ostfale.greenroom.domain.activities;

import de.ostfale.greenroom.domain.Rule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static de.ostfale.greenroom.Fixtures.EVENING;
import static de.ostfale.greenroom.Violations.ruleBrokenBy;
import static org.assertj.core.api.Assertions.assertThat;

/** Plain Java, no Spring: the mirror image of the speaker inquiry, and where it differs. */
class VenueInquiryTest {

    private static final Long EVENT = 1L;
    private static final Long LOCATION = 2L;
    private static final LocalDate SENT = LocalDate.of(2026, 9, 1);
    private static final LocalDate ANSWERED = LocalDate.of(2026, 9, 5);

    private static VenueInquiry sent() {
        return VenueInquiry.sent(EVENT, LOCATION, "Max Muster", EVENING, SENT, ContactChannel.EMAIL);
    }

    @Test
    void anInquiryThatJustWentOutIsWaiting() {
        VenueInquiry inquiry = sent();

        assertThat(inquiry.outcome()).isEqualTo(InquiryOutcome.PENDING);
        assertThat(inquiry.isOpen()).isTrue();
        assertThat(inquiry.isAccepted()).isFalse();
        assertThat(inquiry.id()).isNull();
    }

    @Test
    void anInquiryBelongsToAnEveningAndGoesToAPlace() {
        assertThat(ruleBrokenBy(() -> VenueInquiry.sent(null, LOCATION, null, EVENING, SENT, ContactChannel.EMAIL)))
                .isEqualTo(Rule.INQUIRY_BELONGS_TO_AN_EVENT);
        assertThat(ruleBrokenBy(() -> VenueInquiry.sent(EVENT, null, null, EVENING, SENT, ContactChannel.EMAIL)))
                .isEqualTo(Rule.INQUIRY_NEEDS_A_LOCATION);
    }

    /**
     * The one asymmetry to the speaker inquiry, and the reason the two are separate: a
     * speaker is asked whether a proposed date suits them, a place is asked about a day
     * that is already set.
     */
    @Test
    void aPlaceIsOnlyAskedAboutADayThatIsAlreadySet() {
        assertThat(ruleBrokenBy(() -> VenueInquiry.sent(EVENT, LOCATION, null, null, SENT, ContactChannel.EMAIL)))
                .isEqualTo(Rule.VENUE_INQUIRY_NEEDS_A_DATE);
    }

    @Test
    void anInquiryIsWrittenDownAfterItWentOut() {
        assertThat(ruleBrokenBy(() -> VenueInquiry.sent(EVENT, LOCATION, null, EVENING, null, ContactChannel.EMAIL)))
                .isEqualTo(Rule.INQUIRY_NEEDS_A_SENT_DATE);
        assertThat(ruleBrokenBy(() -> VenueInquiry.sent(EVENT, LOCATION, null, EVENING, SENT, null)))
                .isEqualTo(Rule.INQUIRY_NEEDS_A_CHANNEL);
    }

    /** Whom we wrote to is copied, not looked up — and may well be nobody in particular. */
    @Test
    void theContactIsKeptAsItWasAndMayBeMissing() {
        assertThat(sent().contactName()).isEqualTo("Max Muster");
        assertThat(VenueInquiry.sent(EVENT, LOCATION, "  ", EVENING, SENT, ContactChannel.PHONE)
                .contactName()).isNull();
    }

    @Test
    void theAnswerArrivesOnce() {
        VenueInquiry answered = sent().answered(InquiryOutcome.ACCEPTED, ANSWERED);

        assertThat(answered.outcome()).isEqualTo(InquiryOutcome.ACCEPTED);
        assertThat(answered.isAccepted()).isTrue();
        assertThat(answered.isOpen()).isFalse();
    }

    /** Asking the next place after a refusal is a new inquiry, so both attempts stay. */
    @Test
    void anAnsweredInquiryIsNotAnsweredAgain() {
        VenueInquiry declined = sent().answered(InquiryOutcome.DECLINED, ANSWERED);

        assertThat(ruleBrokenBy(() -> declined.answered(InquiryOutcome.ACCEPTED, ANSWERED)))
                .isEqualTo(Rule.INQUIRY_ALREADY_ANSWERED);
    }

    @Test
    void pendingIsTheAbsenceOfAnAnswerAndNotOne() {
        VenueInquiry inquiry = sent();

        assertThat(ruleBrokenBy(() -> inquiry.answered(InquiryOutcome.PENDING, ANSWERED)))
                .isEqualTo(Rule.PENDING_IS_NOT_AN_ANSWER);
        assertThat(ruleBrokenBy(() -> inquiry.answered(null, ANSWERED)))
                .isEqualTo(Rule.PENDING_IS_NOT_AN_ANSWER);
    }

    @Test
    void whatIsOpenCountsTheDaysAndWhatIsAnsweredCountsNothing() {
        VenueInquiry inquiry = sent();

        assertThat(inquiry.daysWaiting(LocalDate.of(2026, 9, 10))).isEqualTo(9);
        assertThat(inquiry.answered(InquiryOutcome.DECLINED, ANSWERED).daysWaiting(LocalDate.of(2026, 9, 10)))
                .isZero();
    }

    /** The date is copied, so a refusal keeps what was asked even if the evening moves. */
    @Test
    void theDateThatWasAskedAboutStaysOnTheInquiry() {
        VenueInquiry declined = sent().answered(InquiryOutcome.DECLINED, ANSWERED);

        assertThat(declined.forDate()).isEqualTo(EVENING);
    }

    @Test
    void aBlankNoteIsNoNote() {
        assertThat(sent().withNote("   ").note()).isNull();
        assertThat(sent().withNote("Raum nur bis 21 Uhr.").note()).isEqualTo("Raum nur bis 21 Uhr.");
    }

    /** Waiting and answered are the two states, and the date is what tells them apart. */
    @Test
    void theAnswerIsDatedAndOnlyTheAnswerIs() {
        assertThat(sent().answeredOn()).isNull();
        assertThat(sent().answered(InquiryOutcome.ACCEPTED, ANSWERED).answeredOn())
                .isEqualTo(ANSWERED);

        assertThat(ruleBrokenBy(() -> sent().answered(InquiryOutcome.ACCEPTED, null)))
                .isEqualTo(Rule.INQUIRY_ANSWER_IS_DATED);
    }
}
