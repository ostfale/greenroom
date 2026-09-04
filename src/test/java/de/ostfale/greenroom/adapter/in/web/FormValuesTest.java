package de.ostfale.greenroom.adapter.in.web;

import de.ostfale.greenroom.domain.Rule;
import de.ostfale.greenroom.domain.events.EventMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static de.ostfale.greenroom.Violations.ruleBrokenBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one place a form field becomes a value, tested the way the records are: plain Java,
 * no context. Two forms build an evening out of these, which is why the table of what each
 * field turns into belongs in one test rather than being asserted a page at a time.
 *
 * <p>An empty field and a missing one mean the same here — the absence of a value. A
 * browser sends the first and a caller that has nothing may pass the second.
 */
class FormValuesTest {

    @Test
    void aFieldThatWasLeftEmptyIsNoValueAtAll() {
        assertThat(FormValues.date(null)).isNull();
        assertThat(FormValues.date("")).isNull();
        assertThat(FormValues.date("   ")).isNull();

        assertThat(FormValues.time(null)).isNull();
        assertThat(FormValues.time("  ")).isNull();

        assertThat(FormValues.speakerIdOrNone(null)).isNull();
        assertThat(FormValues.speakerIdOrNone("  ")).isNull();

        assertThat(FormValues.locationId(null)).isNull();
        assertThat(FormValues.locationId("  ")).isNull();

        assertThat(FormValues.seats(null)).isNull();
        assertThat(FormValues.seats("  ")).isNull();

        assertThat(FormValues.filterNumber(null)).isNull();
        assertThat(FormValues.filterNumber("  ")).isNull();
    }

    /** Spaces around a value are the browser's doing, not the user's answer. */
    @Test
    void whatIsTypedIsReadWithoutTheSpaceAroundIt() {
        assertThat(FormValues.date(" 2026-09-24 ")).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(FormValues.time(" 19:00 ")).isEqualTo(LocalTime.of(19, 0));
        assertThat(FormValues.mode(" ONLINE ")).isEqualTo(EventMode.ONLINE);
        assertThat(FormValues.speakerId(" 7 ")).isEqualTo(7L);
        assertThat(FormValues.locationId(" 8 ")).isEqualTo(8L);
        assertThat(FormValues.seats(" 120 ")).isEqualTo(120);
    }

    /** A browser sends HH:mm; HH:mm:ss is what somebody typed by hand, and it parses too. */
    @Test
    void anHourIsReadWithOrWithoutItsSeconds() {
        assertThat(FormValues.time("19:00")).isEqualTo(LocalTime.of(19, 0));
        assertThat(FormValues.time("19:00:30")).isEqualTo(LocalTime.of(19, 0, 30));
    }

    /** Empty is what an evening ordinarily is. The years worth typing in were the others. */
    @Test
    void anEveningWithoutAModeIsOnSite() {
        assertThat(FormValues.mode(null)).isEqualTo(EventMode.ONSITE);
        assertThat(FormValues.mode("")).isEqualTo(EventMode.ONSITE);
    }

    /** A select that is still standing on nobody is not the same as one that must not. */
    @Test
    void aSpeakerIsAskedForWhereAnEmptySelectWillNotDo() {
        assertThat(ruleBrokenBy(() -> FormValues.speakerId(null)))
                .isEqualTo(Rule.NO_SPEAKER_CHOSEN);
        assertThat(ruleBrokenBy(() -> FormValues.speakerId("")))
                .isEqualTo(Rule.NO_SPEAKER_CHOSEN);
    }

    @Test
    void whatIsNoValueOfItsKindIsRefusedByName() {
        assertThat(ruleBrokenBy(() -> FormValues.date("24.09.2026")))
                .isEqualTo(Rule.DATE_UNREADABLE);
        assertThat(ruleBrokenBy(() -> FormValues.time("halb acht")))
                .isEqualTo(Rule.TIME_UNREADABLE);
        assertThat(ruleBrokenBy(() -> FormValues.mode("REMOTE")))
                .isEqualTo(Rule.EVENT_NEEDS_A_MODE);
        assertThat(ruleBrokenBy(() -> FormValues.speakerIdOrNone("Max")))
                .isEqualTo(Rule.NO_SPEAKER_CHOSEN);
        assertThat(ruleBrokenBy(() -> FormValues.locationId("Musterfirma")))
                .isEqualTo(Rule.NO_LOCATION_CHOSEN);
        assertThat(ruleBrokenBy(() -> FormValues.seats("viele")))
                .isEqualTo(Rule.CAPACITY_IS_A_NUMBER_OF_SEATS);
    }

    /**
     * The one value that refuses nothing. Somebody looking through a list is not filling
     * in a form, so a filter nobody can read narrows by nothing instead of stopping them.
     */
    @Test
    void aFilterThatCannotBeReadNarrowsNothingInsteadOfRefusing() {
        assertThat(FormValues.filterNumber("alle")).isNull();
        assertThat(FormValues.filterNumber(" 12 ")).isEqualTo(12L);
    }
}
