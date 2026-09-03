package de.ostfale.greenroom.domain.activities;

import de.ostfale.greenroom.domain.Rule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static de.ostfale.greenroom.Violations.ruleBrokenBy;
import static org.assertj.core.api.Assertions.assertThat;

/** Plain Java, no Spring: what an entry is, and what a direction commits it to. */
class ActivityTest {

    private static final Long EVENT = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 2);

    @Test
    void anEntryBelongsToAnEveningAndIsDated() {
        assertThat(ruleBrokenBy(() -> Activity.noted(null, DAY, "Beamer defekt")))
                .isEqualTo(Rule.ACTIVITY_BELONGS_TO_AN_EVENT);
        assertThat(ruleBrokenBy(() -> Activity.noted(EVENT, null, "Beamer defekt")))
                .isEqualTo(Rule.ACTIVITY_IS_DATED);
    }

    @Test
    void anEntrySaysWhatHappened() {
        assertThat(ruleBrokenBy(() -> Activity.noted(EVENT, DAY, "   ")))
                .isEqualTo(Rule.ACTIVITY_NEEDS_A_TEXT);
    }

    /** The direction is what separates something we did from something we wrote down. */
    @Test
    void aNoteWentNowhereSoItHasNoChannel() {
        assertThat(Activity.noted(EVENT, DAY, "Beamer defekt").channel()).isNull();

        assertThat(ruleBrokenBy(() -> new Activity(null, EVENT, DAY, ActivityDirection.NOTE,
                ContactChannel.EMAIL, "Beamer defekt")))
                .isEqualTo(Rule.NOTE_HAS_NO_CHANNEL);
    }

    @Test
    void somethingThatWentOutOrCameInSaysHow() {
        assertThat(Activity.over(EVENT, DAY, ActivityDirection.OUTGOING, ContactChannel.EMAIL,
                "Sponsor angeschrieben").channel()).isEqualTo(ContactChannel.EMAIL);

        assertThat(ruleBrokenBy(() -> new Activity(null, EVENT, DAY, ActivityDirection.INCOMING,
                null, "Sponsor sagt zu")))
                .isEqualTo(Rule.ACTIVITY_NEEDS_A_CHANNEL);
    }

    /**
     * Append-only is carried by the shape: a record with no {@code with…} method is a line
     * that cannot be rewritten. What is wrong is answered by the next line.
     */
    @Test
    void thereIsNoWayToChangeAnEntry() {
        assertThat(Activity.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("with"));
    }

    @Test
    void anEntryThatIsNotStoredYetHasNoId() {
        assertThat(Activity.noted(EVENT, DAY, "Beamer defekt").id()).isNull();
    }
}
