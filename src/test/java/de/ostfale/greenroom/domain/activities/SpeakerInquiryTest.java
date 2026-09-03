package de.ostfale.greenroom.domain.activities;

import de.ostfale.greenroom.domain.Rule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static de.ostfale.greenroom.Fixtures.EVENING;
import static de.ostfale.greenroom.Violations.ruleBrokenBy;
import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(ruleBrokenBy(() -> SpeakerInquiry.sent(null, SPEAKER, EVENING, SENT, ContactChannel.EMAIL)))
                .isEqualTo(Rule.INQUIRY_BELONGS_TO_AN_EVENT);
        assertThat(ruleBrokenBy(() -> SpeakerInquiry.sent(EVENT, null, EVENING, SENT, ContactChannel.EMAIL)))
                .isEqualTo(Rule.INQUIRY_NEEDS_A_SPEAKER);
    }

    @Test
    void anInquiryIsWrittenDownAfterItWentOut() {
        assertThat(ruleBrokenBy(() -> SpeakerInquiry.sent(EVENT, SPEAKER, EVENING, null, ContactChannel.EMAIL)))
                .isEqualTo(Rule.INQUIRY_NEEDS_A_SENT_DATE);
        assertThat(ruleBrokenBy(() -> SpeakerInquiry.sent(EVENT, SPEAKER, EVENING, SENT, null)))
                .isEqualTo(Rule.INQUIRY_NEEDS_A_CHANNEL);
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

        assertThat(ruleBrokenBy(() -> declined.answered(InquiryOutcome.ACCEPTED, ANSWERED)))
                .isEqualTo(Rule.INQUIRY_ALREADY_ANSWERED);
    }

    @Test
    void pendingIsTheAbsenceOfAnAnswerAndNotOne() {
        SpeakerInquiry inquiry = sent();

        assertThat(ruleBrokenBy(() -> inquiry.answered(InquiryOutcome.PENDING, ANSWERED)))
                .isEqualTo(Rule.PENDING_IS_NOT_AN_ANSWER);
        assertThat(ruleBrokenBy(() -> inquiry.answered(null, ANSWERED)))
                .isEqualTo(Rule.PENDING_IS_NOT_AN_ANSWER);
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

        assertThat(ruleBrokenBy(() -> sent().answered(InquiryOutcome.ACCEPTED, null)))
                .isEqualTo(Rule.INQUIRY_ANSWER_IS_DATED);
    }
}
