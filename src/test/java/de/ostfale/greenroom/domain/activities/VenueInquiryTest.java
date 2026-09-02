package de.ostfale.greenroom.domain.activities;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static de.ostfale.greenroom.Fixtures.EVENING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain Java, no Spring: the mirror image of the speaker inquiry, and where it differs. */
class VenueInquiryTest {

    private static final Long EVENT = 1L;
    private static final Long LOCATION = 2L;
    private static final LocalDate SENT = LocalDate.of(2026, 9, 1);

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
        assertThatThrownBy(() -> VenueInquiry.sent(null, LOCATION, null, EVENING, SENT, ContactChannel.EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to an event");
        assertThatThrownBy(() -> VenueInquiry.sent(EVENT, null, null, EVENING, SENT, ContactChannel.EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("goes to a location");
    }

    /**
     * The one asymmetry to the speaker inquiry, and the reason the two are separate: a
     * speaker is asked whether a proposed date suits them, a place is asked about a day
     * that is already set.
     */
    @Test
    void aPlaceIsOnlyAskedAboutADayThatIsAlreadySet() {
        assertThatThrownBy(() -> VenueInquiry.sent(EVENT, LOCATION, null, null, SENT, ContactChannel.EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a date the evening already has");
    }

    @Test
    void anInquiryIsWrittenDownAfterItWentOut() {
        assertThatThrownBy(() -> VenueInquiry.sent(EVENT, LOCATION, null, EVENING, null, ContactChannel.EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after it went out");
        assertThatThrownBy(() -> VenueInquiry.sent(EVENT, LOCATION, null, EVENING, SENT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a channel");
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
        VenueInquiry answered = sent().answered(InquiryOutcome.ACCEPTED);

        assertThat(answered.outcome()).isEqualTo(InquiryOutcome.ACCEPTED);
        assertThat(answered.isAccepted()).isTrue();
        assertThat(answered.isOpen()).isFalse();
    }

    /** Asking the next place after a refusal is a new inquiry, so both attempts stay. */
    @Test
    void anAnsweredInquiryIsNotAnsweredAgain() {
        VenueInquiry declined = sent().answered(InquiryOutcome.DECLINED);

        assertThatThrownBy(() -> declined.answered(InquiryOutcome.ACCEPTED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already answered");
    }

    @Test
    void pendingIsTheAbsenceOfAnAnswerAndNotOne() {
        VenueInquiry inquiry = sent();

        assertThatThrownBy(() -> inquiry.answered(InquiryOutcome.PENDING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an answer");
        assertThatThrownBy(() -> inquiry.answered(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an answer");
    }

    @Test
    void whatIsOpenCountsTheDaysAndWhatIsAnsweredCountsNothing() {
        VenueInquiry inquiry = sent();

        assertThat(inquiry.daysWaiting(LocalDate.of(2026, 9, 10))).isEqualTo(9);
        assertThat(inquiry.answered(InquiryOutcome.DECLINED).daysWaiting(LocalDate.of(2026, 9, 10)))
                .isZero();
    }

    /** The date is copied, so a refusal keeps what was asked even if the evening moves. */
    @Test
    void theDateThatWasAskedAboutStaysOnTheInquiry() {
        VenueInquiry declined = sent().answered(InquiryOutcome.DECLINED);

        assertThat(declined.forDate()).isEqualTo(EVENING);
    }

    @Test
    void aBlankNoteIsNoNote() {
        assertThat(sent().withNote("   ").note()).isNull();
        assertThat(sent().withNote("Raum nur bis 21 Uhr.").note()).isEqualTo("Raum nur bis 21 Uhr.");
    }
}
