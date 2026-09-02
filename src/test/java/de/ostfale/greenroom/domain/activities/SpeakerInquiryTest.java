package de.ostfale.greenroom.domain.activities;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static de.ostfale.greenroom.Fixtures.EVENING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain Java, no Spring: what an inquiry is and how often it may be answered. */
class SpeakerInquiryTest {

    private static final Long EVENT = 1L;
    private static final Long SPEAKER = 2L;
    private static final LocalDate SENT = LocalDate.of(2026, 9, 1);
    private static final LocalDate ANSWERED = LocalDate.of(2026, 9, 5);

    private static SpeakerInquiry sent() {
        return SpeakerInquiry.sent(EVENT, SPEAKER, EVENING, SENT, ContactChannel.EMAIL);
    }

    @Test
    void anInquiryThatJustWentOutIsWaiting() {
        SpeakerInquiry inquiry = sent();

        assertThat(inquiry.outcome()).isEqualTo(InquiryOutcome.PENDING);
        assertThat(inquiry.isOpen()).isTrue();
        assertThat(inquiry.isAccepted()).isFalse();
        assertThat(inquiry.id()).isNull();
    }

    @Test
    void anInquiryBelongsToAnEveningAndGoesToSomebody() {
        assertThatThrownBy(() -> SpeakerInquiry.sent(null, SPEAKER, EVENING, SENT, ContactChannel.EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to an event");
        assertThatThrownBy(() -> SpeakerInquiry.sent(EVENT, null, EVENING, SENT, ContactChannel.EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("goes to a speaker");
    }

    @Test
    void anInquiryIsWrittenDownAfterItWentOut() {
        assertThatThrownBy(() -> SpeakerInquiry.sent(EVENT, SPEAKER, EVENING, null, ContactChannel.EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after it went out");
        assertThatThrownBy(() -> SpeakerInquiry.sent(EVENT, SPEAKER, EVENING, SENT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a channel");
    }

    /** A topic without a date is asked all the same: "would you speak for us at all?" */
    @Test
    void thereMayNotBeADateToAskAbout() {
        assertThat(SpeakerInquiry.sent(EVENT, SPEAKER, null, SENT, ContactChannel.PHONE).askedAbout())
                .isNull();
    }

    @Test
    void theAnswerArrivesOnce() {
        SpeakerInquiry answered = sent().answered(InquiryOutcome.ACCEPTED, ANSWERED);

        assertThat(answered.outcome()).isEqualTo(InquiryOutcome.ACCEPTED);
        assertThat(answered.isAccepted()).isTrue();
        assertThat(answered.isOpen()).isFalse();
    }

    /** Asking again after a refusal is a new inquiry, so both attempts stay. */
    @Test
    void anAnsweredInquiryIsNotAnsweredAgain() {
        SpeakerInquiry declined = sent().answered(InquiryOutcome.DECLINED, ANSWERED);

        assertThatThrownBy(() -> declined.answered(InquiryOutcome.ACCEPTED, ANSWERED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already answered");
    }

    @Test
    void pendingIsTheAbsenceOfAnAnswerAndNotOne() {
        SpeakerInquiry inquiry = sent();

        assertThatThrownBy(() -> inquiry.answered(InquiryOutcome.PENDING, ANSWERED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an answer");
        assertThatThrownBy(() -> inquiry.answered(null, ANSWERED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an answer");
    }

    @Test
    void whatIsOpenCountsTheDaysAndWhatIsAnsweredCountsNothing() {
        SpeakerInquiry inquiry = sent();

        assertThat(inquiry.daysWaiting(LocalDate.of(2026, 9, 13))).isEqualTo(12);
        assertThat(inquiry.answered(InquiryOutcome.ACCEPTED, ANSWERED).daysWaiting(LocalDate.of(2026, 9, 13)))
                .isZero();
    }

    @Test
    void aBlankNoteIsNoNote() {
        assertThat(sent().withNote("   ").note()).isNull();
        assertThat(sent().withNote("Nachfassen am Montag.").note())
                .isEqualTo("Nachfassen am Montag.");
    }

    /** Waiting and answered are the two states, and the date is what tells them apart. */
    @Test
    void theAnswerIsDatedAndOnlyTheAnswerIs() {
        assertThat(sent().answeredOn()).isNull();
        assertThat(sent().answered(InquiryOutcome.ACCEPTED, ANSWERED).answeredOn())
                .isEqualTo(ANSWERED);

        assertThatThrownBy(() -> sent().answered(InquiryOutcome.ACCEPTED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("an answer is dated");
    }
}
