package de.ostfale.greenroom.domain.activities;

import de.ostfale.greenroom.domain.Rule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static de.ostfale.greenroom.Violations.ruleBrokenBy;
import static org.assertj.core.api.Assertions.assertThat;

/** Plain Java, no Spring: what a line of history is, and what it insists on. */
class ActivityTest {

    private static final Long EVENT = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 2);

    @Test
    void anEntryBelongsToAnEveningAndIsDated() {
        assertThat(ruleBrokenBy(() -> Activity.of(null, DAY, ActivityKind.MAIL_SENT, "Termin angefragt")))
                .isEqualTo(Rule.ACTIVITY_BELONGS_TO_AN_EVENT);
        assertThat(ruleBrokenBy(() -> Activity.of(EVENT, null, ActivityKind.MAIL_SENT, "Termin angefragt")))
                .isEqualTo(Rule.ACTIVITY_IS_DATED);
    }

    @Test
    void anEntrySaysWhetherTheMailWentOrCame() {
        assertThat(Activity.of(EVENT, DAY, ActivityKind.MAIL_RECEIVED, "Max sagt zu").kind())
                .isEqualTo(ActivityKind.MAIL_RECEIVED);

        assertThat(ruleBrokenBy(() -> Activity.of(EVENT, DAY, null, "Max sagt zu")))
                .isEqualTo(Rule.ACTIVITY_NEEDS_A_KIND);
    }

    @Test
    void anEntrySaysWhatHappened() {
        assertThat(ruleBrokenBy(() -> Activity.of(EVENT, DAY, ActivityKind.MAIL_SENT, "   ")))
                .isEqualTo(Rule.ACTIVITY_NEEDS_A_TEXT);
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
        assertThat(Activity.of(EVENT, DAY, ActivityKind.MAIL_SENT, "Termin angefragt").id()).isNull();
    }
}
