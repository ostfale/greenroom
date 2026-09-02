package de.ostfale.greenroom.domain.activities;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain Java, no Spring: what an entry is, and what a direction commits it to. */
class ActivityTest {

    private static final Long EVENT = 1L;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 2);

    @Test
    void anEntryBelongsToAnEveningAndIsDated() {
        assertThatThrownBy(() -> Activity.noted(null, DAY, "Beamer defekt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belongs to an event");
        assertThatThrownBy(() -> Activity.noted(EVENT, null, "Beamer defekt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is dated");
    }

    @Test
    void anEntrySaysWhatHappened() {
        assertThatThrownBy(() -> Activity.noted(EVENT, DAY, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("say what happened");
    }

    /** The direction is what separates something we did from something we wrote down. */
    @Test
    void aNoteWentNowhereSoItHasNoChannel() {
        assertThat(Activity.noted(EVENT, DAY, "Beamer defekt").channel()).isNull();

        assertThatThrownBy(() -> new Activity(null, EVENT, DAY, ActivityDirection.NOTE,
                ContactChannel.EMAIL, "Beamer defekt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a note went nowhere");
    }

    @Test
    void somethingThatWentOutOrCameInSaysHow() {
        assertThat(Activity.over(EVENT, DAY, ActivityDirection.OUTGOING, ContactChannel.EMAIL,
                "Sponsor angeschrieben").channel()).isEqualTo(ContactChannel.EMAIL);

        assertThatThrownBy(() -> new Activity(null, EVENT, DAY, ActivityDirection.INCOMING,
                null, "Sponsor sagt zu"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a channel");
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
